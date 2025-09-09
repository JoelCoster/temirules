package com.robotemi.sdk.conversationframework.Skills

import android.content.Context
import android.util.Log
import com.robotemi.sdk.Robot
import com.robotemi.sdk.conversationframework.UIHelper

/**
 * A skill for managing robot locations.
 * This skill provides functionality to list, save, and delete locations.
 */
class Locations(
    context: Context,
    robot: Robot,
    uiHelper: UIHelper
) : Skill(context, robot, uiHelper) {

    override val skillName: String = "Locations"

    override fun initialize() {
        Log.d("Locations", "Locations skill initialized")
    }

    /**
     * Returns a string of all locations known by the robot, separated with commas.
     * 
     * @return Comma-separated string of location names, or "No locations saved" if empty
     */
    fun listLocations(): String {
        Log.d("Locations", "listLocations() called")
        
        return try {
            val locations = robot.locations
            if (locations.isEmpty()) {
                "No locations saved"
            } else {
                val locationList = locations.joinToString(", ")
                Log.d("Locations", "Retrieved ${locations.size} locations: $locationList")
                locationList
            }
        } catch (e: Exception) {
            Log.e("Locations", "Failed to retrieve locations", e)
            "Error retrieving locations"
        }
    }

    /**
     * Saves the current location using the supplied name.
     * 
     * @param name The name to save the current location as
     * @return Boolean indicating success or failure
     */
    fun saveCurrentLocation(name: String): Boolean {
        Log.d("Locations", "saveCurrentLocation() called with name: $name")
        
        if (name.isBlank()) {
            Log.e("Locations", "Location name cannot be empty")
            return false
        }
        
        val trimmedName = name.trim()
        
        return try {
            val result = robot.saveLocation(trimmedName)
            if (result) {
                Log.d("Locations", "Successfully saved location: $trimmedName")
            } else {
                Log.w("Locations", "Failed to save location: $trimmedName")
            }
            result
        } catch (e: Exception) {
            Log.e("Locations", "Error saving location: $trimmedName", e)
            false
        }
    }

    /**
     * Deletes the location with the given name from the robot's location list.
     * 
     * @param name The name of the location to delete
     * @return Boolean indicating success or failure
     */
    fun deleteLocation(name: String): Boolean {
        Log.d("Locations", "deleteLocation() called with name: $name")
        
        if (name.isBlank()) {
            Log.e("Locations", "Location name cannot be empty")
            return false
        }
        
        val trimmedName = name.trim()
        
        // Check if the location exists first
        val existingLocations = try {
            robot.locations
        } catch (e: Exception) {
            Log.e("Locations", "Error retrieving locations list", e)
            return false
        }
        
        val normalizedLocationName = trimmedName.lowercase()
        val locationExists = existingLocations.any { it.lowercase() == normalizedLocationName }
        
        if (!locationExists) {
            Log.w("Locations", "Location '$trimmedName' not found in saved locations: $existingLocations")
            return false
        }
        
        return try {
            val result = robot.deleteLocation(trimmedName)
            if (result) {
                Log.d("Locations", "Successfully deleted location: $trimmedName")
            } else {
                Log.w("Locations", "Failed to delete location: $trimmedName")
            }
            result
        } catch (e: Exception) {
            Log.e("Locations", "Error deleting location: $trimmedName", e)
            false
        }
    }

    /**
     * Get the number of saved locations.
     * 
     * @return Number of saved locations
     */
    fun getLocationCount(): Int {
        Log.d("Locations", "getLocationCount() called")
        
        return try {
            val count = robot.locations.size
            Log.d("Locations", "Location count: $count")
            count
        } catch (e: Exception) {
            Log.e("Locations", "Failed to get location count", e)
            0
        }
    }

    /**
     * Check if a specific location exists.
     * 
     * @param name The name of the location to check
     * @return Boolean indicating if the location exists
     */
    fun locationExists(name: String): Boolean {
        Log.d("Locations", "locationExists() called with name: $name")
        
        if (name.isBlank()) {
            return false
        }
        
        return try {
            val locations = robot.locations
            val normalizedName = name.trim().lowercase()
            val exists = locations.any { it.lowercase() == normalizedName }
            Log.d("Locations", "Location '$name' exists: $exists")
            exists
        } catch (e: Exception) {
            Log.e("Locations", "Error checking if location exists: $name", e)
            false
        }
    }

    override fun cleanup() {
        Log.d("Locations", "Locations skill cleanup")
        // No specific cleanup needed for this skill
    }
}
