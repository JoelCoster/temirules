package com.robotemi.sdk.conversationframework

import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.robotemi.sdk.Robot
import com.robotemi.sdk.TtsRequest

/**
 * Helper class for managing UI-related operations
 */
class UIHelper(
    private val activity: AppCompatActivity,
    private val robot: Robot
) {
    // UI Elements
    private lateinit var btnListen: Button
    private lateinit var btnReloadRules: Button
    private lateinit var tvStatus: TextView
    private lateinit var rvConversationLog: RecyclerView
    private lateinit var conversationAdapter: ConversationAdapter
    private lateinit var btnExit: ImageButton
    private lateinit var btnKioskMode: ImageButton
    
    // State tracking
    private var isListening = false
    
    /**
     * Initialize and set up the UI elements
     */
    fun setupUi() {
        // Initialize views
        btnListen = activity.findViewById(R.id.btnListen)
        btnReloadRules = activity.findViewById(R.id.btnReloadRules)
        tvStatus = activity.findViewById(R.id.tvStatus)
        rvConversationLog = activity.findViewById(R.id.rvConversationLog)
        btnExit = activity.findViewById(R.id.btnExit)
        btnKioskMode = activity.findViewById(R.id.btnKioskMode)
        
        // Set up RecyclerView
        conversationAdapter = ConversationAdapter(activity)
        rvConversationLog.apply {
            layoutManager = LinearLayoutManager(activity).apply {
                stackFromEnd = true  // Start from bottom to show latest messages at bottom
                reverseLayout = false
            }
            adapter = conversationAdapter
        }
        
        // Set up Listen button
        btnListen.setOnClickListener { 
            if (!isListening) {
                startListening(false) // false means not from wakeup word
            } else {
                stopListening()
            }
        }

        // Set up Reload Rules button
        btnReloadRules.setOnClickListener {
            reloadRules()
        }
        
        // Setup Exit button
        btnExit.setOnClickListener {
            exitToLauncher()
        }
        
        // Set up Kiosk Mode button - only show if application is in kiosk mode
        if (robot.isKioskModeOn()) {
            btnKioskMode.visibility = View.VISIBLE
            btnKioskMode.setOnClickListener {
                toggleKioskMode()
            }
        } else {
            btnKioskMode.visibility = View.GONE
        }
    }

    /**
     * Start Temi's voice recognition CHECK IF STILL IN USE
     */
    fun startListening(fromWakeupWord: Boolean = false) {
        updateListeningState(true)
        robot.askQuestion("What can I do for You")
    }
    
    /**
     * Stop Temi's voice recognition
     */
    fun stopListening() {
        updateListeningState(false)
        robot.finishConversation()
        updateStatusDisplay()
    }
    
    /**
     * Update the UI to show the current listening state
     */
    fun updateListeningState(listening: Boolean) {
        // Ensure this runs on the UI thread
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            // Already on UI thread
            updateListeningStateInternal(listening)
        } else {
            // Post to UI thread
            Handler(Looper.getMainLooper()).post {
                updateListeningStateInternal(listening)
            }
        }
    }
    
    private fun updateListeningStateInternal(listening: Boolean) {
        isListening = listening
        if (listening) {
            btnListen.text = activity.getString(R.string.listening)
            btnListen.setCompoundDrawablesWithIntrinsicBounds(
                ContextCompat.getDrawable(activity, R.drawable.ic_mic_listening),
                null, null, null
            )
        } else {
            btnListen.text = activity.getString(R.string.listen)
            btnListen.setCompoundDrawablesWithIntrinsicBounds(
                ContextCompat.getDrawable(activity, R.drawable.ic_mic),
                null, null, null
            )
        }
        
        updateStatusDisplay()
    }
    
    /**
     * Update the status display to show both listening and navigation states
     * Can also provide a custom navigation status message
     */
    fun updateStatusDisplay(navStatusMessage: String? = null, isNavigating: Boolean = false) {
        // Ensure this runs on the UI thread
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            // Already on UI thread
            updateStatusDisplayInternal(navStatusMessage, isNavigating)
        } else {
            // Post to UI thread
            Handler(Looper.getMainLooper()).post {
                updateStatusDisplayInternal(navStatusMessage, isNavigating)
            }
        }
    }
    
    private fun updateStatusDisplayInternal(navStatusMessage: String? = null, isNavigating: Boolean = false) {
        val statusText = StringBuilder()
        if (isListening) {
            statusText.append("Listening")
        } else {
            statusText.append("Ready")
        }
        if (isNavigating) {
            if (navStatusMessage != null) {
                statusText.append(" | $navStatusMessage")
            } else {
                statusText.append(" | Navigating")
            }
        }
        tvStatus.text = statusText.toString()
    }
    
    /**
     * Add a user message to the conversation
     */
    fun addUserMessage(message: String) {
        // Store in Memory if possible
        storeInMemory("user", message)
        
        // Ensure this runs on the UI thread
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            // Already on UI thread
            conversationAdapter.addMessage(ConversationMessage(message, MessageType.USER))
            scrollToBottom()
        } else {
            // Post to UI thread
            Handler(Looper.getMainLooper()).post {
                conversationAdapter.addMessage(ConversationMessage(message, MessageType.USER))
                scrollToBottom()
            }
        }
    }

    /**
     * Add a robot message to the conversation
     */
    fun addRobotMessage(message: String) {
        // Store in Memory if possible
        storeInMemory("assistant", message)
        
        // Ensure this runs on the UI thread
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            // Already on UI thread
            conversationAdapter.addMessage(ConversationMessage(message, MessageType.ROBOT))
            scrollToBottom()
        } else {
            // Post to UI thread
            Handler(Looper.getMainLooper()).post {
                conversationAdapter.addMessage(ConversationMessage(message, MessageType.ROBOT))
                scrollToBottom()
            }
        }
    }
    
    /**
     * Scroll to the bottom of the conversation
     */
    private fun scrollToBottom() {
        rvConversationLog.post {
            rvConversationLog.smoothScrollToPosition(conversationAdapter.itemCount - 1)
        }
    }
    
    /**
     * Exit the application and return to the launcher/home screen
     */
    fun exitToLauncher() {
        robot.showAppList()
    }
    
    /**
     * Toggle kiosk mode on/off
     */
    fun toggleKioskMode() {
        val isKioskModeOn = robot.isKioskModeOn()
        robot.setKioskModeOn(!isKioskModeOn)
        
        if (isKioskModeOn) {
            Toast.makeText(activity, "Kiosk mode disabled", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(activity, "Kiosk mode enabled", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Check if the robot is currently in listening mode
     */
    fun isListening(): Boolean {
        return isListening
    }

    /**
     * Reload rules from the web location
     */
    private fun reloadRules() {
        // Notify the InteractionManager to reload rules
        (activity as? InteractionManager)?.reloadRules()
    }

    /**
     * Get access to the memory through the InteractionManager
     */
    fun getMemory(): Memory? {
        return (activity as? InteractionManager)?.memory
    }

    /**
     * Store a message in Memory as conversation history
     */
    private fun storeInMemory(role: String, message: String) {
        try {
            val memory = getMemory()
            memory?.addToConversationHistory(role, message)
        } catch (e: Exception) {
            // Log but don't fail if memory storage fails
            android.util.Log.w("UIHelper", "Failed to store message in memory: ${e.message}")
        }
    }
}