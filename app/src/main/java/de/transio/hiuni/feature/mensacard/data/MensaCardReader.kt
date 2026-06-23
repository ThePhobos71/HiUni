package de.transio.hiuni.feature.mensacard.data

import android.nfc.Tag
import android.nfc.tech.IsoDep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Liest DESfire-basierte Hochschulkarten (Intercard, App-ID 0x5F8415) über
 * NFC aus. Stiftungsuni Hildesheim nutzt ausschließlich diesen Anbieter — der
 * MagnaCarta-Pfad wäre nur Ballast.
 *
 * Alle APDUs folgen dem ISO 7816-4 wrapped DESfire-Schema:
 * `90 <INS> 00 00 <Lc> <Params> 00`. Antworten enden mit `91 <Status>`;
 * `0x00` = OK, `0xAF` = weiterer Frame, alles andere = Fehler.
 */
@Singleton
class MensaCardReader @Inject constructor() {

    suspend fun read(tag: Tag): CardReadResult = withContext(Dispatchers.IO) {
        val isoDep = IsoDep.get(tag) ?: return@withContext failure(
            CardReadResult.Reason.NOT_DESFIRE,
            "Karte unterstützt ISO 14443-4 nicht"
        )
        try {
            isoDep.connect()
            isoDep.timeout = 5_000
            // Broadcom-NFC-Workaround: erster Transceive nach connect() kann
            // Müll liefern. Dummy-SELECT, Ergebnis ignorieren, reconnecten.
            runCatching { selectApplication(isoDep, 0x000000) }
            if (isoDep.isConnected) runCatching { isoDep.close() }
            isoDep.connect()

            val mfg = runCatching { transceive(isoDep, byteArrayOf(0x60.toByte())) }
                .getOrNull()
            val uid = mfg?.let { extractUid(it) } ?: tagIdHex(tag.id)
            val production = mfg?.let { extractProduction(it) }

            readIntercard(isoDep, uid, production)?.let { return@withContext success(it) }

            failure(CardReadResult.Reason.APP_NOT_FOUND, "Keine Intercard-App auf der Karte")
        } catch (t: Throwable) {
            Timber.w(t, "MensaCardReader: Fehler beim Auslesen")
            failure(CardReadResult.Reason.TRANSCEIVE_ERROR, t.message)
        } finally {
            runCatching { isoDep.close() }
        }
    }

    private fun readIntercard(
        isoDep: IsoDep,
        uid: String,
        production: ProductionData?
    ): MensaCardScan? {
        return try {
            selectApplication(isoDep, INTERCARD_APP_ID) ?: return null
            // READ_VALUE auf File 1 → 4 Bytes Little Endian, Wert in 1/1000 €.
            val raw = transceive(isoDep, byteArrayOf(0x6C.toByte(), 0x01)) ?: return null
            if (raw.size < 4) return null
            val value = ByteBuffer.wrap(raw, 0, 4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .int
            MensaCardScan(
                uid = uid,
                valueMilliEuro = value,
                scannedAt = Instant.now(),
                source = CardSource.INTERCARD,
                production = production
            )
        } catch (t: Throwable) {
            Timber.d(t, "Intercard-Pfad fehlgeschlagen")
            null
        }
    }

    /**
     * Sendet SELECT_APPLICATION (0x5A) mit der 3-Byte-App-ID (Big Endian).
     * Antwort `null` heißt: Karte hat die App nicht oder verweigert Zugriff.
     */
    private fun selectApplication(isoDep: IsoDep, appId: Int): ByteArray? {
        val params = byteArrayOf(
            ((appId shr 16) and 0xFF).toByte(),
            ((appId shr 8) and 0xFF).toByte(),
            (appId and 0xFF).toByte()
        )
        return transceive(isoDep, byteArrayOf(0x5A.toByte()) + params)
    }

    /**
     * Wickelt das DESfire-Befehlsbyte + optionale Parameter in den ISO-7816
     * APDU-Wrapper ein und sammelt Multi-Frame-Antworten (Status 0xAF) zu
     * einem ByteArray. Gibt `null` zurück wenn der Server einen Fehlerstatus
     * liefert (z. B. 0x9D = Permission Denied, 0xAE = Auth Error, 0xF0 = File
     * Not Found) — also alles außer 0x00/0xAF.
     */
    private fun transceive(isoDep: IsoDep, command: ByteArray): ByteArray? {
        val ins = command[0]
        val params = command.copyOfRange(1, command.size)
        val firstApdu = wrap(ins, params)
        val out = ByteArrayOutputStream()
        var response = isoDep.transceive(firstApdu)
        while (true) {
            if (response.size < 2) return null
            out.write(response, 0, response.size - 2)
            val status = response[response.size - 1]
            when (status) {
                0x00.toByte() -> return out.toByteArray()
                0xAF.toByte() -> {
                    val nextApdu = wrap(0xAF.toByte(), ByteArray(0))
                    response = isoDep.transceive(nextApdu)
                }
                else -> {
                    Timber.v("DESfire ins=%02X status=%02X", ins, status)
                    return null
                }
            }
        }
    }

    private fun wrap(ins: Byte, params: ByteArray): ByteArray {
        val apdu = ByteArray(5 + params.size + 1)
        apdu[0] = 0x90.toByte()
        apdu[1] = ins
        apdu[2] = 0x00
        apdu[3] = 0x00
        apdu[4] = params.size.toByte()
        System.arraycopy(params, 0, apdu, 5, params.size)
        apdu[apdu.size - 1] = 0x00
        return apdu
    }

    private fun extractUid(mfg: ByteArray): String? {
        if (mfg.size < 21) return null
        return tagIdHex(mfg.copyOfRange(14, 21))
    }

    private fun extractProduction(mfg: ByteArray): ProductionData? {
        if (mfg.size < 28) return null
        val weekBcd = mfg[26].toInt() and 0xFF
        val yearBcd = mfg[27].toInt() and 0xFF
        val week = ((weekBcd shr 4) * 10) + (weekBcd and 0x0F)
        val year = 2000 + ((yearBcd shr 4) * 10) + (yearBcd and 0x0F)
        if (week !in 1..53 || year !in 2000..2099) return null
        return ProductionData(week = week, year = year)
    }

    private fun tagIdHex(bytes: ByteArray): String =
        bytes.joinToString(separator = "") { "%02X".format(it) }

    private fun success(scan: MensaCardScan): CardReadResult.Success =
        CardReadResult.Success(scan)

    private fun failure(reason: CardReadResult.Reason, detail: String?): CardReadResult.Failure =
        CardReadResult.Failure(reason, detail)

    companion object {
        private const val INTERCARD_APP_ID = 0x5F8415
    }
}
