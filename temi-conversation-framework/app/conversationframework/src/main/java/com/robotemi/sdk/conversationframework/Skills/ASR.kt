package com.robotemi.sdk.conversationframework.Skills

import android.util.Log
import com.robotemi.sdk.Robot
import com.robotemi.sdk.conversationframework.UIHelper

/**
 * A skill for Automatic Speech Recognition (ASR) functionality.
 * Provides methods to trigger speech recognition.
 * Note: ASR results are handled by the main InteractionManager.
 */
class ASR(
    robot: Robot,
    uiHelper: UIHelper
) : Skill(robot, uiHelper) {

    override val skillName: String = "ASR"
    
    // Flag to track if ASR is currently active
    @Volatile
    private var isListening = false
    
    override fun initialize() {
        super.initialize()
        Log.d("ASR", "ASR skill initialized")
    }

    override fun cleanup() {
        super.cleanup()
        Log.d("ASR", "ASR skill cleaned up")
    }

    /**
     * Start listening for speech input.
     * Ensures clean conversation state and uses askQuestion to trigger ASR.
     */
    
    fun listen() {
        Log.d("ASR", "Starting ASR listening with proper conversation management")
        isListening = true
        uiHelper.updateListeningState(true)
        robot.finishConversation()
        
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            robot.askQuestion(" ")
        }, 100)
    }
    
    /**
     * Stop the current ASR session
     */
    fun stopListening() {
        Log.d("ASR", "Stopping ASR listening")
        isListening = false
        uiHelper.updateListeningState(false)
        robot.finishConversation()
    }
    
    /**
     * Check if ASR is currently listening
     */
    fun isListening(): Boolean {
        return isListening
    }
    
    /**
     * Internal method to update listening state (called by InteractionManager)
     */
    fun setListeningState(listening: Boolean) {
        isListening = listening
        uiHelper.updateListeningState(listening)
    }
}