package com.robotemi.sdk.conversationframework.Skills

import android.util.Log
import com.robotemi.sdk.Robot
import com.robotemi.sdk.constants.SdkConstants
import com.robotemi.sdk.conversationframework.UIHelper
import com.robotemi.sdk.navigation.model.SpeedLevel

/**
 * A skill for robot movement and navigation.
 * This skill provides functionality to navigate to locations, go home, and follow users.
 */
class Move(
    robot: Robot,
    uiHelper: UIHelper
) : Skill(robot, uiHelper) {

    override val skillName: String = "Move"
    
    // State tracking
    @Volatile
    private var isMoving = false
    
    @Volatile
    private var isFollowing = false

    override fun initialize() {
        Log.d("Move", "Move skill initialized")
    }

    /**
     * Navigate the robot to a previously saved location.
     * 
     * @param locationName The name of the saved location to navigate to
     * @param speedLevel Optional speed level for navigation (HIGH, MEDIUM, SLOW, or custom)
     * @param backwards Optional parameter to move backwards to destination
     */
    @JvmOverloads
    fun goToLocation(
        locationName: String, 
        speedLevel: SpeedLevel? = null,
        backwards: Boolean? = false
    ) {
        Log.d("Move", "goToLocation() called with location: $locationName")
        
        if (locationName.isBlank()) {
            Log.e("Move", "Location name cannot be empty")
            return
        }
        
        // Check if the location exists in saved locations
        val savedLocations = robot.locations
        val normalizedLocationName = locationName.trim().lowercase()
        val locationExists = savedLocations.any { it.lowercase() == normalizedLocationName }
        
        if (!locationExists) {
            Log.w("Move", "Location '$locationName' not found in saved locations: $savedLocations")
        }
        
        try {
            isMoving = true
            isFollowing = false
            robot.goTo(
                location = locationName,
                backwards = backwards,
                speedLevel = speedLevel
            )
            Log.d("Move", "Successfully initiated navigation to $locationName")
        } catch (e: Exception) {
            isMoving = false
            Log.e("Move", "Failed to navigate to $locationName", e)
        }
    }

    /**
     * Navigate the robot to its home base (charging station).
     * 
     * @param speedLevel Optional speed level for navigation
     */
    @JvmOverloads
    fun goHome(speedLevel: SpeedLevel? = null) {
        Log.d("Move", "goHome() called")
        
        try {
            isMoving = true
            isFollowing = false
            robot.goTo(
                location = SdkConstants.LOCATION_HOME_BASE,
                speedLevel = speedLevel
            )
            Log.d("Move", "Successfully initiated navigation to home base")
        } catch (e: Exception) {
            isMoving = false
            Log.e("Move", "Failed to navigate to home base", e)
        }
    }

    /**
     * Start following the user.
     * The robot will use its sensors to detect and follow the user around.
     * 
     * @param speedLevel Optional speed level for following (defaults to medium speed)
     */
    @JvmOverloads
    fun follow(speedLevel: SpeedLevel? = SpeedLevel.MEDIUM) {
        Log.d("Move", "follow() called")
        
        try {
            isFollowing = true
            isMoving = false
            robot.beWithMe(speedLevel)
            Log.d("Move", "Successfully started following user")
        } catch (e: Exception) {
            isFollowing = false
            Log.e("Move", "Failed to start following user", e)
        }
    }

    /**
     * Stop following the user and halt any current movement.
     * This will stop the robot from following and cancel any ongoing navigation.
     */
    fun stopFollowing() {
        Log.d("Move", "stopFollowing() called")
        
        try {
            isFollowing = false
            isMoving = false
            robot.stopMovement()
            Log.d("Move", "Successfully stopped movement and following")
        } catch (e: Exception) {
            Log.e("Move", "Failed to stop movement", e)
        }
    }

    /**
     * Get a list of all saved location names.
     * 
     * @return List of saved location names
     */
    fun getSavedLocations(): List<String> {
        Log.d("Move", "getSavedLocations() called")
        
        return try {
            val locations = robot.locations
            Log.d("Move", "Retrieved ${locations.size} saved locations: $locations")
            locations
        } catch (e: Exception) {
            Log.e("Move", "Failed to retrieve saved locations", e)
            emptyList()
        }
    }

    /**
     * Stop any current movement without specifically stopping follow mode.
     * This is useful for pausing movement temporarily.
     */
    fun stopMovement() {
        Log.d("Move", "stopMovement() called")
        
        try {
            isMoving = false
            isFollowing = false
            robot.stopMovement()
            Log.d("Move", "Successfully stopped movement")
        } catch (e: Exception) {
            Log.e("Move", "Failed to stop movement", e)
        }
    }
    
    /**
     * Check if the robot is currently moving to a location.
     * 
     * @return true if the robot is currently navigating
     */
    fun isMoving(): Boolean {
        return isMoving
    }
    
    /**
     * Check if the robot is currently following a user.
     * 
     * @return true if the robot is currently following
     */
    fun isFollowing(): Boolean {
        return isFollowing
    }
    
    /**
     * Get the current movement state as a string.
     * 
     * @return String describing current movement state
     */
    fun getMovementState(): String {
        return when {
            isFollowing -> "following"
            isMoving -> "navigating"
            else -> "stationary"
        }
    }

    override fun cleanup() {
        Log.d("Move", "Move skill cleanup")
        // Stop any ongoing movement when the skill is being cleaned up
        try {
            isMoving = false
            isFollowing = false
            robot.stopMovement()
        } catch (e: Exception) {
            Log.e("Move", "Error during cleanup", e)
        }
    }
}
