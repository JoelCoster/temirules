package com.robotemi.sdk.conversationframework

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.robotemi.sdk.Robot
import com.robotemi.sdk.SttLanguage
import com.robotemi.sdk.TtsRequest
import com.robotemi.sdk.listeners.OnGoToLocationStatusChangedListener
import com.robotemi.sdk.listeners.OnRobotReadyListener
import com.robotemi.sdk.voice.WakeupOrigin
import com.robotemi.sdk.permission.Permission
import com.robotemi.sdk.conversationframework.Skills.TiltHead
import kotlin.reflect.full.*
import android.util.Log
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import android.widget.Toast


sealed interface Expression

data class FunctionCall(val receiver: String, val method: String, val args: List<Expression>) : Expression

data class StringLiteral(val value: String) : Expression

data class EqualsExpr(val left: Expression, val right: Expression) : Expression

data class AndExpr(val left: Expression, val right: Expression) : Expression

data class OrExpr(val left: Expression, val right: Expression) : Expression

data class PatternMatch(val pattern: String, val text: Expression) : Expression

data class ExtractParam(val pattern: String, val text: Expression, val paramIndex: Int = 0) : Expression

data class Rule(val condition: Expression, val actions: List<Expression>)

private fun splitFunctionArgs(argsString: String): List<String> {
    if (argsString.isBlank()) return emptyList()
    
    val args = mutableListOf<String>()
    var current = StringBuilder()
    var depth = 0
    var inQuotes = false
    var i = 0
    
    while (i < argsString.length) {
        val char = argsString[i]
        when {
            char == '"' -> {
                inQuotes = !inQuotes
                current.append(char)
            }
            !inQuotes && char == '(' -> {
                depth++
                current.append(char)
            }
            !inQuotes && char == ')' -> {
                depth--
                current.append(char)
            }
            !inQuotes && char == ',' && depth == 0 -> {
                args.add(current.toString().trim())
                current.clear()
            }
            else -> {
                current.append(char)
            }
        }
        i++
    }
    
    // Add the last argument if there's one
    if (current.isNotEmpty()) {
        args.add(current.toString().trim())
    }
    
    return args
}

private fun splitActions(actionsString: String): List<String> {
    val actions = mutableListOf<String>()
    var current = StringBuilder()
    var depth = 0
    var inQuotes = false
    var i = 0
    
    while (i < actionsString.length) {
        val char = actionsString[i]
        when {
            char == '"' -> {
                inQuotes = !inQuotes
                current.append(char)
            }
            !inQuotes && char == '(' -> {
                depth++
                current.append(char)
            }
            !inQuotes && char == ')' -> {
                depth--
                current.append(char)
            }
            !inQuotes && char == ';' && depth == 0 -> {
                if (current.isNotEmpty()) {
                    actions.add(current.toString().trim())
                    current.clear()
                }
            }
            else -> {
                current.append(char)
            }
        }
        i++
    }
    
    // Add the last action if there's one
    if (current.isNotEmpty()) {
        actions.add(current.toString().trim())
    }
    
    return actions
}

private fun parseRules(ruleString: String): List<Rule> {
    // Remove outer brackets if present
    val cleanedRuleString = ruleString.trim().removePrefix("[").removeSuffix("]")

    val rules = mutableListOf<Rule>()
    val ruleRegex = Regex("\\[\\s*([^\\]]+?)\\s*-->\\s*([^\\]]+?)\\s*\\]")
    // Match receiver.method(args), allowing nested parentheses in args
    val functionRegex = Regex("([a-zA-Z0-9_]+)\\.([a-zA-Z0-9_]+)\\((.*)\\)")

    ruleRegex.findAll(cleanedRuleString).forEach { match ->
        try {
            Log.d("parseRules", "Parsing rule condition: ${match.groupValues[1].trim()}")
            val condition = parseExpression(match.groupValues[1].trim(), functionRegex)
            Log.d("parseRules", "Parsing rule actions: ${match.groupValues[2]}")
            val actionsString = match.groupValues[2]
            val actions = splitActions(actionsString).map { parseExpression(it.trim(), functionRegex) }
            rules.add(Rule(condition, actions))
            Log.d("parseRules", "Successfully parsed rule")
        } catch (e: IllegalArgumentException) {
            Log.e("parseRules", "Invalid rule: ${match.value}", e)
        } catch (e: Exception) {
            Log.e("parseRules", "Unexpected error parsing rule: ${match.value}", e)
        }
    }
    return rules
}

