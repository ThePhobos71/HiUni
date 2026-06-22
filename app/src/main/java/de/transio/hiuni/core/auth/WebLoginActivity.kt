package de.transio.hiuni.core.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import dagger.hilt.android.AndroidEntryPoint
import de.transio.hiuni.core.design.HiUniTheme
import timber.log.Timber
import javax.inject.Inject

/**
 * Zeigt eine echte WebView mit der CAS-Login-Page. Nach erfolgreichem Login
 * (URL navigiert weg von /cas/login) lesen wir das TGC aus dem CookieManager
 * und persistieren es über [CasSession.onLoginSuccess].
 *
 * Aufruf via [CasLoginContract] aus Compose:
 *   val launcher = rememberLauncherForActivityResult(CasLoginContract()) { result -> ... }
 *   Button(onClick = { launcher.launch(Unit) })
 */
@AndroidEntryPoint
class WebLoginActivity : ComponentActivity() {

    @Inject lateinit var casSession: CasSession

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)

        val baseUrl = casSession.baseUrl()
        val startUrl = "$baseUrl${CasConfig.LOGIN_PATH}"

        // Cookies vor Login resetten — sonst nehmen wir stale TGCs vom vorherigen Versuch.
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()

        setContent {
            HiUniTheme {
                LoginScaffold(
                    startUrl = startUrl,
                    baseUrl = baseUrl,
                    onSuccess = { tgc, username ->
                        casSession.onLoginSuccess(tgc, username, baseUrl)
                        setResult(Activity.RESULT_OK)
                        finish()
                    },
                    onCancel = {
                        setResult(Activity.RESULT_CANCELED)
                        finish()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@androidx.compose.runtime.Composable
private fun LoginScaffold(
    startUrl: String,
    baseUrl: String,
    onSuccess: (tgc: String, username: String?) -> Unit,
    onCancel: () -> Unit
) {
    var loading by remember { mutableStateOf(true) }
    val ctx = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Uni-Login") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Outlined.Close, contentDescription = "Schließen")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    buildWebView(
                        context = context,
                        baseUrl = baseUrl,
                        onStateChange = { loading = it },
                        onSuccess = onSuccess
                    )
                },
                update = { webView ->
                    if (webView.url == null) webView.loadUrl(startUrl)
                }
            )
            if (loading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                )
            }
        }
    }
}

@Suppress("SetJavaScriptEnabled")
private fun buildWebView(
    context: Context,
    baseUrl: String,
    onStateChange: (loading: Boolean) -> Unit,
    onSuccess: (tgc: String, username: String?) -> Unit
): WebView = WebView(context).apply {
    layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
    )
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.loadsImagesAutomatically = true
    CookieManager.getInstance().setAcceptCookie(true)
    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

    webViewClient = object : WebViewClient() {
        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
            onStateChange(true)
            Timber.d("WebLogin onPageStarted url=$url")
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            onStateChange(false)
            Timber.d("WebLogin onPageFinished url=$url")
            // Wenn wir nicht (mehr) auf der CAS-Login-Seite sind, prüfen wir auf TGC.
            if (url != null && !CasConfig.isLoginUrl(url)) {
                tryExtractTgc(baseUrl)?.let { tgc ->
                    Timber.i("WebLogin success — extracted TGC (length=${tgc.length})")
                    onSuccess(tgc, null) // Username later via service responses
                }
            }
        }

        override fun shouldOverrideUrlLoading(
            view: WebView?,
            request: WebResourceRequest?
        ): Boolean {
            val url = request?.url?.toString()
            Timber.v("WebLogin shouldOverrideUrlLoading url=$url")
            return false // immer in WebView weiterleiten
        }
    }
}

private fun tryExtractTgc(baseUrl: String): String? {
    val cookieHeader = CookieManager.getInstance().getCookie(baseUrl) ?: return null
    // CookieManager liefert "name1=value1; name2=value2; ..." als String
    val tgc = cookieHeader.split(';')
        .map { it.trim() }
        .firstOrNull { it.startsWith("${CasConfig.TGC_COOKIE_NAME}=") }
        ?.substringAfter('=')
        ?.takeIf { it.isNotBlank() }
    Timber.d("WebLogin extracted TGC=${tgc?.take(20)}... fullCookieHeader.len=${cookieHeader.length}")
    return tgc
}

/**
 * Activity-Result-Contract — gibt true zurück wenn der Login erfolgreich war,
 * false bei Abbruch.
 */
class CasLoginContract : ActivityResultContract<Unit, Boolean>() {
    override fun createIntent(context: Context, input: Unit): Intent =
        Intent(context, WebLoginActivity::class.java)

    override fun parseResult(resultCode: Int, intent: Intent?): Boolean =
        resultCode == Activity.RESULT_OK
}
