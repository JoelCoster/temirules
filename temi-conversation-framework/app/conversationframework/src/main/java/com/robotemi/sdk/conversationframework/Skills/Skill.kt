package com.robotemi.sdk.conversationframework.Skills

import com.robotemi.sdk.Robot
import com.robotemi.sdk.conversationframework.UIHelper

/**
 * Base interface for all skills in the conversation framework
 */
abstract class Skill(
    protected val robot: Robot,
    protected val uiHelper: UIHelper
) {
    /**
     * The name of this skill - used for identification and dynamic loading
     */
    abstract val skillName: String
    
    /**
     * Initialize the skill - called when the skill is first loaded
     */
    open fun initialize() {
        // Default implementation does nothing
    }
    
    /**
     * Cleanup resources when the skill is being unloaded
     */
    open fun cleanup() {
        // Default implementation does nothing
    }
}