private fun containsLogicalOperator(expr: String, symbolOp: String, wordOp: String): Boolean {
    // Check if the expression contains the logical operator outside of quoted strings
    var inQuotes = false
    var i = 0
    while (i < expr.length) {
        when {
            expr[i] == '"' -> inQuotes = !inQuotes
            !inQuotes -> {
                // Check for symbol operator (e.g., "&&", "||")
                if (i + symbolOp.length <= expr.length && expr.substring(i, i + symbolOp.length) == symbolOp) {
                    return true
                }
                // Check for word operator (e.g., "AND", "OR") with word boundaries
                if (i + wordOp.length <= expr.length && expr.substring(i, i + wordOp.length).uppercase() == wordOp) {
                    val isWordBoundary = (i == 0 || !expr[i - 1].isLetterOrDigit()) &&
                                        (i + wordOp.length == expr.length || !expr[i + wordOp.length].isLetterOrDigit())
                    if (isWordBoundary) return true
                }
            }
        }
        i++
    }
    return false
}

private fun splitByLogicalOperator(expr: String, symbolOp: String, wordOp: String): Pair<String, String> {
    // Find the rightmost occurrence of the logical operator outside of quoted strings
    // This implements left-associativity
    var inQuotes = false
    var lastOperatorIndex = -1
    var i = 0
    while (i < expr.length) {
        when {
            expr[i] == '"' -> inQuotes = !inQuotes
            !inQuotes -> {
                // Check for symbol operator
                if (i + symbolOp.length <= expr.length && expr.substring(i, i + symbolOp.length) == symbolOp) {
                    lastOperatorIndex = i
                    i += symbolOp.length - 1 // Skip the rest of the operator
                }
                // Check for word operator with word boundaries
                else if (i + wordOp.length <= expr.length && expr.substring(i, i + wordOp.length).uppercase() == wordOp) {
                    val isWordBoundary = (i == 0 || !expr[i - 1].isLetterOrDigit()) &&
                                        (i + wordOp.length == expr.length || !expr[i + wordOp.length].isLetterOrDigit())
                    if (isWordBoundary) {
                        lastOperatorIndex = i
                        i += wordOp.length - 1 // Skip the rest of the operator
                    }
                }
            }
        }
        i++
    }
    
    if (lastOperatorIndex == -1) {
        throw IllegalArgumentException("Logical operator not found in expression: $expr")
    }
    
    // Determine the operator length
    val operatorLength = when {
        lastOperatorIndex + symbolOp.length <= expr.length && 
        expr.substring(lastOperatorIndex, lastOperatorIndex + symbolOp.length) == symbolOp -> symbolOp.length
        else -> wordOp.length
    }
    
    val left = expr.substring(0, lastOperatorIndex)
    val right = expr.substring(lastOperatorIndex + operatorLength)
    return Pair(left, right)
}

