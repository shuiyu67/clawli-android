package com.clawli.app

import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONObject

/**
 * 聊天页：文本直接进 AI；图片/文件上传到电脑磁盘（不喂 AI）；工具调用状态展示。
 */
class ChatActivity : AppCompatActivity(), ClawliSocket.Listener {

    private lateinit var socket: ClawliSocket
    private lateinit var adapter: MessageAdapter
    private lateinit var input: EditText
    private lateinit var sendBtn: Button
    private lateinit var attachBtn: Button
    private lateinit var statusView: android.widget.TextView
    private lateinit var recycler: RecyclerView

    private val pickFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        try {
            contentResolver.openInputStream(uri)?.use { ins ->
                val bytes = ins.readBytes()
                val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                val name = queryDisplayName(uri).ifEmpty { "file.bin" }
                // 大文件提示（base64 后 ~1.33x，控制在 48MB 内）
                if (bytes.size > 36L * 1024 * 1024) {
                    Toast.makeText(this, "文件过大（>36MB），已取消", Toast.LENGTH_LONG).show()
                    return@use
                }
                if (socket.sendFile(b64, name, input.text.toString().trim())) {
                    addMsg(ChatMessage(ChatMessage.KIND_ME, "上传文件: $name（${bytes.size / 1024} KB）"))
                } else {
                    addMsg(ChatMessage(ChatMessage.KIND_TOOL, "上传失败：未连接"))
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "读取文件失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun queryDisplayName(uri: Uri): String {
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) return c.getString(idx) ?: ""
        }
        return uri.lastPathSegment ?: ""
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        recycler = findViewById(R.id.recycler)
        input = findViewById(R.id.input)
        sendBtn = findViewById(R.id.sendBtn)
        attachBtn = findViewById(R.id.attachBtn)
        statusView = findViewById(R.id.statusView)

        adapter = MessageAdapter(mutableListOf())
        recycler.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        recycler.adapter = adapter

        socket = ClawliSocket(this)
        val url = intent.getStringExtra("url") ?: ""
        val pw = intent.getStringExtra("password") ?: ""
        statusView.text = url
        socket.connect(url, pw)

        sendBtn.setOnClickListener {
            val text = input.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener
            if (socket.sendText(text)) {
                addMsg(ChatMessage(ChatMessage.KIND_ME, text))
                input.setText("")
            } else {
                Toast.makeText(this, "未连接，发送失败", Toast.LENGTH_SHORT).show()
            }
        }

        attachBtn.setOnClickListener {
            // 只选文件/图片；上传到电脑磁盘，不进 AI
            pickFile.launch(arrayOf("*/*"))
        }
    }

    private fun addMsg(m: ChatMessage) {
        runOnUiThread {
            val pos = adapter.add(m)
            recycler.scrollToPosition(pos)
        }
    }

    // ── ClawliSocket.Listener（OkHttp 线程回调，addMsg 内部切 UI 线程）──

    override fun onState(state: String) {
        runOnUiThread { statusView.text = state }
    }

    override fun onAuth(ok: Boolean, message: String) {
        addMsg(ChatMessage(ChatMessage.KIND_TOOL, if (ok) "认证成功" else "认证失败: $message"))
    }

    override fun onText(content: String) {
        addMsg(ChatMessage(ChatMessage.KIND_AI, content))
    }

    override fun onToolStatus(tool: String, args: String, status: String, result: String) {
        val mark = if (status == "success") "✓" else if (status == "calling") "…" else status
        addMsg(ChatMessage(ChatMessage.KIND_TOOL, "$tool $mark  ${args.take(60)}"))
    }

    override fun onImage(b64: String, filename: String, caption: String) {
        addMsg(ChatMessage(ChatMessage.KIND_IMAGE, caption, imageB64 = b64, filename = filename))
    }

    override fun onRaw(type: String, json: JSONObject) {
        if (type == "relay") return   // 中继控制帧静默
        addMsg(ChatMessage(ChatMessage.KIND_TOOL, "[$type] ${json.toString().take(120)}"))
    }

    override fun onDestroy() {
        socket.disconnect()
        super.onDestroy()
    }
}
