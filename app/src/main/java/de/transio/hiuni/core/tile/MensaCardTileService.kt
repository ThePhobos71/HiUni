package de.transio.hiuni.core.tile

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import de.transio.hiuni.MainActivity
import de.transio.hiuni.feature.widgets.WidgetDeepLinkController

/**
 * Quick-Settings-Kachel "Mensa-Karte": öffnet die App direkt auf der
 * Mensa-Karten-Sicht. Der NFC-Scan startet dort automatisch (MensaCardScreen
 * schaltet ihn beim Betreten scharf); ohne NFC-Chip öffnet sich die Sicht
 * trotzdem.
 */
class MensaCardTileService : TileService() {

    override fun onStartListening() {
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        val intent = buildLaunchIntent(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pending = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    companion object {
        fun buildLaunchIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java).apply {
                action = WidgetDeepLinkController.ACTION_OPEN_MENSA_CARD
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
    }
}
