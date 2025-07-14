package com.sanne.webviewpro

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.AttributeSet
import android.view.View
import android.webkit.*
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class WebViewPro : RelativeLayout {

    private var webView: WebView
    private var progressBar: ProgressBar
    private var swipeRefreshLayout: SwipeRefreshLayout
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var activity: Activity? = null

    companion object {
        private const val FILE_CHOOSER_REQUEST = 1001
    }

    constructor(context: Context) : this(context, null, null)
    constructor(context: Context, urlToLoad: String?) : this(context, null, urlToLoad)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, null)
    constructor(context: Context, attrs: AttributeSet?, urlToLoad: String?) : super(context, attrs) {
        if (context is Activity) {
            this.activity = context
        } else {
            throw IllegalArgumentException("Context must be an Activity.")
        }
        initialize(context, urlToLoad)
    }

    private fun initialize(context: Context, urlToLoad: String?) {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)

        // === Pull to Refresh ===
        swipeRefreshLayout = SwipeRefreshLayout(context)
        swipeRefreshLayout.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)

        // === WebView ===
        webView = WebView(context)
        webView.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        swipeRefreshLayout.addView(webView)

        // === ProgressBar ===
        progressBar = ProgressBar(context)
        val pbParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        pbParams.addRule(CENTER_IN_PARENT)
        progressBar.layoutParams = pbParams
        progressBar.visibility = View.GONE

        addView(swipeRefreshLayout)
        addView(progressBar)

        setupWebViewSettings()
        setupWebViewClients(context)
        setupSwipeToRefresh()

        // Load initial URL or fallback to local file
        val url = if (!urlToLoad.isNullOrBlank()) urlToLoad else "file:///android_asset/index.html"
        webView.loadUrl(url)

        // Enable Chrome remote debugging for development
        if (BuildConfig.DEBUG && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
    }

    private fun setupWebViewSettings() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.setSupportZoom(false)
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true

        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false
    }

    private fun setupWebViewClients(context: Context) {
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                progressBar.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                progressBar.visibility = View.GONE
                swipeRefreshLayout.isRefreshing = false
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    Toast.makeText(context, "Failed to load page. Showing offline fallback.", Toast.LENGTH_SHORT).show()
                    webView.loadUrl("file:///android_asset/offline.html")
                }
            }

            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                Toast.makeText(context, "SSL Error: $error", Toast.LENGTH_SHORT).show()
                handler?.proceed() // Caution: consider overriding for stricter apps
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return handleCustomScheme(request?.url.toString(), context)
            }

            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                return handleCustomScheme(url ?: "", context)
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                view: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                if (activity == null) return false
                this@WebViewPro.filePathCallback = filePathCallback
                val intent = Intent(Intent.ACTION_GET_CONTENT)
                intent.addCategory(Intent.CATEGORY_OPENABLE)
                intent.type = "*/*"
                activity?.startActivityForResult(Intent.createChooser(intent, "Select File"), FILE_CHOOSER_REQUEST)
                return true
            }

            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
            }
        }

        // File/Media downloads
        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            try {
                val request = DownloadManager.Request(Uri.parse(url))
                request.setMimeType(mimetype)
                request.addRequestHeader("User-Agent", userAgent)
                request.setTitle(URLUtil.guessFileName(url, contentDisposition, mimetype))
                request.setDescription("Downloading file...")
                request.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    URLUtil.guessFileName(url, contentDisposition, mimetype)
                )
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

                val dm = activity?.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                if (dm != null) {
                    dm.enqueue(request)
                    Toast.makeText(context, "Downloading...", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Download failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupSwipeToRefresh() {
        swipeRefreshLayout.setOnRefreshListener { webView.reload() }
    }

    private fun handleCustomScheme(url: String, context: Context): Boolean {
        return try {
            when {
                url.startsWith("http://") || url.startsWith("https://") -> false // let WebView load it
                url.startsWith("tel:") || url.startsWith("mailto:") ||
                        url.startsWith("intent:") || url.startsWith("sms:") -> {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    context.startActivity(intent)
                    true
                }
                else -> false
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot open link", Toast.LENGTH_SHORT).show()
            true
        }
    }

    // === Public APIs ===

    fun onFileUploadResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == FILE_CHOOSER_REQUEST && filePathCallback != null) {
            val result = if (resultCode == Activity.RESULT_OK && data != null && data.data != null)
                arrayOf(data.data!!)
            else
                null
            filePathCallback?.onReceiveValue(result)
            filePathCallback = null
        }
    }

    fun bindJS(name: String, javaObject: Any) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            webView.addJavascriptInterface(javaObject, name)
        }
    }

    fun reload() {
        webView.reload()
    }

    fun canGoBack(): Boolean {
        return webView.canGoBack()
    }

    fun goBack() {
        if (canGoBack()) {
            webView.goBack()
        }
    }

    fun getWebView(): WebView {
        return webView
    }

    fun launch() {
        activity?.setContentView(this)
    }
}
