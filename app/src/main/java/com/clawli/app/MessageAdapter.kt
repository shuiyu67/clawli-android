package com.clawli.app

import android.graphics.BitmapFactory
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/** 聊天消息（我发 / AI 回 / 工具状态 / 图片） */
data class ChatMessage(
    val kind: Int,             // KIND_*
    val text: String,
    val imageB64: String = "",
    val filename: String = "",
) {
    companion object {
        const val KIND_ME = 0
        const val KIND_AI = 1
        const val KIND_TOOL = 2
        const val KIND_IMAGE = 3
    }
}

class MessageAdapter(private val items: MutableList<ChatMessage>) :
    RecyclerView.Adapter<MessageAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val label: TextView = v.findViewById(R.id.itemLabel)
        val body: TextView = v.findViewById(R.id.itemBody)
        val image: ImageView = v.findViewById(R.id.itemImage)
    }

    override fun getItemViewType(position: Int): Int = items[position].kind

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val layout = when (viewType) {
            ChatMessage.KIND_ME -> R.layout.item_msg_me
            ChatMessage.KIND_TOOL -> R.layout.item_msg_tool
            ChatMessage.KIND_IMAGE -> R.layout.item_msg_image
            else -> R.layout.item_msg_ai
        }
        return VH(LayoutInflater.from(parent.context).inflate(layout, parent, false))
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val m = items[position]
        holder.body.text = m.text
        when (m.kind) {
            ChatMessage.KIND_ME -> holder.label.text = "我"
            ChatMessage.KIND_AI -> holder.label.text = "小狸"
            ChatMessage.KIND_TOOL -> holder.label.text = "工具"
            ChatMessage.KIND_IMAGE -> {
                holder.label.text = m.filename.ifEmpty { "图片" }
                try {
                    val bytes = Base64.decode(m.imageB64, Base64.DEFAULT)
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    holder.image.setImageBitmap(bmp)
                    holder.image.visibility = View.VISIBLE
                } catch (_: Exception) {
                    holder.image.visibility = View.GONE
                }
            }
        }
    }

    override fun getItemCount(): Int = items.size

    fun add(msg: ChatMessage): Int {
        items.add(msg)
        notifyItemInserted(items.size - 1)
        return items.size - 1
    }
}
