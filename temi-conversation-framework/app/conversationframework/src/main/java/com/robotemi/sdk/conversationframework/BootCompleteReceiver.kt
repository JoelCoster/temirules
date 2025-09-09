package com.robotemi.sdk.conversationframework

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.robotemi.sdk.Robot
import com.robotemi.sdk.listeners.OnRobotReadyListener

/**
 * Receiver to handle boot complete event and start the app in kiosk mode
 */
class BootCompleteReceiver: BroadcastReceiver(), OnRobotReadyListener {

    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d("BootCompleteReceiver", "Boot received, starting app")
        
        // Start the app in kiosk mode
        val launchIntent = Intent(context, InteractionManager::class.java)
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context?.startActivity(launchIntent)
        
        // Set up robot ready listener to initialize the robot when ready
        Robot.getInstance().addOnRobotReadyListener(this)
    }

    override fun onRobotReady(isReady: Boolean) {
        if (isReady) {
            Log.d("BootCompleteReceiver", "Robot is ready")
            Robot.getInstance().removeOnRobotReadyListener(this)
        }
    }
}