package com.example

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val fileName = it.lastPathSegment ?: "server_icon.png"
            webView.evaluateJavascript("window.onImageSelected('$fileName')", null)
            Toast.makeText(this, "Foto seleccionada: $fileName", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        webView = WebView(this).apply {
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                allowFileAccess = true
                allowFileAccessFromFileURLs = true
                allowUniversalAccessFromFileURLs = true
                useWideViewPort = true
                loadWithOverviewMode = true
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                cacheMode = WebSettings.LOAD_DEFAULT
            }
            webViewClient = WebViewClient()
            addJavascriptInterface(AndroidBridge(this@MainActivity, this), "AndroidBridge")
            loadUrl("file:///android_asset/ui/index.html")
        }

        setContentView(webView)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    finish()
                }
            }
        })
    }

    fun launchImagePicker() {
        pickImageLauncher.launch("image/*")
    }

    class AndroidBridge(private val activity: MainActivity, private val webView: WebView) {

        @JavascriptInterface
        fun pickServerImage() {
            activity.runOnUiThread {
                activity.launchImagePicker()
            }
        }

        @JavascriptInterface
        fun fetchGuilds(token: String) {
            // Simulated Discord API / Python backend integration returning guilds JSON
            activity.runOnUiThread {
                val mockGuildsJson = "[" +
                    "{\"id\":\"10928347102\",\"name\":\"🎮 Gaming Community [Admin]\"}," +
                    "{\"id\":\"88371920384\",\"name\":\"🔥 VIP Lounge Server [Admin]\"}," +
                    "{\"id\":\"44920183742\",\"name\":\"🤖 Bot Testing Hub [Admin]\"}" +
                    "]"
                webView.evaluateJavascript("window.renderServers('$mockGuildsJson')", null)
            }
        }

        @JavascriptInterface
        fun executeNuke(token: String, serverId: String, serverName: String, channelName: String, channelLimit: String, messageContent: String, messageLimit: String) {
            android.util.Log.d("AutoNuke", "Executing Nuke: Server=$serverId, Name=$serverName, Channels=$channelLimit of $channelName, Messages=$messageLimit")
        }

        @JavascriptInterface
        fun showToast(message: String) {
            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
        }
    }
}
