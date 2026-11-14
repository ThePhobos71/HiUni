package de.transio.hiuni.feature.rooms.data

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import de.transio.hiuni.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Öffentliche LSF-Seiten, die der Raumfinder braucht. Eigene Schnittstelle, damit
 * das Repository ohne Netz getestet werden kann.
 */
interface LsfRoomSource {
    /** HTML der Raumauswahl mit allen Räumen. */
    suspend fun fetchRoomList(): String

    /** HTML des Wochenplans von Raum [rgid] für [week] im LSF-Format `46_2026`. */
    suspend fun fetchWeekPlan(rgid: Int, week: String): String

    /** Rohtext einer absoluten URL (hier: der iCal-Export aus dem Wochenplan). */
    suspend fun fetchText(url: String): String
}

/**
 * Abruf über den App-weiten OkHttp-Client, aber ohne Cookies: Die Seiten sind ohne
 * Login erreichbar, und so kann eine LSF-Sitzung des Nutzers (anderes Semester,
 * andere Ansicht) die Antwort nicht verändern. Der [de.transio.hiuni.core.network.PolitenessInterceptor]
 * des Basis-Clients bremst jeden LSF-Abruf zusätzlich.
 */
@Singleton
class OkHttpLsfRoomSource @Inject constructor(
    httpClient: OkHttpClient,
    @IoDispatcher private val io: CoroutineDispatcher
) : LsfRoomSource {

    private val client: OkHttpClient = httpClient.newBuilder()
        .cookieJar(CookieJar.NO_COOKIES)
        .build()

    override suspend fun fetchRoomList(): String = get(ROOM_LIST_URL)

    override suspend fun fetchWeekPlan(rgid: Int, week: String): String =
        get("$LSF_BASE?state=wplan&act=Raum&pool=Raum&show=plan&P.vx=kurz&raum.rgid=$rgid&week=$week")

    override suspend fun fetchText(url: String): String = get(url)

    private suspend fun get(url: String): String = withContext(io) {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "text/html,text/calendar,*/*;q=0.8")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("LSF antwortete mit HTTP ${response.code}")
            response.body?.string() ?: throw IOException("LSF lieferte keine Daten")
        }
    }

    companion object {
        const val LSF_BASE = "https://lsf.uni-hildesheim.de/qisserver/rds"

        /** Öffentliche Raumauswahl ("Räume und Gebäude", ohne Login), 163 Räume am 2026-11-14. */
        const val ROOM_LIST_URL = "$LSF_BASE?state=change&type=6&moduleParameter=raumSelect" +
            "&nextdir=change&next=SearchSelect.vm&target=raumSearch&subdir=raum&init=y" +
            "&source=state%3Dchange%26type%3D5%26moduleParameter%3DraumSearch%26nextdir%3Dchange" +
            "%26next%3Dsearch.vm%26subdir%3Draum%26_form%3Ddisplay%26topitem%3Dfacilities" +
            "%26subitem%3Dsearch%26function%3Dnologgedin%26field%3Ddtxt" +
            "&targetfield=dtxt&_form=display"
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class LsfRoomSourceModule {
    @Binds
    @Singleton
    abstract fun bindLsfRoomSource(impl: OkHttpLsfRoomSource): LsfRoomSource
}
