package com.gongwen.paiban.data

import android.content.Context

/** 轻量应用偏好：当前激活模板等。 */
object AppPrefs {
    private const val PREFS = "gongwen_prefs"
    private const val KEY_ACTIVE_TEMPLATE = "active_template_id"

    fun activeTemplateId(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ACTIVE_TEMPLATE, com.gongwen.gongwen.Gbt9704Template.TEMPLATE_ID)!!

    fun setActiveTemplate(context: Context, templateId: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_ACTIVE_TEMPLATE, templateId).apply()
    }
}
