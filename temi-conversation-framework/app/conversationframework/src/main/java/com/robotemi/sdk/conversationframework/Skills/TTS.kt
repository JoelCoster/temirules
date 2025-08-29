package com.robotemi.sdk.conversationframework.Skills

import android.util.Log
import com.robotemi.sdk.Robot
import com.robotemi.sdk.TtsRequest
import com.robotemi.sdk.conversationframework.UIHelper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * A skill for making the robot speak.
 */
class TTS(
    robot: Robot,
    uiHelper: UIHelper
) : Skill(robot, uiHelper), Robot.TtsListener {

    override val skillName: String = "TTS"

    override fun initialize() {
        super.initialize()
        robot.addTtsListener(this)
        Log.d("TTS", "TTS skill initialized with listener")
    }

    override fun cleanup() {
        super.cleanup()
        robot.removeTtsListener(this)
        Log.d("TTS", "TTS skill cleaned up")
    }

    /**
     * Makes the robot speak the given text and waits for completion.
     * This method blocks until the TTS is estimated to be finished.
     *
     * @param text The text to speak.
     */
    fun speak(text: String) {
        Log.d("TTS", "Speaking: '$text' (length: ${text.length})")
        if (text.isBlank()) {
            return
        }
        
        // Update UI on main thread and wait for it to complete
        val uiUpdateComplete = CountDownLatch(1)
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            uiHelper.addRobotMessage(text)
            uiUpdateComplete.countDown()
        }
        
        // Ensure conversation is finished before speaking
        robot.finishConversation()
        Thread.sleep(100L)
        
        val ttsRequest = TtsRequest.create(text, false)
        robot.speak(ttsRequest)
        
        // Calculate wait time based on text length
        val estimatedDurationMs = ((text.length * 100) + 300).toLong()
        Thread.sleep(estimatedDurationMs)
    }

    override fun onTtsStatusChanged(ttsRequest: TtsRequest) {
        Log.d("TTS", "TTS status changed: ${ttsRequest.status} for request: ${ttsRequest.speech}")
    }
}