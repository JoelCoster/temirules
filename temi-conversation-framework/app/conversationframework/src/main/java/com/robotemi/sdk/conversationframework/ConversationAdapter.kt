package com.robotemi.sdk.conversationframework

import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * Adapter for displaying conversation messages in a RecyclerView
 */
class ConversationAdapter(private val context: Context) : 
    RecyclerView.Adapter<ConversationAdapter.MessageViewHolder>() {
    
    // List of messages in the conversation
    private val messages = mutableListOf<ConversationMessage>()
    
    /**
     * Add a new message to the conversation
     */
    fun addMessage(message: ConversationMessage) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }
    
    /**
     * Clear all messages from the conversation
     */
    fun clearMessages() {
        val size = messages.size
        messages.clear()
        notifyItemRangeRemoved(0, size)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_conversation, parent, false)
        return MessageViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(messages[position])
    }
    
    override fun getItemCount(): Int = messages.size
    
    /**
     * ViewHolder for conversation messages
     */
    inner class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvMessage: TextView = itemView.findViewById(R.id.tvMessage)
        private val layoutParams = tvMessage.layoutParams as LinearLayout.LayoutParams
        
        fun bind(message: ConversationMessage) {
            // Set the message text (with prefix)
            tvMessage.text = when (message.type) {
                MessageType.USER -> context.getString(R.string.user_said, message.text)
                MessageType.ROBOT -> context.getString(R.string.temi_said, message.text)
            }
            
            // Set the message bubble style and alignment based on type
            when (message.type) {
                MessageType.USER -> {
                    tvMessage.setBackgroundResource(R.drawable.conversation_bubble_user)
                    layoutParams.gravity = Gravity.END
                }
                MessageType.ROBOT -> {
                    tvMessage.setBackgroundResource(R.drawable.conversation_bubble)
                    layoutParams.gravity = Gravity.START
                }
            }
            
            tvMessage.layoutParams = layoutParams
        }
    }
}

/**
 * Represents a single message in the conversation
 */
data class ConversationMessage(
    val text: String,
    val type: MessageType
)

/**
 * Defines the types of messages in the conversation
 */
enum class MessageType {
    USER,   // Messages from the user
    ROBOT   // Messages from Temi
}
