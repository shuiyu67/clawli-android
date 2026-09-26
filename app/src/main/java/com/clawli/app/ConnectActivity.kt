package com.clawli.app

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * 连接配置页：局域网直连（IP:9079+密码）或云模式（中继+房间码+令牌+密码）。
 * 配置写入 SharedPreferences，下次进入自动回填。
 */
class ConnectActivity : AppCompatActivity() {

    private val prefs by lazy { getSharedPreferences("clawli", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_connect)

        val modeGroup = findViewById<RadioGroup>(R.id.modeGroup)
        val lanBox = findViewById<android.view.View>(R.id.lanBox)
        val cloudBox = findViewById<android.view.View>(R.id.cloudBox)
        val host = findViewById<EditText>(R.id.lanHost)
        val port = findViewById<EditText>(R.id.lanPort)
        val relay = findViewById<EditText>(R.id.cloudRelay)
        val room = findViewById<EditText>(R.id.cloudRoom)
        val token = findViewById<EditText>(R.id.cloudToken)
        val password = findViewById<EditText>(R.id.password)
        val connectBtn = findViewById<Button>(R.id.connectBtn)

        // 回填历史配置
        host.setText(prefs.getString("lan_host", ""))
        port.setText(prefs.getString("lan_port", "9079"))
        relay.setText(prefs.getString("cloud_relay", ""))
        room.setText(prefs.getString("cloud_room", ""))
        token.setText(prefs.getString("cloud_token", ""))
        password.setText(prefs.getString("password", ""))
        val savedMode = prefs.getInt("mode", R.id.modeLan)
        modeGroup.check(savedMode)

        fun syncVisibility() {
            val lan = modeGroup.checkedRadioButtonId == R.id.modeLan
            lanBox.visibility = if (lan) android.view.View.VISIBLE else android.view.View.GONE
            cloudBox.visibility = if (lan) android.view.View.GONE else android.view.View.VISIBLE
        }
        syncVisibility()
        modeGroup.setOnCheckedChangeListener { _, _ -> syncVisibility() }

        connectBtn.setOnClickListener {
            val pw = password.text.toString()
            val url: String = if (modeGroup.checkedRadioButtonId == R.id.modeLan) {
                val h = host.text.toString().trim()
                val p = port.text.toString().trim().ifEmpty { "9079" }
                if (h.isEmpty()) {
                    Toast.makeText(this, "填 PC 的局域网 IP", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                "ws://$h:$p"
            } else {
                val r = relay.text.toString().trim().trimEnd('/')
                val rm = room.text.toString().trim()
                val tk = token.text.toString().trim()
                if (r.isEmpty() || rm.isEmpty()) {
                    Toast.makeText(this, "填中继地址和房间码", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                // 统一成 <relay>/ws?room=..[&token=..] 形态（中继约定）
                buildString {
                    append(if (r.endsWith("/ws")) r else "$r/ws")
                    append("?room=").append(rm)
                    if (tk.isNotEmpty()) append("&token=").append(tk)
                }
            }

            prefs.edit()
                .putInt("mode", modeGroup.checkedRadioButtonId)
                .putString("lan_host", host.text.toString())
                .putString("lan_port", port.text.toString())
                .putString("cloud_relay", relay.text.toString())
                .putString("cloud_room", room.text.toString())
                .putString("cloud_token", token.text.toString())
                .putString("password", pw)
                .apply()

            startActivity(
                Intent(this, ChatActivity::class.java)
                    .putExtra("url", url)
                    .putExtra("password", pw)
            )
        }
    }
}
