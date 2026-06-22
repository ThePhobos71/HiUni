package de.transio.hiuni.core.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
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
import de.transio.hiuni.core.security.CredentialsManager
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
    @Inject lateinit var credentialsManager: CredentialsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)

        val baseUrl = casSession.baseUrl()
        // Mit service-Parameter damit CAS nach Login zu LSF redirected statt auf
        // der /sso/login-Page hängen zu bleiben.
        val startUrl = CasConfig.initialLoginUrl(baseUrl)

        // Cookies vor Login resetten — sonst nehmen wir stale TGCs vom vorherigen Versuch.
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()

        // Erfasst die im Form eingegebene RZ-Kennung beim Submit, damit später
        // Silent-Renewal ohne erneutes WebView-Popup möglich ist und das gleiche
        // Konto auch für Mail-IMAP wiederverwendet werden kann.
        var capturedUsername: String? = null
        val credentialsBridge = CredentialsCaptureBridge { user, password ->
            capturedUsername = user
            credentialsManager.saveCredentials(user, password)
            Timber.i("WebLogin captured credentials for user=$user (password redacted)")
        }

        setContent {
            HiUniTheme {
                LoginScaffold(
                    startUrl = startUrl,
                    baseUrl = baseUrl,
                    credentialsBridge = credentialsBridge,
                    onSuccess = { tgc, cookieHeader, userAgent ->
                        casSession.onLoginSuccess(
                            tgc = tgc,
                            cookieHeader = cookieHeader,
                            userAgent = userAgent,
                            username = capturedUsername,
                            baseUrl = baseUrl
                        )
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

/**
 * JS-Bridge: läuft im WebView-Render-Prozess und gibt die Werte der Username-
 * /Passwort-Felder ans Native-Layer weiter, sobald der User den Submit-Button
 * tippt. Methode wird über `addJavascriptInterface(..., "HiUniCreds")` exponiert.
 */
private class CredentialsCaptureBridge(
    private val onCaptured: (username: String, password: String) -> Unit
) {
    @JavascriptInterface
    fun onCredentials(username: String?, password: String?) {
        val u = username?.trim().orEmpty()
        val p = password.orEmpty()
        if (u.isNotEmpty() && p.isNotEmpty()) onCaptured(u, p)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@androidx.compose.runtime.Composable
private fun LoginScaffold(
    startUrl: String,
    baseUrl: String,
    credentialsBridge: CredentialsCaptureBridge,
    onSuccess: (tgc: String, cookieHeader: String, userAgent: String) -> Unit,
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
                        credentialsBridge = credentialsBridge,
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
    credentialsBridge: CredentialsCaptureBridge,
    onStateChange: (loading: Boolean) -> Unit,
    onSuccess: (tgc: String, cookieHeader: String, userAgent: String) -> Unit
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
    addJavascriptInterface(credentialsBridge, JS_BRIDGE_NAME)

    webViewClient = object : WebViewClient() {
        private var firedSuccess = false

        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
            onStateChange(true)
            Timber.d("WebLogin onPageStarted url=$url")
            // TGC kann schon nach dem POST-Redirect da sein, bevor onPageFinished feuert
            checkTgcAndFinish(view, baseUrl, onSuccess, markFired = { firedSuccess = it }, alreadyFired = firedSuccess)
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            onStateChange(false)
            Timber.d("WebLogin onPageFinished url=$url")
            // Auf der CAS-Login-Page Form-Submit-Listener injizieren — capture-phase,
            // damit wir die Felder lesen können bevor das Form tatsächlich submitted.
            if (CasConfig.isLoginUrl(url)) {
                view?.evaluateJavascript(CREDENTIAL_HOOK_JS, null)
            }
            checkTgcAndFinish(view, baseUrl, onSuccess, markFired = { firedSuccess = it }, alreadyFired = firedSuccess)
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

private const val JS_BRIDGE_NAME = "HiUniCreds"

/**
 * Hookt den `submit`-Event des CAS-Login-Forms in der Capture-Phase ab. Wir lesen
 * `username` + `password` Werte und reichen sie ans Native-Layer durch — bevor
 * der Browser das Form tatsächlich abschickt.
 */
private val CREDENTIAL_HOOK_JS = """
(function() {
  if (window.__hiUniHooked) return;
  var u = document.querySelector('input[name="username"]');
  var p = document.querySelector('input[name="password"]');
  var form = (u && u.form) || (p && p.form) || document.querySelector('form');
  if (!u || !p || !form) return;
  window.__hiUniHooked = true;
  form.addEventListener('submit', function() {
    try { $JS_BRIDGE_NAME.onCredentials(u.value, p.value); } catch (e) {}
  }, true);
})();
""".trimIndent()

/**
 * Cookie-basierte Detection: wir haben vor Login-Start alle Cookies gecleart. Sobald
 * TGC im CookieManager auftaucht, war Login erfolgreich — egal welche URL gerade
 * angezeigt wird (CAS-Success-Page, LSF-Portal, etc.).
 */
private fun checkTgcAndFinish(
    webView: WebView?,
    baseUrl: String,
    onSuccess: (tgc: String, cookieHeader: String, userAgent: String) -> Unit,
    markFired: (Boolean) -> Unit,
    alreadyFired: Boolean
) {
    if (alreadyFired) return
    val cookieHeader = CookieManager.getInstance().getCookie(baseUrl) ?: return
    val tgc = extractTgcFrom(cookieHeader) ?: return
    val userAgent = webView?.settings?.userAgentString.orEmpty()
    markFired(true)
    Timber.i("WebLogin success — TGC ${tgc.length}ch, cookieHeader ${cookieHeader.length}ch, UA=${userAgent.take(60)}…")
    onSuccess(tgc, cookieHeader, userAgent)
}

private fun extractTgcFrom(cookieHeader: String): String? =
    cookieHeader.split(';')
        .map { it.trim() }
        .firstOrNull { it.startsWith("${CasConfig.TGC_COOKIE_NAME}=") }
        ?.substringAfter('=')
        ?.takeIf { it.isNotBlank() }

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
