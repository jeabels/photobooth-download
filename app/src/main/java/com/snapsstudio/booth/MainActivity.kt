package com.snapsstudio.booth

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import org.json.JSONObject

/**
 * Full-screen kiosk that hosts the booth page (assets/booth/index.html).
 *
 * The page is served from https://appassets.androidplatform.net so it runs in a
 * secure origin: the camera, IndexedDB and localStorage behave exactly as in
 * Chrome. Native features are exposed to the page as window.NativeBooth.
 */
class MainActivity : Activity() {

    private lateinit var web: WebView
    private lateinit var bridge: BoothBridge

    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var pendingCameraRequest: PermissionRequest? = null

    private val assetLoader by lazy {
        WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val debuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        WebView.setWebContentsDebuggingEnabled(debuggable)

        web = WebView(this)
        setContentView(web)
        hideSystemBars()

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = false
            loadWithOverviewMode = true
            useWideViewPort = true
            textZoom = 100
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
        }
        web.overScrollMode = View.OVER_SCROLL_NEVER
        web.setBackgroundColor(0xFFF2EDE5.toInt())

        bridge = BoothBridge(this)
        web.addJavascriptInterface(bridge, "NativeBooth")
        web.webViewClient = BoothWebClient()
        web.webChromeClient = BoothChromeClient()

        requestStartupPermissions()
        web.loadUrl(START_URL)
    }

    /* ------------------------------------------------------------------ */
    /*  Called by BoothBridge                                              */
    /* ------------------------------------------------------------------ */

    /** Answers an async NativeBooth call made by the page. */
    fun reply(callbackId: String, ok: Boolean, data: String) {
        val js = "window.__nativeReply && window.__nativeReply(" +
            JSONObject.quote(callbackId) + "," + ok + "," + JSONObject.quote(data) + ")"
        runOnUiThread { if (!isDestroyed) web.evaluateJavascript(js, null) }
    }

    fun setKiosk(lock: Boolean) {
        runOnUiThread {
            try {
                if (lock) startLockTask() else stopLockTask()
            } catch (ignored: Exception) {
                // Not pinned, or pinning is switched off in Android settings.
            }
        }
    }

    fun exit() {
        runOnUiThread {
            try { stopLockTask() } catch (ignored: Exception) { }
            finishAndRemoveTask()
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Permissions                                                        */
    /* ------------------------------------------------------------------ */

    /** Asked once on first launch so guests never see a permission prompt. */
    private fun requestStartupPermissions() {
        val wanted = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) wanted += Manifest.permission.BLUETOOTH_CONNECT
        val missing = wanted.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), REQ_STARTUP)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // An empty result means the request was interrupted by another one; the
        // other request's answer settles the pending camera prompt instead.
        if (grantResults.isEmpty()) return
        val pending = pendingCameraRequest ?: return
        pendingCameraRequest = null
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            pending.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))
        } else {
            pending.deny()
        }
    }

    /* ------------------------------------------------------------------ */
    /*  File uploads (wallpaper, GCash QR, layout designs)                 */
    /* ------------------------------------------------------------------ */

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQ_FILE) {
            val callback = fileCallback
            fileCallback = null
            callback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data))
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    /* ------------------------------------------------------------------ */
    /*  Kiosk behaviour                                                    */
    /* ------------------------------------------------------------------ */

    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                )
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    /** Back never leaves the booth; the page closes its own dialogs instead. */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        web.evaluateJavascript("window.onNativeBack && window.onNativeBack()", null)
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
        hideSystemBars()
    }

    override fun onPause() {
        web.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        fileCallback?.onReceiveValue(null)
        fileCallback = null
        pendingCameraRequest?.deny()
        pendingCameraRequest = null
        bridge.shutdown()
        web.destroy()
        super.onDestroy()
    }

    /* ------------------------------------------------------------------ */

    private inner class BoothWebClient : WebViewClient() {
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
            assetLoader.shouldInterceptRequest(request.url)

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url
            if (url.host == WebViewAssetLoader.DEFAULT_DOMAIN) return false
            // Any other link opens outside the booth rather than replacing it.
            try {
                startActivity(Intent(Intent.ACTION_VIEW, url))
            } catch (ignored: ActivityNotFoundException) {
            }
            return true
        }
    }

    private inner class BoothChromeClient : WebChromeClient() {

        override fun onPermissionRequest(request: PermissionRequest) {
            runOnUiThread {
                if (!request.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) {
                    request.deny()
                    return@runOnUiThread
                }
                if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    request.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))
                } else {
                    pendingCameraRequest?.deny()
                    pendingCameraRequest = request
                    requestPermissions(arrayOf(Manifest.permission.CAMERA), REQ_CAMERA)
                }
            }
        }

        override fun onPermissionRequestCanceled(request: PermissionRequest) {
            if (pendingCameraRequest === request) pendingCameraRequest = null
        }

        override fun onShowFileChooser(
            webView: WebView,
            filePathCallback: ValueCallback<Array<Uri>>,
            fileChooserParams: WebChromeClient.FileChooserParams
        ): Boolean {
            fileCallback?.onReceiveValue(null)
            fileCallback = filePathCallback

            val mimes = fileChooserParams.acceptTypes
                .flatMap { it.split(',') }
                .map { it.trim() }
                .filter { it.contains('/') }
                .distinct()
            val pick = Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = if (mimes.size == 1) mimes[0] else "*/*"
                if (mimes.size > 1) putExtra(Intent.EXTRA_MIME_TYPES, mimes.toTypedArray())
            }
            try {
                startActivityForResult(Intent.createChooser(pick, "Choose a file"), REQ_FILE)
            } catch (ignored: ActivityNotFoundException) {
                fileCallback = null
                filePathCallback.onReceiveValue(null)
            }
            return true
        }
    }

    companion object {
        private const val REQ_STARTUP = 10
        private const val REQ_CAMERA = 11
        private const val REQ_FILE = 12
        private val START_URL = "https://" + WebViewAssetLoader.DEFAULT_DOMAIN + "/assets/booth/index.html"
    }
}
