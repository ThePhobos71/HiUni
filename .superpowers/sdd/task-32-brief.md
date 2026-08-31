### Task 32: QR-Intro UI

**Files:**
- Create: `app/src/main/java/de/transio/hiuni/feature/mensa/review/trust/QrIntro.kt`
- Modify: `app/build.gradle.kts` (zxing-Dependency)

**Interfaces:**
- Produces: `@Composable fun ShowMyPubkeyQr()`, `@Composable fun ScanPeerPubkeyQr(onResult)`

- [ ] **Step 1:** Dependencies in `libs.versions.toml`:

```toml
zxing = "3.5.3"
zxingAndroidEmbedded = "4.3.0"

# in [libraries]
zxing-core = { group = "com.google.zxing", name = "core", version.ref = "zxing" }
zxing-android-embedded = { group = "com.journeyapps", name = "zxing-android-embedded", version.ref = "zxingAndroidEmbedded" }
```

- [ ] **Step 2:** `QrIntro.kt`:

```kotlin
package de.transio.hiuni.feature.mensa.review.trust

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

private fun qrBitmap(text: String, size: Int = 512): Bitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
    for (x in 0 until size) for (y in 0 until size)
        bmp.setPixel(x, y, if (matrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFFFF.toInt())
    return bmp
}

@Composable
fun ShowMyPubkeyQr(pubkey: String) {
    val bmp = remember(pubkey) { qrBitmap("hiuni-intro:$pubkey") }
    Image(bmp.asImageBitmap(), contentDescription = "Mein Pubkey-QR")
}

// ScanPeerPubkeyQr: via zxing-android-embedded ScannerActivity oder CameraX-Wrapper;
// Result-Parsing: "hiuni-intro:<base64-pubkey>" extrahieren
```

- [ ] **Step 3:** Scanner-Aufruf in `ProfileScreen` o.ä.: Button „Bekannten einführen", öffnet Scanner, parsed Pubkey, ruft `IntroIssuer.issueIntro(pubkey)`.

- [ ] **Step 4:** Commit:

```bash
git add app/
git commit -m "feat(reviews): QR-Intro (Anzeige + Scan + IntroIssuer-Trigger)"
```

