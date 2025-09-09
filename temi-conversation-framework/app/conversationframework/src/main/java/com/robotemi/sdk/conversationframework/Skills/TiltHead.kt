package com.robotemi.sdk.conversationframework.Skills

import android.content.Context
import android.util.Log
import com.robotemi.sdk.Robot
import com.robotemi.sdk.conversationframework.UIHelper

/**
 * A skill for tilting the robot's head.
 */
class TiltHead(
    context: Context,
    robot: Robot,
    uiHelper: UIHelper
) : Skill(context, robot, uiHelper) {

    override val skillName: String = "TiltHead"

    override fun initialize() {
        Log.d("TiltHead", "TiltHead skill initialized")
    }

    /**
     * Tilts the robot's head up.
     */
    fun up() {
        Log.d("TiltHead", "up() method called")
        robot.tiltAngle(45)
        Log.d("TiltHead", "up() method completed")
    }

    /**
     * Moves the robot's head back to the default position.
     */
    fun down() {
        robot.tiltAngle(0) // Default position
    }
}