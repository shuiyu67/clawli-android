package com.clawli.app

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Clawli WebSocket 协议客户端。
 *
 * 协议（与 xiaoli-cli 的 clawli_server.py 对齐）：
 *   连接后首帧: {"type":"auth","password":<密码>}
 *   应答:       {"type":"auth_success"} / {"type":"auth_failed","message":...}
 *   发文本:     {"type":"message","content":"..."}           → 直接进 AI
 *   发文件:     {"type":"file","file":"<base64>","filename":"...","text":"..."} → 落盘电脑
 *   收文本:     {"type":"text","content":"..."}              → AI 回复
 *   收工具:     {"type":"tool_status","tool","args","status","result"}
 *   收图片:     {"type":"image","image":"<base64>","filename","caption"}
 *
 * 局域网直连: ws://<IP>:9079
 * 云模式:    ws://<中继>/ws?room=<房间码>&role=phone[&token=<令牌>]
 */
class ClawliSocket(private val listener: Listener) {

    interface Listener {
        fun onState(state: String)                 // 连接状态人话
        fun onAuth(ok: Boolean, message: String)   // 鉴权结果
        fun onText(content: String)                // AI 文本
        fun onToolStatus(tool: String, args: String, status: String, result: String)
        fun onImage(b64: String, filename: String, caption: String)
        fun onRaw(type: String, json: JSONObject)  // 其他类型兜底
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)     // 长连接不读超时
        .connectTimeout(10, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)        // 心跳保活（过 NAT/中继）
        .build()

    private var ws: WebSocket? = null
    @Volatile var authenticated: Boolean = false
        private set

    fun connect(url: String, password: String) {
        disconnect()
        authenticated = false
        listener.onState("连接中…")
        val req = Request.Builder().url(url).build()
        ws = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                // Clawli 鉴权帧（中继透明转发，此帧直达 PC 端）
                webSocket.send(JSONObject().apply {
                    put("type", "auth")
                    put("password", password)
                }.toString())
                listener.onState("已连接，鉴权中…")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val o = JSONObject(text)
                    when (optString(o, "type")) {
                        "auth_success" -> {
                            authenticated = true
                            listener.onState("已连接")
                            listener.onAuth(true, "认证成功")
                        }
                        "auth_failed" -> {
                            listener.onAuth(false, optString(o, "message", "密码错误"))
                            disconnect()
                        }
                        "text", "message" -> listener.onText(optString(o, "content"))
                        "tool_status" -> listener.onToolStatus(
                            optString(o, "tool"), optString(o, "args"),
                            optString(o, "status"), optString(o, "result"))
                        "image" -> listener.onImage(
                            optString(o, "image"), optString(o, "filename"), optString(o, "caption"))
                        "error" -> listener.onState("错误: ${optString(o, "message")}")
                        else -> listener.onRaw(optString(o, "type"), o)
                    }
                } catch (_: Exception) {
                    listener.onText(text)   // 非 JSON 兜底当文本
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                listener.onText(bytes.utf8())
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                authenticated = false
                listener.onState("已断开 ($code)")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                authenticated = false
                listener.onState("连接失败: ${t.message ?: t.javaClass.simpleName}")
            }
        })
    }

    fun sendText(content: String): Boolean {
        val w = ws ?: return false
        return w.send(JSONObject().apply {
            put("type", "message")
            put("content", content)
        }.toString())
    }

    fun sendFile(base64: String, filename: String, text: String = ""): Boolean {
        val w = ws ?: return false
        return w.send(JSONObject().apply {
            put("type", "file")
            put("file", base64)
            put("filename", filename)
            put("text", text)
        }.toString())
    }

    fun disconnect() {
        ws?.close(1000, "bye")
        ws = null
        authenticated = false
    }

    private fun optString(o: JSONObject, key: String, def: String = ""): String =
        if (o.has(key) && !o.isNull(key)) o.optString(key, def) else def
}
