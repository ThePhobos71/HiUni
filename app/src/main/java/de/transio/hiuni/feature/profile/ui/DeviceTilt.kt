package de.transio.hiuni.feature.profile.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext

/**
 * Liefert die aktuelle Geräte-Neigung als Compose-State.
 *
 * Output:
 *  - `Offset(roll, pitch)` in Radian, clamped auf ±π/4 (~45°), damit Extreme nicht abdrehen.
 *  - Roll = Kippen nach links/rechts (positiv = rechts gekippt).
 *  - Pitch = Kippen nach vorne/hinten (positiv = nach vorne gekippt).
 *
 * Sensor-Strategie:
 *  - Primär: `TYPE_GAME_ROTATION_VECTOR` — driftfrei ohne Magnetometer, ideal für UI-Effekte.
 *  - Fallback: `TYPE_ROTATION_VECTOR` — verbreiteter, nutzt aber Magnetfeld (weniger ruhig).
 *  - Beides null (Emulator ohne Sensoren) → State bleibt auf [Offset.Zero], keine Crashes.
 *
 * Low-Pass-Filter (α = 0.15) glättet das Jitter aus den Rohdaten, sodass die UI nicht
 * bei jeder Mikro-Bewegung zittert.
 *
 * Lifecycle: `DisposableEffect` (re)gisitriert + de-registriert den Listener sauber, wenn
 * die Karte aus der Composition fliegt oder die App in den Background geht.
 */
@Composable
fun rememberDeviceTilt(): State<Offset> {
    val context = LocalContext.current
    val tilt = remember { mutableStateOf(Offset.Zero) }

    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        if (sensorManager == null || sensor == null) {
            // Emulator ohne Sensor → flach rendern, kein Crash.
            return@DisposableEffect onDispose { }
        }

        val rotationMatrix = FloatArray(9)
        val orientation = FloatArray(3)
        val maxTilt = (Math.PI / 4).toFloat() // ±45°

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                // Rotation-Vector → 3x3-Matrix → Euler-Winkel.
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientation)

                // orientation = [azimuth, pitch, roll]
                val rawPitch = orientation[1].coerceIn(-maxTilt, maxTilt)
                val rawRoll = orientation[2].coerceIn(-maxTilt, maxTilt)

                // Low-Pass-Filter — sanftes Nachschwingen statt rohem Jitter.
                val prev = tilt.value
                val alpha = 0.15f
                tilt.value = Offset(
                    x = prev.x * (1f - alpha) + rawRoll * alpha,
                    y = prev.y * (1f - alpha) + rawPitch * alpha
                )
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)

        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    return tilt
}
