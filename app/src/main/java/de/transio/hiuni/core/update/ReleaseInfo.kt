package de.transio.hiuni.core.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Fehler im Update-Pfad mit nutzertauglicher deutscher [message]. */
class UpdateException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Herunterladbare Datei eines GitHub-Releases. */
data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long
)

/** Das für die App relevante Abbild eines GitHub-Releases. */
data class ReleaseInfo(
    val tag: String,
    val version: AppVersion,
    val title: String,
    /** Release-Text (Markdown) aus GitHub, dient als Changelog. */
    val changelog: String,
    val htmlUrl: String,
    /** Die zu installierende APK, `null` wenn das Release keine enthält. */
    val apk: ReleaseAsset?
)

/**
 * Liest die Antwort von `GET /repos/{owner}/{repo}/releases/latest`. Rein und
 * Android-frei, damit sie mit einer JSON-Fixture getestet werden kann.
 */
object ReleaseJsonParser {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    /**
     * @return das Release oder `null`, wenn der Tag keinem `vX.Y.Z`-Schema folgt
     *   oder es ein Entwurf bzw. eine Vorabversion ist (der latest-Endpunkt liefert
     *   die zwar nie, doppelt hält aber besser).
     * @throws UpdateException bei kaputtem JSON.
     */
    fun parse(body: String): ReleaseInfo? {
        val dto = try {
            json.decodeFromString(GitHubReleaseDto.serializer(), body)
        } catch (e: SerializationException) {
            throw UpdateException("Antwort von GitHub nicht lesbar", e)
        } catch (e: IllegalArgumentException) {
            throw UpdateException("Antwort von GitHub nicht lesbar", e)
        }
        if (dto.draft || dto.prerelease) return null
        val version = AppVersion.parse(dto.tagName) ?: return null
        return ReleaseInfo(
            tag = dto.tagName,
            version = version,
            title = dto.name?.takeIf { it.isNotBlank() } ?: "HiUni ${version.name}",
            changelog = dto.body.orEmpty().replace("\r\n", "\n").trim(),
            htmlUrl = dto.htmlUrl,
            apk = pickApk(dto.assets, version)
        )
    }

    /** Bevorzugt `HiUni-<Version>.apk`, sonst die erste APK im Release. */
    private fun pickApk(assets: List<GitHubAssetDto>, version: AppVersion): ReleaseAsset? {
        val apks = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
        val chosen = apks.firstOrNull { it.name.equals("HiUni-${version.name}.apk", ignoreCase = true) }
            ?: apks.firstOrNull()
            ?: return null
        return ReleaseAsset(chosen.name, chosen.browserDownloadUrl, chosen.size)
    }
}

@Serializable
internal data class GitHubReleaseDto(
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val body: String? = null,
    @SerialName("html_url") val htmlUrl: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GitHubAssetDto> = emptyList()
)

@Serializable
internal data class GitHubAssetDto(
    val name: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
    val size: Long = 0
)
