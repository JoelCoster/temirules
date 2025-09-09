package com.robotemi.sdk.conversationframework.Skills

import android.content.Context
import android.util.Log
import com.robotemi.sdk.Robot
import com.robotemi.sdk.conversationframework.UIHelper
import com.robotemi.sdk.conversationframework.Memory
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

/**
 * A skill for interacting with HuggingFace LLM API.
 * This skill provides functionality to send prompts to an LLM and receive responses.
 */
class HuggingFace(
    context: Context,
    robot: Robot,
    uiHelper: UIHelper
) : Skill(context, robot, uiHelper) {

    override val skillName: String = "HuggingFace"
    
    private val apiUrl = "https://router.huggingface.co/v1/chat/completions"
    private val model = "openai/gpt-oss-20b:together"

    override fun initialize() {
        Log.d("HuggingFace", "HuggingFace skill initialized")
    }

    /**
     * Send a prompt to the HuggingFace LLM and return the response.
     * Gets conversation history from Memory automatically.
     * 
     * @param prompt The prompt to send to the LLM
     * @return The LLM's response as a string
     */
    fun ask(prompt: String): String {
        Log.d("HuggingFace", "ask() called with prompt: $prompt")

        if (prompt.isBlank()) {
            Log.e("HuggingFace", "Prompt cannot be empty")
            return "Sorry, I received an empty prompt."
        }

        return try {
            val response = sendRequestWithMemory(prompt)
            Log.d("HuggingFace", "Received response: $response")
            response
        } catch (e: Exception) {
            Log.e("HuggingFace", "Failed to get response from HuggingFace API", e)
            "Sorry, I encountered an error while processing your request."
        }
    }

    /**
     * Send a prompt to the HuggingFace LLM and return the response.
     * 
     * @param prompt The prompt to send to the LLM
     * @param memory The Memory instance to get conversation history from
     * @return The LLM's response as a string
     */
    fun ask(prompt: String, memory: Memory): String {
        Log.d("HuggingFace", "ask() called with prompt: $prompt")

        if (prompt.isBlank()) {
            Log.e("HuggingFace", "Prompt cannot be empty")
            return "Sorry, I received an empty prompt."
        }

        return try {
            val response = sendRequest(prompt, memory)
            Log.d("HuggingFace", "Received response: $response")
            response
        } catch (e: Exception) {
            Log.e("HuggingFace", "Failed to get response from HuggingFace API", e)
            "Sorry, I encountered an error while processing your request."
        }
    }

    private fun sendRequestWithMemory(prompt: String): String {
        // Try to get Memory from the global context
        val memory = getMemoryInstance()
        Log.d("HuggingFace", "Memory instance: $memory")
        return if (memory != null) {
            sendRequest(prompt, memory)
        } else {
            Log.w("HuggingFace", "Memory instance not available, sending request without conversation history")
            sendRequestWithoutHistory(prompt)
        }
    }

    /**
     * Get Memory instance from UIHelper
     */
    private fun getMemoryInstance(): Memory? {
        return try {
            val memory = uiHelper.getMemory()
            Log.d("HuggingFace", "Retrieved memory instance: $memory")
            memory
        } catch (e: Exception) {
            Log.e("HuggingFace", "Failed to get memory instance: ${e.message}")
            null
        }
    }

    private fun sendRequest(prompt: String, memory: Memory): String {
        val url = URL(apiUrl)
        val connection = url.openConnection() as HttpURLConnection
        
        try {
            // Get API token from resources
            val apiToken = context.getString(context.resources.getIdentifier("huggingface_api_token", "string", context.packageName))
            
            // Setup connection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $apiToken")
            connection.doOutput = true
            connection.connectTimeout = 30000
            connection.readTimeout = 30000

            // Get conversation history from Memory
            val conversationHistory = getConversationHistoryFromMemory(memory)
            
            // Create JSON payload with conversation history
            val jsonPayload = JSONObject().apply {
                put("model", model)
                put("stream", false)
                put("messages", JSONArray().apply {
                    // Add system message to set context
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", "You are Temi, a helpful robot assistant owned by the Information Science department of the University of Groningen. The user's current question is the most recent message. Previous messages provide context from our conversation history. Keep your answers short, don't use formatting or try to list things, keep a natural flow of conversation")
                    })
                    
                    // Add conversation history
                    conversationHistory.forEach { (role, content) ->
                        put(JSONObject().apply {
                            put("role", role)
                            put("content", content)
                        })
                    }
                    
                    // Add current prompt as the user's question
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    })
                })
            }

            Log.d("HuggingFace", "Sending JSON payload: ${jsonPayload.toString()}")

            // Send request
            val writer = OutputStreamWriter(connection.outputStream)
            writer.write(jsonPayload.toString())
            writer.flush()
            writer.close()

            // Check response code
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.e("HuggingFace", "HTTP error: $responseCode")
                return "Sorry, the AI service is currently unavailable."
            }

            // Read response
            val responseText = connection.inputStream.bufferedReader().use { it.readText() }
            Log.d("HuggingFace", "Raw API response: $responseText")
            
            // Parse JSON response
            val jsonResponse = JSONObject(responseText)
            val choices = jsonResponse.getJSONArray("choices")
            if (choices.length() > 0) {
                val firstChoice = choices.getJSONObject(0)
                val message = firstChoice.getJSONObject("message")
                return message.getString("content").trim()
            } else {
                Log.e("HuggingFace", "No choices in API response")
                return "Sorry, I didn't receive a proper response from the AI service."
            }
            
        } finally {
            connection.disconnect()
        }
    }

    private fun sendRequestWithoutHistory(prompt: String): String {
        val url = URL(apiUrl)
        val connection = url.openConnection() as HttpURLConnection
        
        try {
            // Get API token from resources
            val apiToken = context.getString(context.resources.getIdentifier("huggingface_api_token", "string", context.packageName))
            
            // Setup connection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $apiToken")
            connection.doOutput = true
            connection.connectTimeout = 30000
            connection.readTimeout = 30000

            // Create JSON payload without conversation history
            val jsonPayload = JSONObject().apply {
                put("model", model)
                put("stream", false)
                put("messages", JSONArray().apply {
                    // Add system message
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", "You are Temi, a helpful robot assistant. Keep your answers short and don't use formatting.")
                    })
                    
                    // Add current prompt
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    })
                })
            }

            // Send request
            val writer = OutputStreamWriter(connection.outputStream)
            writer.write(jsonPayload.toString())
            writer.flush()
            writer.close()

            // Check response code
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.e("HuggingFace", "HTTP error: $responseCode")
                return "Sorry, the AI service is currently unavailable."
            }

            // Read response
            val responseText = connection.inputStream.bufferedReader().use { it.readText() }
            Log.d("HuggingFace", "Raw API response: $responseText")
            
            // Parse JSON response
            val jsonResponse = JSONObject(responseText)
            val choices = jsonResponse.getJSONArray("choices")
            if (choices.length() > 0) {
                val firstChoice = choices.getJSONObject(0)
                val message = firstChoice.getJSONObject("message")
                return message.getString("content").trim()
            } else {
                Log.e("HuggingFace", "No choices in API response")
                return "Sorry, I didn't receive a proper response from the AI service."
            }
            
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Get conversation history from Memory system using the conversation history state parameter
     */
    private fun getConversationHistoryFromMemory(memory: Memory): List<Pair<String, String>> {
        return try {
            // Get the conversation history from the state parameter
            val conversationHistory = memory.getStateParam("conversationHistory") as? List<Pair<String, String>>
            
            if (conversationHistory != null) {
                Log.d("HuggingFace", "Retrieved conversation history: ${conversationHistory.size} messages")
                
                conversationHistory.forEach { (role, content) ->
                    Log.d("HuggingFace", "Message: $role - $content")
                }
                
                Log.d("HuggingFace", "Returning ${conversationHistory.size} messages for API call")
                conversationHistory
            } else {
                Log.d("HuggingFace", "No conversation history found in memory")
                emptyList()
            }
            
        } catch (e: Exception) {
            Log.w("HuggingFace", "Failed to get conversation history from memory: ${e.message}")
            emptyList()
        }
    }

    override fun cleanup() {
        Log.d("HuggingFace", "HuggingFace skill cleanup")
        // No specific cleanup needed for HTTP client
    }
}
