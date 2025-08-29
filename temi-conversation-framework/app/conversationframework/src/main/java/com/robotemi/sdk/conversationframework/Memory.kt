package com.robotemi.sdk.conversationframework

import java.util.*

/**
 * Memory class to keep track of current and historic state of the robot.
 * State parameters can be added dynamically and their history is maintained with timestamps.
 */
class Memory {
    // Data class to hold state parameter value with timestamp
    data class StateEntry(
        val value: Any,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    private val currentState = mutableMapOf<String, Any>()
    private val stateHistory = mutableMapOf<String, MutableList<StateEntry>>()
    
    /**
     * Updates the current value of a state parameter.
     * New parameter is added if non-existent.
     * Timestamp of state change is registered.
     */
    fun setStateParam(paramName: String, value: Any) {
        currentState[paramName] = value
        
        if (!stateHistory.containsKey(paramName)) {
            stateHistory[paramName] = mutableListOf()
        }
        stateHistory[paramName]?.add(StateEntry(value))
    }
    
    /**
     * Returns the current state of a given state parameter.
     */
    fun getStateParam(paramName: String): Any? {
        return currentState[paramName]
    }
    
    /**
     * Returns a log of historic states of a parameter, including timestamps.
     * Optionally accepts a start and end time to return state changes in that window.
     */
    fun getStateParamHistory(paramName: String, startTime: Long? = null, endTime: Long? = null): List<StateEntry> {
        val history = stateHistory[paramName] ?: return emptyList()
        
        return if (startTime != null || endTime != null) {
            history.filter { entry ->
                val afterStart = startTime?.let { entry.timestamp >= it } ?: true
                val beforeEnd = endTime?.let { entry.timestamp <= it } ?: true
                afterStart && beforeEnd
            }
        } else {
            history.toList()
        }
    }
    
    /**
     * Returns the entire current state of the robot, including all parameters.
     */
    fun getState(): Map<String, Any> {
        return currentState.toMap()
    }
    
    /**
     * Returns a log of historic states of all parameters.
     * Optionally accepts a start and end time to return state changes in that window.
     */
    fun getStateHistory(startTime: Long? = null, endTime: Long? = null): Map<String, List<StateEntry>> {
        return stateHistory.mapValues { (_, history) ->
            if (startTime != null || endTime != null) {
                history.filter { entry ->
                    val afterStart = startTime?.let { entry.timestamp >= it } ?: true
                    val beforeEnd = endTime?.let { entry.timestamp <= it } ?: true
                    afterStart && beforeEnd
                }
            } else {
                history.toList()
            }
        }
    }
    
    /**
     * Clears all state history while maintaining current state.
     */
    fun clearHistory() {
        stateHistory.clear()
    }
    
    /**
     * Resets both current state and history.
     */
    fun reset() {
        currentState.clear()
        stateHistory.clear()
    }
    
    /**
     * Returns the previous value of a state parameter.
     * Returns null if there is no previous value or if the parameter doesn't exist.
     */
    fun getPreviousStateParam(paramName: String): Any? {
        val history = stateHistory[paramName] ?: return null
        return if (history.size >= 2) {
            history[history.size - 2].value // Second to last entry
        } else {
            null
        }
    }
}