private fun parseExpression(expr: String, functionRegex: Regex): Expression {
    return when {
        expr.startsWith("\"") && expr.endsWith("\"") -> StringLiteral(expr.trim('"'))
        // Handle pattern matching functions
        expr.startsWith("PatternMatch(") && expr.endsWith(")") -> {
            val content = expr.substring(13, expr.length - 1) // Remove "PatternMatch(" and ")"
            val parts = splitFunctionArgs(content)
            if (parts.size != 2) throw IllegalArgumentException("PatternMatch requires exactly 2 arguments: pattern, text")
            val pattern = parseExpression(parts[0].trim(), functionRegex)
            val text = parseExpression(parts[1].trim(), functionRegex)
            PatternMatch((pattern as? StringLiteral)?.value ?: throw IllegalArgumentException("Pattern must be a string literal"), text)
        }
        expr.startsWith("ExtractParam(") && expr.endsWith(")") -> {
            val content = expr.substring(13, expr.length - 1) // Remove "ExtractParam(" and ")"
            val parts = splitFunctionArgs(content)
            if (parts.size < 2 || parts.size > 3) throw IllegalArgumentException("ExtractParam requires 2 or 3 arguments: pattern, text, [paramIndex]")
            val pattern = parseExpression(parts[0].trim(), functionRegex)
            val text = parseExpression(parts[1].trim(), functionRegex)
            val paramIndex = if (parts.size == 3) parts[2].trim().toIntOrNull() ?: 0 else 0
            ExtractParam((pattern as? StringLiteral)?.value ?: throw IllegalArgumentException("Pattern must be a string literal"), text, paramIndex)
        }
        // Handle logical operators (AND/OR) with precedence (AND has higher precedence than OR)
        containsLogicalOperator(expr, "||", "OR") -> {
            val (left, right) = splitByLogicalOperator(expr, "||", "OR")
            OrExpr(parseExpression(left.trim(), functionRegex), parseExpression(right.trim(), functionRegex))
        }
        containsLogicalOperator(expr, "&&", "AND") -> {
            val (left, right) = splitByLogicalOperator(expr, "&&", "AND")
            AndExpr(parseExpression(left.trim(), functionRegex), parseExpression(right.trim(), functionRegex))
        }
        "==" in expr -> {
            val parts = expr.split("==").map { it.trim() }
            EqualsExpr(parseExpression(parts[0], functionRegex), parseExpression(parts[1], functionRegex))
        }
        // Handle function calls with nested parentheses (e.g., TTS.speak(Memory.getStateParam("...") ) )
        expr.indexOf('.') > 0 && expr.contains('(') && expr.endsWith(')') -> {
            val dotIndex = expr.indexOf('.')
            val receiver = expr.substring(0, dotIndex)
            val parenIndex = expr.indexOf('(', startIndex = dotIndex + 1)
            if (parenIndex < 0) throw IllegalArgumentException("Invalid function call: $expr")
            val method = expr.substring(dotIndex + 1, parenIndex)
            // Find matching closing parenthesis
            var depth = 1
            var endIndex = parenIndex
            while (endIndex + 1 < expr.length && depth > 0) {
                endIndex++
                when (expr[endIndex]) {
                    '(' -> depth++
                    ')' -> depth--
                }
            }
            if (depth != 0 || endIndex != expr.length - 1) throw IllegalArgumentException("Invalid function call: $expr")
            val argsString = expr.substring(parenIndex + 1, endIndex)
            val args = if (argsString.isBlank()) emptyList() else {
                splitFunctionArgs(argsString).map { parseExpression(it.trim(), functionRegex) }
            }
            FunctionCall(receiver, method, args)
        }
        else -> throw IllegalArgumentException("Unsupported expression: $expr")
    }
}

private fun evaluateExpression(expr: Expression, context: InteractionManager): Any? {
    return when (expr) {
        is StringLiteral -> expr.value
        is EqualsExpr -> evaluateExpression(expr.left, context) == evaluateExpression(expr.right, context)
        is AndExpr -> {
            val leftResult = evaluateExpression(expr.left, context) as? Boolean ?: false
            val rightResult = evaluateExpression(expr.right, context) as? Boolean ?: false
            leftResult && rightResult
        }
        is OrExpr -> {
            val leftResult = evaluateExpression(expr.left, context) as? Boolean ?: false
            val rightResult = evaluateExpression(expr.right, context) as? Boolean ?: false
            leftResult || rightResult
        }
        is PatternMatch -> {
            val pattern = expr.pattern
            val text = evaluateExpression(expr.text, context) as? String ?: ""
            val result = matchesPattern(pattern, text)
            Log.d("PatternMatch", "Pattern: '$pattern', Text: '$text', Result: $result")
            result
        }
        is ExtractParam -> {
            val pattern = expr.pattern
            val text = evaluateExpression(expr.text, context) as? String ?: ""
            val result = extractParameter(pattern, text, expr.paramIndex)
            Log.d("ExtractParam", "Pattern: '$pattern', Text: '$text', Result: '$result'")
            result
        }
        is FunctionCall -> {
            // Resolve the receiver dynamically using the context
            val receiverInstance = when (expr.receiver) {
                "Memory" -> context.memory
                "TTS", "TiltHead", "ASR", "Move", "Locations" -> context.skillManager
                else -> throw IllegalArgumentException("Unknown receiver: ${expr.receiver}")
            }
            // Invoke skill calls via skillManager, others via reflection
            return if (expr.receiver == "TTS" || expr.receiver == "TiltHead" || expr.receiver == "ASR" || expr.receiver == "Move" || expr.receiver == "Locations") {
                val args = expr.args.map { evaluateExpression(it, context) }.toTypedArray()
                context.skillManager.call(expr.receiver, expr.method, *args)
            } else {
                val method = receiverInstance::class.functions.firstOrNull { it.name == expr.method }
                    ?: throw IllegalArgumentException("Method '${expr.method}' not found in receiver '${expr.receiver}'")
                val args = expr.args.map { evaluateExpression(it, context) }.toTypedArray()
                method.call(receiverInstance, *args)
            }
        }
    }
}

