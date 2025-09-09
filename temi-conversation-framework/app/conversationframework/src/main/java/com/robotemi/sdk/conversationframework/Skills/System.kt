package com.robotemi.sdk.conversationframework.Skills

import android.content.Context
import android.content.Intent
import android.util.Log
import com.robotemi.sdk.Robot
import com.robotemi.sdk.constants.Page
import com.robotemi.sdk.conversationframework.UIHelper

/**
 * A skill for system control functions including app launching.
 * This skill provides functionality to open apps/system pages.
 */
class System(
    context: Context,
    robot: Robot,
    uiHelper: UIHelper
) : Skill(context, robot, uiHelper) {

    override val skillName: String = "System"

    override fun initialize() {
        Log.d("System", "System skill initialized")
    }

    /**
     * Open an app by package name or system page by name.
     * For system pages: "Settings", "Map", "Contacts", "Locations", "Apps", "Home", "Tours"
     * For other apps: provide the full package name (e.g., "com.android.settings")
     * 
     * @param appName The name of the app/page to open
     */
    fun openApp(appName: String) {
        Log.d("System", "openApp() called with appName: $appName")
        
        if (appName.isBlank()) {
            Log.e("System", "App name cannot be empty")
            return
        }

        try {
            // Try to open as system page first
            val systemPage = mapToSystemPage(appName)
            if (systemPage != null) {
                robot.startPage(systemPage)
                Log.d("System", "Opened system page: $appName")
                return
            }

            // If not a system page, try to open as an app using Intent
            openAppByIntent(appName)
            
        } catch (e: Exception) {
            Log.e("System", "Failed to open app: $appName", e)
        }
    }

    /**
     * Map user-friendly names to system Page enum values.
     */
    private fun mapToSystemPage(appName: String): Page? {
        return when (appName.lowercase().trim()) {
            "settings" -> Page.SETTINGS
            "map", "map editor" -> Page.MAP_EDITOR
            "contacts" -> Page.CONTACTS
            "locations" -> Page.LOCATIONS
            "apps", "all apps", "app list" -> Page.ALL_APPS
            "home" -> Page.HOME
            "tours" -> Page.TOURS
            else -> null
        }
    }

    /**
     * Open an app using Android Intent.
     * First tries to open by package name, then by action.
     */
    private fun openAppByIntent(appName: String) {
        try {
            val packageManager = context.packageManager
            
            // Try to treat as package name first
            var intent: Intent? = null
            
            if (appName.contains(".")) {
                // Looks like a package name
                intent = packageManager.getLaunchIntentForPackage(appName)
            }
            
            if (intent == null) {
                // Try common app mappings
                intent = when (appName.lowercase().trim()) {
                    "calculator" -> packageManager.getLaunchIntentForPackage("com.android.calculator2") 
                        ?: packageManager.getLaunchIntentForPackage("com.google.android.calculator")
                    "camera" -> Intent("android.media.action.IMAGE_CAPTURE")
                    "gallery", "photos" -> Intent(Intent.ACTION_VIEW).apply { 
                        type = "image/*" 
                    }
                    "browser", "chrome" -> packageManager.getLaunchIntentForPackage("com.android.chrome")
                        ?: packageManager.getLaunchIntentForPackage("com.android.browser")
                    "youtube" -> packageManager.getLaunchIntentForPackage("com.google.android.youtube")
                    else -> null
                }
            }
            
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                Log.d("System", "Successfully opened app: $appName")
            } else {
                Log.w("System", "Could not find app to open: $appName")
            }
            
        } catch (e: Exception) {
            Log.e("System", "Failed to open app by intent: $appName", e)
        }
    }

    override fun cleanup() {
        Log.d("System", "System skill cleanup")
        // No specific cleanup needed for system functions
    }
}
