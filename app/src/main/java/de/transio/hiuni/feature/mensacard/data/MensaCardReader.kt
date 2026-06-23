package de.transio.hiuni.feature.mensacard.data

import android.nfc.Tag
import android.nfc.tech.IsoDep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
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
            connectIsoDepWithRetry(isoDep)
            isoDep.timeout = 5_000

            // UID nehmen wir aus dem ISO-14443-3-Anti-Collision (tag.id). Damit
            // sparen wir uns das fehleranfällige GET_MANUFACTURING_DATA (0x60)
            // — moderne DESfire EV2/EV3 antworten ohne Auth mit 0x1C "Illegal
            // Command Code" UND tearen danach die Session ab, sodass das
            // nachfolgende SELECT_APPLICATION mit "Call connect() first!"
            // knallt. Production-Daten holen wir später best-effort.
            val uid = tagIdHex(tag.id)

            val scan = readIntercard(isoDep, uid)
                ?: return@withContext failure(
                    CardReadResult.Reason.APP_NOT_FOUND,
                    "Keine Intercard-App auf der Karte"
                )

            // Nice-to-have am Ende: Production-Woche/Jahr. Wenn der Chip jetzt
            // wegen 0x60 zickt, ist's egal — der Scan ist schon vollständig.
            val production = runCatching {
                val mfg = transceive(isoDep, byteArrayOf(0x60.toByte()))
                mfg?.let { extractProduction(it) }
            }.getOrNull()

            success(scan.copy(production = production))
        } catch (t: Throwable) {
            Timber.w(t, "MensaCardReader: Fehler beim Auslesen")
            failure(CardReadResult.Reason.TRANSCEIVE_ERROR, t.message)
        } finally {
            runCatching { isoDep.close() }
        }
    }

    /**
     * IsoDep.connect() schlägt gelegentlich mit "Only one TagTechnology…" fehl,
     * wenn die NfcA-Anti-Collision noch nicht freigegeben ist. Wir warten kurz
     * und versuchen es bis zu 3× — der Backoff (80/160 ms) entspricht der Zeit,
     * die der Chip braucht um sich zu sortieren.
     */
    private suspend fun connectIsoDepWithRetry(isoDep: IsoDep, attempts: Int = 3) {
        var lastError: IOException? = null
        repeat(attempts) { attempt ->
            try {
                isoDep.connect()
                return
            } catch (e: IOException) {
                lastError = e
                Timber.d("IsoDep.connect attempt ${attempt + 1} failed: ${e.message}")
                runCatching { isoDep.close() }
                delay(80L * (attempt + 1))
            }
        }
        throw lastError ?: IOException("IsoDep.connect failed after $attempts attempts")
    }

    private fun readIntercard(isoDep: IsoDep, uid: String): MensaCardScan? {
        return try {
            selectApplication(isoDep, INTERCARD_APP_ID) ?: return null
            // READ_VALUE auf File 1 → 4 Bytes Little Endian, Wert in 1/1000 €.
            val raw = transceive(isoDep, byteArrayOf(0x6C.toByte(), 0x01)) ?: return null
            if (raw.size < 4) return null
            val value = ByteBuffer.wrap(raw, 0, 4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .int
            val lastDebit = readLimitedCreditValueFromSettings(isoDep)
            if (lastDebit != null) {
                Timber.i(
                    "Intercard File-1 LimitedCreditValue = %d (current balance = %d) → letzte Abbuchung %.2f €",
                    lastDebit, value, lastDebit / 1000.0
                )
            }
            MensaCardScan(
                uid = uid,
                valueMilliEuro = value,
                scannedAt = Instant.now(),
                source = CardSource.INTERCARD,
                production = null,
                rawLastTransactionRecord = readLatestTransactionRecord(isoDep),
                lastDebitAmountMilliEuro = lastDebit
            )
        } catch (t: Throwable) {
            Timber.d(t, "Intercard-Pfad fehlgeschlagen")
            null
        }
    }

    /**
     * Liest die File Settings der Value-File 1 (`GET_FILE_SETTINGS 0xF5`) und
     * extrahiert die LimitedCreditValue. Response-Layout für ein Value File:
     *   Byte 0:      File Type (0x02)
     *   Byte 1:      Comm Settings
     *   Bytes 2-3:   Access Rights
     *   Bytes 4-7:   Lower Limit (Int32 LE)
     *   Bytes 8-11:  Upper Limit (Int32 LE)
     *   Bytes 12-15: Limited Credit Value (Int32 LE)
     *   Byte 16:     Limited Credit Enabled
     *
     * Die LimitedCreditValue wird nach jeder Debit-Operation auf den Betrag
     * der Abbuchung gesetzt — Refund-Mechanismus, das gleiche Terminal kann
     * ohne Auth wieder gut schreiben. Bei Intercard heißt das praktisch:
     * **on-chip gespeicherter Betrag der letzten Abbuchung**, verfügbar auch
     * direkt nach einem Auth-freien Read.
     */
    private fun readLimitedCreditValueFromSettings(isoDep: IsoDep): Int? {
        val settings = transceive(isoDep, byteArrayOf(0xF5.toByte(), 0x01)) ?: return null
        if (settings.size < 16) return null
        return ByteBuffer.wrap(settings, 12, 4).order(ByteOrder.LITTLE_ENDIAN).int
    }

    /**
     * Versucht den jüngsten Eintrag aus File 2 (Cyclic Record File mit
     * Transaktionshistorie) zu lesen. APDU: READ_RECORDS (0xBB) mit
     * FileID=2, Offset=0 (= jüngster bei Cyclic), Count=1 → 32 Bytes.
     *
     * Laut Intercard-Doku ist File 2 mit Read=Key1 belegt (auth-gated) und
     * sollte 0xAE liefern. Wir probieren es trotzdem — falls es bei uns
     * frei lesbar ist, loggen wir die Bytes damit wir den Parser an echten
     * Daten schreiben können.
     */
    private fun readLatestTransactionRecord(isoDep: IsoDep): ByteArray? {
        val params = byteArrayOf(
            0x02,                    // File-ID 2
            0x00, 0x00, 0x00,        // Offset 0 = jüngster Record
            0x01, 0x00, 0x00         // Count 1 = nur den einen
        )
        val record = transceive(isoDep, byteArrayOf(0xBB.toByte()) + params) ?: return null
        if (record.isEmpty()) return null
        Timber.i(
            "Intercard Transaction Record (File 2 / Offset 0): %d Bytes = %s",
            record.size,
            record.joinToString(separator = " ") { "%02X".format(it) }
        )
        return record
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

    /**
     * ISO 7816-4 APDU-Wrapper für DESfire-Befehle.
     *  - Ohne Params (Case 2 short): `CLA INS P1 P2 Le` = 5 Bytes mit Le=0.
     *  - Mit Params (Case 4 short):  `CLA INS P1 P2 Lc <data> Le` = 5+Lc+1.
     *
     * Der Unterschied ist wichtig: bei params-freien Commands sendete eine
     * frühere Version 6 Bytes (Lc=0 PLUS Le=0). DESfire-Chips antworten dann
     * mit `91 7E` ("Length Error"), weil sie das als Case 4 mit ambiguer
     * Length-Spezifikation interpretieren.
     */
    private fun wrap(ins: Byte, params: ByteArray): ByteArray {
        if (params.isEmpty()) {
            return byteArrayOf(0x90.toByte(), ins, 0x00, 0x00, 0x00)
        }
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
