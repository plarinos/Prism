package io.prism.data.local

import android.content.Context
import io.prism.data.model.CustomTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class CustomTemplateStorage(private val context: Context) {

    private val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    suspend fun saveCustomTemplate(
        name: String,
        description: String,
        templateJson: String
    ): CustomTemplate = withContext(Dispatchers.IO) {
        val template = CustomTemplate(
            id = UUID.randomUUID().toString(),
            name = name,
            description = description,
            templateJson = templateJson
        )

        val templates = getAllCustomTemplatesSync().toMutableList()
        templates.add(template)
        saveAllTemplates(templates)

        template
    }

    suspend fun getAllCustomTemplates(): List<CustomTemplate> = withContext(Dispatchers.IO) {
        getAllCustomTemplatesSync()
    }

    suspend fun deleteCustomTemplate(id: String) = withContext(Dispatchers.IO) {
        val templates = getAllCustomTemplatesSync().toMutableList()
        templates.removeAll { it.id == id }
        saveAllTemplates(templates)
    }

    suspend fun updateCustomTemplate(template: CustomTemplate) = withContext(Dispatchers.IO) {
        val templates = getAllCustomTemplatesSync().toMutableList()
        val index = templates.indexOfFirst { it.id == template.id }

        if (index >= 0) {
            templates[index] = template
            saveAllTemplates(templates)
        }
    }

    private fun getAllCustomTemplatesSync(): List<CustomTemplate> {
        val json = sharedPrefs.getString(KEY_TEMPLATES, "[]") ?: "[]"
        val jsonArray = JSONArray(json)

        val templates = mutableListOf<CustomTemplate>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            templates.add(
                CustomTemplate(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    description = obj.optString("description", ""),
                    templateJson = obj.getString("templateJson"),
                    createdAt = obj.optLong("createdAt", 0)
                )
            )
        }
        return templates
    }

    private fun saveAllTemplates(templates: List<CustomTemplate>) {
        val jsonArray = JSONArray()
        templates.forEach { template ->
            val obj = JSONObject().apply {
                put("id", template.id)
                put("name", template.name)
                put("description", template.description)
                put("templateJson", template.templateJson)
                put("createdAt", template.createdAt)
            }
            jsonArray.put(obj)
        }

        sharedPrefs.edit()
            .putString(KEY_TEMPLATES, jsonArray.toString())
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "custom_templates"
        private const val KEY_TEMPLATES = "templates"

        val EXAMPLE_TEMPLATE_JSON = """
            {
                "layout": "horizontal",
                "padding": 24,
                "ignoreTheme": false,
                "elements": [
                    {
                        "type": "logo",
                        "size": 48,
                        "marginEnd": 16
                    },
                    {
                        "type": "divider",
                        "style": "vertical",
                        "width": 2,
                        "height": 40,
                        "marginEnd": 16
                    },
                    {
                        "type": "text_group",
                        "children": [
                            {
                                "type": "main_text",
                                "size": 18,
                                "bold": true
                            },
                            {
                                "type": "device_name",
                                "size": 14,
                                "marginTop": 4
                            },
                            {
                                "type": "exif_text",
                                "size": 12,
                                "marginTop": 4,
                                "alpha": 0.7
                            }
                        ]
                    }
                ]
            }
        """.trimIndent()
    }
}