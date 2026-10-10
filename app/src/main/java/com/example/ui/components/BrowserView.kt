package com.example.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.example.engine.BrowserAutomationEngine
import com.example.ui.theme.DarkNavyBackground
import com.example.ui.theme.ElectricBlue

class NoKeyboardWebView(context: android.content.Context, val blockKeyboard: Boolean) : WebView(context) {
    override fun onCreateInputConnection(outAttrs: android.view.inputmethod.EditorInfo?): android.view.inputmethod.InputConnection? {
        outAttrs?.let {
            it.imeOptions = it.imeOptions or android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        }
        return if (blockKeyboard) null else super.onCreateInputConnection(outAttrs)
    }

    override fun getImportantForAutofill(): Int = android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS

    override fun getAutofillType(): Int = android.view.View.AUTOFILL_TYPE_NONE

    override fun getAutofillHints(): Array<String>? = null
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserView(
    engine: BrowserAutomationEngine,
    onUrlChanged: (String) -> Unit,
    initialUrl: String = BrowserAutomationEngine.DEFAULT_INSTAGRAM_URL,
    disableKeyboard: Boolean = false,
    modifier: Modifier = Modifier
) {
    val defaultInstagramUrl = initialUrl
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember { mutableStateOf(defaultInstagramUrl) }
    var progress by remember { mutableIntStateOf(100) }
    var canGoBack by remember { mutableStateOf(false) }

    BackHandler(enabled = canGoBack) {
        if (webViewInstance?.canGoBack() == true) {
            webViewInstance?.goBack()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            engine.detachWebView()
            webViewInstance?.destroy()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkNavyBackground)
    ) {
        // Embedded Android WebView (Full normal screen)
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .testTag("embedded_webview"),
            factory = { ctx ->
                NoKeyboardWebView(ctx, disableKeyboard).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
                        try {
                            val afm = ctx.getSystemService(android.view.autofill.AutofillManager::class.java)
                            afm?.cancel()
                            afm?.disableAutofillServices()
                        } catch (e: Exception) {}
                    }

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        allowFileAccess = true
                        allowContentAccess = true
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        saveFormData = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        cacheMode = WebSettings.LOAD_DEFAULT
                        builtInZoomControls = false
                        displayZoomControls = false

                        val rawUa = userAgentString
                        userAgentString = rawUa
                            .replace("; wv", "")
                            .replace("Version/4.0 ", "")
                    }

                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(this, true)

                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            progress = newProgress
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            if (url != null) {
                                currentUrl = url
                                onUrlChanged(url)
                                canGoBack = view?.canGoBack() == true
                                engine.injectAutoAccountCreatorWatcher()
                            }
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            if (url != null) {
                                currentUrl = url
                                onUrlChanged(url)
                                canGoBack = view?.canGoBack() == true
                                engine.captureLoggedInUsername()
                                if (engine.isRecording) {
                                    engine.injectRecorderScript()
                                }
                                engine.injectAutoAccountCreatorWatcher()
                            }
                        }

                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val reqUrl = request?.url?.toString() ?: return false
                            if (reqUrl.startsWith("http://") || reqUrl.startsWith("https://")) {
                                return false // keep loading inside WebView
                            }
                            return true
                        }
                    }

                    webViewInstance = this
                    engine.attachWebView(this)
                    
                    val pendingSession = engine.pendingSessionToRestore
                    if (!pendingSession.isNullOrBlank()) {
                        engine.switchTabSession(pendingSession, targetUrl = defaultInstagramUrl)
                    } else {
                        loadUrl(defaultInstagramUrl)
                    }
                }
            },
            update = { view ->
                webViewInstance = view
            }
        )

        // Subtle loading indicator at top
        AnimatedVisibility(visible = progress < 100) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp),
                color = ElectricBlue,
                trackColor = DarkNavyBackground
            )
        }
    }
}
