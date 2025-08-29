package com.robotemi.sdk.conversationframework

import android.content.Context
import android.util.Log
import com.robotemi.sdk.Robot
import com.robotemi.sdk.conversationframework.Skills.Skill
import dalvik.system.DexFile
import java.io.IOException

class SkillManager(
    private val context: Context,
    private val robot: Robot,
    private val uiHelper: UIHelper
) {
    val skills = mutableMapOf<String, Skill>()

    fun loadSkills() {
        try {
            val dexFile = DexFile(context.packageCodePath)
            val entries = dexFile.entries()
            val skillPackageName = "com.robotemi.sdk.conversationframework.Skills"
            while (entries.hasMoreElements()) {
                val className = entries.nextElement()
                if (className.startsWith(skillPackageName)) {
                    Log.d("SkillManager", "Discovered class: $className")
                    try {
                        val loadedClass = Class.forName(className)
                        if (Skill::class.java.isAssignableFrom(loadedClass) && !loadedClass.isInterface && !java.lang.reflect.Modifier.isAbstract(loadedClass.modifiers)) {
                            val constructor = loadedClass.getConstructor(Robot::class.java, UIHelper::class.java)
                            val skillInstance = constructor.newInstance(robot, uiHelper) as Skill
                            skills[skillInstance.skillName] = skillInstance
                            skillInstance.initialize()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun getSkill(name: String): Skill? {
        return skills[name]
    }

    fun call(skillName: String, methodName: String, vararg args: Any?): Any? {
        val skill = skills[skillName]
        if (skill != null) {
            val method = skill::class.java.methods.find { it.name == methodName }
            if (method != null) {
                return method.invoke(skill, *args)
            }
        }
        return null
    }

    fun cleanup() {
        skills.values.forEach { it.cleanup() }
    }

    fun loadSkill(skillName: String) {
        val skillPackageName = "com.robotemi.sdk.conversationframework.Skills"
        val className = "$skillPackageName.$skillName"
        try {
            val loadedClass = Class.forName(className)
            if (Skill::class.java.isAssignableFrom(loadedClass) && !loadedClass.isInterface && !java.lang.reflect.Modifier.isAbstract(loadedClass.modifiers)) {
                val constructor = loadedClass.getConstructor(Robot::class.java, UIHelper::class.java)
                val skillInstance = constructor.newInstance(robot, uiHelper) as Skill
                skills[skillInstance.skillName] = skillInstance
                skillInstance.initialize()
            } else {
                throw IllegalArgumentException("Class $className is not a valid Skill implementation.")
            }
        } catch (e: Exception) {
            Log.e("SkillManager", "Failed to load skill: $skillName", e)
        }
    }
}
