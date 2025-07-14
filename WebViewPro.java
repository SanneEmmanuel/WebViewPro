package com.sanne.webviewpro;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.AttributeSet;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.webkit.*;

public class WebViewPro extends RelativeLayout {

    private WebView webView;
    private ProgressBar progressBar;
    private ValueCallback<Uri[]> filePathCallback;
    private static final int FILE_CHOOSER_REQUEST = 1001;
    private Activity activity;

    // Custom refresh view
    private RelativeLayout refreshContainer;
    private View refreshIndicator;
    private boolean isRefreshing = false;
    private OnRefreshListener onRefreshListener;

    public interface OnRefreshListener {
        void onRefresh();
    }

    public WebViewPro(Context context) {
        this(context, null, null);
    }

    public WebViewPro(Context context, String urlToLoad) {
        this(context, null, urlToLoad);
    }

    public WebViewPro(Context context, AttributeSet attrs) {
        this(context, attrs, null);
    }

    public WebViewPro(Context context, AttributeSet attrs, String urlToLoad) {
        super(context, attrs);
        if (context instanceof Activity) {
            this.activity = (Activity) context;
        } else {
            throw new IllegalArgumentException("Context must be an Activity.");
        }
        initialize(context, urlToLoad);
    }

    private void initialize(Context context, String urlToLoad) {
        setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // === Custom Pull to Refresh ===
        refreshContainer = new RelativeLayout(context);
        refreshContainer.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // Simple refresh indicator (could be replaced with any custom animation or view)
        refreshIndicator = new ProgressBar(context);
        LayoutParams indicatorParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        indicatorParams.addRule(CENTER_HORIZONTAL);
        indicatorParams.topMargin = 40;
        refreshIndicator.setLayoutParams(indicatorParams);
        refreshIndicator.setVisibility(GONE);
        refreshContainer.addView(refreshIndicator);

        // === WebView ===
        webView = new WebView(context);
        webView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        refreshContainer.addView(webView);

        // === ProgressBar ===
        progressBar = new ProgressBar(context);
        LayoutParams pbParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        pbParams.addRule(CENTER_IN_PARENT);
        progressBar.setLayoutParams(pbParams);
        progressBar.setVisibility(GONE);

        addView(refreshContainer);
        addView(progressBar);

        setupWebViewSettings(context);
        setupWebViewClients(context);
        setupCustomPullToRefresh();

        // Load initial URL or fallback to local file
        String url = (urlToLoad != null && !urlToLoad.trim().isEmpty())
                ? urlToLoad
                : "file:///android_asset/index.html";
        webView.loadUrl(url);

        // Enable Chrome remote debugging for development
        if (BuildConfig.DEBUG && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true);
        }
    }

    private void setupWebViewSettings(Context context) {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setSupportZoom(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);

        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
    }

    private void setupWebViewClients(Context context) {
        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageStarted(WebView view, String url, Bitmap favicon) {
                progressBar.setVisibility(VISIBLE);
            }

            @Override public void onPageFinished(WebView view, String url) {
                progressBar.setVisibility(GONE);
                setRefreshing(false); // Stop refresh indicator
            }

            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    Toast.makeText(context, "Failed to load page. Showing offline fallback.", Toast.LENGTH_SHORT).show();
                    webView.loadUrl("file:///android_asset/offline.html");
                }
            }

            @Override public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                Toast.makeText(context, "SSL Error: " + error.toString(), Toast.LENGTH_SHORT).show();
                handler.proceed(); // Caution: consider overriding for stricter apps
            }

            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleCustomScheme(request.getUrl().toString(), context);
            }

            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleCustomScheme(url, context);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                if (activity == null) return false;
                WebViewPro.this.filePathCallback = filePathCallback;
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                activity.startActivityForResult(Intent.createChooser(intent, "Select File"), FILE_CHOOSER_REQUEST);
                return true;
            }

            @Override public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setVisibility(newProgress < 100 ? VISIBLE : GONE);
            }
        });

        // File/Media downloads
        webView.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
            try {
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                request.setMimeType(mimetype);
                request.addRequestHeader("User-Agent", userAgent);
                request.setTitle(URLUtil.guessFileName(url, contentDisposition, mimetype));
                request.setDescription("Downloading file...");
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,
                        URLUtil.guessFileName(url, contentDisposition, mimetype));
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

                DownloadManager dm = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
                if (dm != null) {
                    dm.enqueue(request);
                    Toast.makeText(context, "Downloading...", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Toast.makeText(context, "Download failed", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Minimal pull-to-refresh: pulls down to reload if scrolled to top.
    private void setupCustomPullToRefresh() {
        // Listen for touch events to trigger a manual refresh (simple version)
        webView.setOnTouchListener(new OnTouchListener() {
            private float startY = 0;
            @Override
            public boolean onTouch(View v, android.view.MotionEvent event) {
                switch (event.getAction()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        startY = event.getY();
                        break;
                    case android.view.MotionEvent.ACTION_UP:
                        float endY = event.getY();
                        if (endY - startY > 120 && webView.getScrollY() == 0) { // Pull down threshold
                            triggerRefresh();
                            return true;
                        }
                        break;
                }
                return false;
            }
        });
        setOnRefreshListener(new OnRefreshListener() {
            @Override
            public void onRefresh() {
                webView.reload();
            }
        });
    }

    private void triggerRefresh() {
        setRefreshing(true);
        if (onRefreshListener != null) {
            onRefreshListener.onRefresh();
        }
    }

    public void setOnRefreshListener(OnRefreshListener listener) {
        this.onRefreshListener = listener;
    }

    public void setRefreshing(boolean refreshing) {
        isRefreshing = refreshing;
        refreshIndicator.setVisibility(refreshing ? VISIBLE : GONE);
    }

    private boolean handleCustomScheme(String url, Context context) {
        try {
            if (url.startsWith("http://") || url.startsWith("https://")) {
                return false; // let WebView load it
            }

            if (url.startsWith("tel:") || url.startsWith("mailto:") || url.startsWith("intent:") || url.startsWith("sms:")) {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                context.startActivity(intent);
                return true;
            }

            return false;
        } catch (Exception e) {
            Toast.makeText(context, "Cannot open link", Toast.LENGTH_SHORT).show();
            return true;
        }
    }

    // === Public APIs ===

    public void onFileUploadResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_CHOOSER_REQUEST && filePathCallback != null) {
            Uri[] result = (resultCode == Activity.RESULT_OK && data != null && data.getData() != null)
                    ? new Uri[]{data.getData()}
                    : null;
            filePathCallback.onReceiveValue(result);
            filePathCallback = null;
        }
    }

    public void bindJS(String name, Object javaObject) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            webView.addJavascriptInterface(javaObject, name);
        }
    }

    public void reload() {
        webView.reload();
    }

    public boolean canGoBack() {
        return webView.canGoBack();
    }

    public void goBack() {
        if (canGoBack()) {
            webView.goBack();
        }
    }

    public WebView getWebView() {
        return webView;
    }

    public void launch() {
        if (activity != null) {
            activity.setContentView(this);
        }
    }
}
