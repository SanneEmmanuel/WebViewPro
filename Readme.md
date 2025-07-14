# WebViewPro

A flexible and beginner friendly Implementation of Android WebView, for beginners, with lesser code to turn your websites into apps , containing ProgressBar, and SwipeRefreshLayout for seamless web browsing and file handling in your app.  
Supports both **Kotlin** and **Java** integration!

## Features

- **WebView Integration:** Easily load web pages or local assets.
- **ProgressBar:** Displays page load progress.
- **SwipeRefreshLayout:** Pull-to-refresh for reloading web content.
- **Download Support:** Handles file/media downloads via Android's DownloadManager.
- **File Uploads:** Supports file chooser for HTML `<input type="file">`.
- **Custom Schemes:** Opens phone, mail, intent, and SMS links in external apps.
- **Offline Fallback:** Loads a local offline page if network errors occur.
- **JavaScript Bridge:** Bind Java/Kotlin objects to JavaScript for advanced integrations.
- **Go Back Navigation:** Programmatically control browser history.
- **Chrome Debugging:** Debug using Chrome DevTools (when in debug mode).

## Usage

### 1. Add the Dependency

Copy `WebViewPro.kt` (for Kotlin) or `WebViewPro.java` (for Java) to your project, e.g., `com.sanne.webviewpro.WebViewPro`.

### 2. Add to Your Layout

You can add `WebViewPro` via XML or programmatically.

**XML Example:**
```xml
<com.sanne.webviewpro.WebViewPro
    android:id="@+id/webview_pro"
    android:layout_width="match_parent"
    android:layout_height="match_parent"/>
```

**Kotlin Example:**
```kotlin
val webViewPro = WebViewPro(this, "https://example.com")
webViewPro.launch() // Sets as Activity contentView
```

**Java Example:**
```java
WebViewPro webViewPro = new WebViewPro(this, "https://example.com");
webViewPro.launch(); // Sets as Activity contentView
```

### 3. Handling File Uploads

In your Activity, forward results to `WebViewPro`:

**Kotlin:**
```kotlin
override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    webViewPro.onFileUploadResult(requestCode, resultCode, data)
}
```
**Java:**
```java
@Override
protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    webViewPro.onFileUploadResult(requestCode, resultCode, data);
}
```

### 4. Bind JavaScript APIs

**Kotlin:**
```kotlin
webViewPro.bindJS("AndroidBridge", MyBridge())
```
**Java:**
```java
webViewPro.bindJS("AndroidBridge", new MyBridge());
```

## Customization

- Load your own URL or asset:  
  `WebViewPro(context, "https://your.url")`
- Offline fallback: Place `offline.html` in your `assets` folder.
- Local start page: Place `index.html` in your `assets` folder.

## License

MIT

## Author

Sanne Karibo

## Contributions

Feel free to fork, improve and submit pull requests!