/**
 * Check if text matches a pattern using wildcards
 * Pattern syntax: * = any number of characters, {param} = named parameter placeholder
 * Examples:
 * - "go to *" matches "go to kitchen", "go to the living room"
 * - "go to {location}" matches "go to kitchen" and extracts "kitchen" as location
 */
private fun matchesPattern(pattern: String, text: String): Boolean {
    val normalizedText = text.lowercase().trim()
    val normalizedPattern = pattern.lowercase().trim()
    
    // Convert pattern to regex
    // Replace {param} with (.+) and * with (.*)
    // Escape special regex characters first
    val regexPattern = normalizedPattern
        .replace("\\", "\\\\")
        .replace(".", "\\.")
        .replace("^", "\\^")
        .replace("$", "\\$")
        .replace("+", "\\+")
        .replace("?", "\\?")
        .replace("[", "\\[")
        .replace("]", "\\]")
        .replace("(", "\\(")
        .replace(")", "\\)")
        .replace("|", "\\|")
        .replace(Regex("\\{[^}]+\\}"), "(.+)")  // {param} becomes capturing group
        .replace("*", "(.*)")  // * becomes wildcard capturing group
    
    val regex = Regex("^$regexPattern$")
    Log.d("PatternMatch", "Pattern: '$pattern' -> Regex: '^$regexPattern$' for text: '$normalizedText'")
    val result = regex.matches(normalizedText)
    Log.d("PatternMatch", "Match result: $result")
    return result
}

/**
 * Extract parameter from text using pattern
 * Pattern syntax: {param} = named parameter placeholder, * = wildcard
 * paramIndex: which parameter to extract (0-based, useful when multiple parameters)
 */
private fun extractParameter(pattern: String, text: String, paramIndex: Int = 0): String? {
    val normalizedText = text.lowercase().trim()
    val normalizedPattern = pattern.lowercase().trim()
    
    // Convert pattern to regex with capturing groups
    // Escape special regex characters first
    val regexPattern = normalizedPattern
        .replace("\\", "\\\\")
        .replace(".", "\\.")
        .replace("^", "\\^")
        .replace("$", "\\$")
        .replace("+", "\\+")
        .replace("?", "\\?")
        .replace("[", "\\[")
        .replace("]", "\\]")
        .replace("(", "\\(")
        .replace(")", "\\)")
        .replace("|", "\\|")
        .replace(Regex("\\{[^}]+\\}"), "(.+)")  // {param} becomes capturing group
        .replace("*", "(.*)")  // * becomes wildcard capturing group
    
    val regex = Regex("^$regexPattern$")
    val matchResult = regex.find(normalizedText)
    
    Log.d("ExtractParam", "Pattern: '$pattern' -> Regex: '^$regexPattern$' for text: '$normalizedText'")
    Log.d("ExtractParam", "Match groups: ${matchResult?.groupValues}")
    
    return if (matchResult != null && paramIndex < matchResult.groupValues.size - 1) {
        // Find the original case version of the extracted parameter
        val extractedLowercase = matchResult.groupValues[paramIndex + 1].trim()
        // Try to find the original case in the original text
        val originalText = text.trim()
        val startIndex = originalText.lowercase().indexOf(extractedLowercase)
        if (startIndex >= 0) {
            originalText.substring(startIndex, startIndex + extractedLowercase.length)
        } else {
            extractedLowercase // fallback to lowercase version
        }
    } else {
        null
    }
}


class InteractionManager : AppCompatActivity(), OnRobotReadyListener, Robot.AsrListener, Robot.TtsListener, 
    Robot.WakeupWordListener, OnGoToLocationStatusChangedListener {
    private lateinit var robot: Robot
    private lateinit var uiHelper: UIHelper
    internal lateinit var memory: Memory
    internal lateinit var skillManager: SkillManager
    private val REQUEST_CODE_ALL_PERMISSIONS = 1001
    private lateinit var rules: String
    @Volatile
    private var rulesReloaded: Boolean = false
    private val rulesLock = Any() // Lock object for synchronization

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_interaction_manager)
        rules = loadRulesFromAssets()
        robot = Robot.getInstance()
        memory = Memory()
        uiHelper = UIHelper(this, robot)
        uiHelper.setupUi()
        skillManager = SkillManager(applicationContext, robot, uiHelper)
        skillManager.loadSkills()
        skillManager.loadSkill("TiltHead")
        skillManager.loadSkill("ASR")
        skillManager.loadSkill("Move")
        skillManager.loadSkill("Locations")
    }
    

    override fun onStart() {
        super.onStart()
        robot.addOnRobotReadyListener(this)
        robot.addAsrListener(this)
        robot.addWakeupWordListener(this)
        robot.addOnGoToLocationStatusChangedListener(this)
    }


    override fun onStop() {
        super.onStop()
        robot.removeOnRobotReadyListener(this)
        robot.removeAsrListener(this)
        robot.removeWakeupWordListener(this)
        robot.removeOnGoToLocationStatusChangedListener(this)
    }
    

    private fun requestAllPermissionsIfNeeded() {
        val permissionsToRequest = Permission.values()
            .filter { robot.checkSelfPermission(it) != Permission.GRANTED }
            .map { it.value }
            .toMutableList()

        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(android.Manifest.permission.RECORD_AUDIO)
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), REQUEST_CODE_ALL_PERMISSIONS)
        }
    }

    
    override fun onRobotReady(isReady: Boolean) {        
        if (isReady) {
            uiHelper.updateStatusDisplay("Robot is ready")
            try {
                if (!robot.isKioskModeOn()) {
                    robot.setKioskModeOn(true)
                    robot.requestToBeKioskApp()
                }
            } catch (e: Exception) {
                uiHelper.updateStatusDisplay("Robot is ready, but could not enable kiosk mode, app might not function properly")
            }
              
            if (robot.wakeupWordDisabled && robot.isKioskModeOn()) {
                robot.toggleWakeup(false)
            }
            
            requestAllPermissionsIfNeeded()
            interactionLoop()
        } else {
            uiHelper.updateStatusDisplay("Robot is not ready")
        }
    }
    

    /** Called automatically by robot when wakeup word is detected */
    override fun onWakeupWord(wakeupWord: String, direction: Int, origin: WakeupOrigin) {
        if (uiHelper.isListening()) { // not sure if necessary, but might trigger wakeup word during conversation, TODO: check
            return
        }
        memory.setStateParam("interactionState", "Active")
    }
    

    /** Called automatically by robot when ASR result is received */
    override fun onAsrResult(asrResult: String, sttLanguage: SttLanguage) {
        Log.d("InteractionManager", "ASR result received: '$asrResult' (language: $sttLanguage)")
        
        // Notify ASR skill that listening has stopped
        skillManager.getSkill("ASR")?.let { asrSkill ->
            try {
                val setListeningStateMethod = asrSkill::class.java.getMethod("setListeningState", Boolean::class.java)
                setListeningStateMethod.invoke(asrSkill, false)
            } catch (e: Exception) {
                Log.e("InteractionManager", "Failed to update ASR skill listening state", e)
            }
        }
        
        if (asrResult.isNotEmpty()) {
            uiHelper.addUserMessage(asrResult)
            // Store the ASR result in memory for rule processing
            memory.setStateParam("lastAsrResult", asrResult)
            memory.setStateParam("asrLanguage", sttLanguage.toString())
            // Set a state that can be used by rules to detect new ASR input
            memory.setStateParam("interactionState", "asrReceived")
        }
        
        // Stop listening after ASR result
        robot.finishConversation()
    }


    /** Called automatically by robot when TTS status changes, e.g., after using robot.speak */
    override fun onTtsStatusChanged(ttsRequest: TtsRequest) {}


    /** Called automatically by robot when Location status changes, e.g., after moving somewhere */
    override fun onGoToLocationStatusChanged(location: String, status: String, descriptionId: Int, description: String) {}


    /** Main interaction loop for robot and user. */
    private fun interactionLoop() {
        memory.setStateParam("interactionState", "Active") // Hardcoded initialization for now
        val interactionManager = this // Capture reference to avoid confusion with Thread's this
        Thread {
            var rules = parseRules(interactionManager.rules) // Parse rules initially
            Log.d("InteractionLoop", "Parsed rules count: ${rules.size}")
            
            while (!Thread.currentThread().isInterrupted) {
                try {
                    synchronized(rulesLock) {
                        if (rulesReloaded) {
                            rules = parseRules(interactionManager.rules) // Re-parse rules if reloaded
                            rulesReloaded = false // Reset the flag
                            Log.d("InteractionLoop", "Rules reloaded and re-parsed")
                        }
                    }

                    Log.d("InteractionLoop", "Evaluating rules...")
                    for (rule in rules) {
                        //Log.d("InteractionLoop", "Evaluating condition: ${rule.condition}")
                        if (evaluateExpression(rule.condition, interactionManager) as? Boolean == true) {
                            //Log.d("InteractionLoop", "Condition met. Executing actions...")
                            // Check if any action is a TTS speak call - if so, handle differently
                            val hasTtsAction = rule.actions.any { action ->
                                action is FunctionCall && action.receiver == "TTS" && action.method == "speak"
                            }
                            
                            if (hasTtsAction) {
                                // For TTS actions, execute on current background thread
                                rule.actions.forEach {
                                    //Log.d("InteractionLoop", "Executing action: $it")
                                    evaluateExpression(it, interactionManager)
                                }
                            } else {
                                // For non-TTS actions, use UI thread as before
                                interactionManager.runOnUiThread {
                                    rule.actions.forEach {
                                        //Log.d("InteractionLoop", "Executing action: $it")
                                        evaluateExpression(it, interactionManager)
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    //Log.e("InteractionLoop", "Error in interaction loop", e)
                }
                Thread.sleep(200) // Sleep briefly between iterations (200ms for responsive interaction)
            }
        }.start()
    }

    /* Remove listeners when closing apps */
    override fun onDestroy() {
        super.onDestroy()
        skillManager.cleanup()
    }

    /**
     * Load rules from assets directory
     */
    private fun loadRulesFromAssets(): String {
        return try {
            assets.open("rules.txt").bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            Log.e("InteractionManager", "Failed to load rules from assets", e)
            // Fallback to default rules
            """[
        [Memory.getStateParam("interactionState") == "active" -->
        TTS.speak("this is a test"); Memory.setStateParam("interactionState", "idle")]
        [Memory.getStateParam("interactionState") == "idle" -->
        TTS.speak("Interaction is idle"); Memory.setStateParam("interactionState", "active")]
    ]"""
        }
    }

    /**
     * Reload rules from web location
     */
    fun reloadRules() {
        Thread {
            try {
                val url = URL("https://raw.githubusercontent.com/JoelCoster/temirules/refs/heads/main/rules.txt")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 10000

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val newRules = connection.inputStream.bufferedReader().use { it.readText() }
                    synchronized(rulesLock) {
                        rules = newRules
                        rulesReloaded = true // Set the flag to true after reloading rules
                    }
                    runOnUiThread {
                        Toast.makeText(this, "Rules reloaded successfully", Toast.LENGTH_SHORT).show()
                        uiHelper.updateStatusDisplay("Rules reloaded from web")
                    }
                    Log.d("InteractionManager", "Rules reloaded successfully")
                } else {
                    runOnUiThread {
                        Toast.makeText(this, "Failed to reload rules: HTTP $responseCode", Toast.LENGTH_SHORT).show()
                    }
                    Log.e("InteractionManager", "Failed to reload rules: HTTP $responseCode")
                }
                connection.disconnect()
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Failed to reload rules: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                Log.e("InteractionManager", "Failed to reload rules", e)
            }
        }.start()
    }
}