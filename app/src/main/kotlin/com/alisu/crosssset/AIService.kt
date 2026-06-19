package com.alisu.crosssset

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object AIService {
    private const val TAG = "AIService"
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    enum class AIProvider {
        OPENAI, DEEPSEEK, GEMINI, NONE
    }

    fun getSelectedProvider(context: Context): AIProvider {
        val name = context.getSharedPreferences("ai_prefs", Context.MODE_PRIVATE)
            .getString("provider", AIProvider.NONE.name)
        return AIProvider.valueOf(name ?: AIProvider.NONE.name)
    }

    fun getApiKey(context: Context): String? {
        return context.getSharedPreferences("ai_prefs", Context.MODE_PRIVATE)
            .getString("api_key", null)
    }

    suspend fun fetchDescription(context: Context, table: SettingsTable, key: String): String? = withContext(Dispatchers.IO) {
        val provider = getSelectedProvider(context)
        val apiKey = getApiKey(context)
        
        if (provider == AIProvider.NONE || apiKey.isNullOrBlank()) return@withContext null

        try {
            val language = java.util.Locale.getDefault().displayLanguage
//            val prompt = "Explique de forma técnica e curta (máximo 2 linhas) o que a configuração do Android '$key' da tabela '${table.name}' faz. Responda apenas a explicação no idioma: $language."
            val prompt = "Explain in a brief technical way (maximum 3 lines) what the Android setting '$key' in the '${table.name}' table does, including known settings options. Respond only with the explanation in the language: $language."
            
            return@withContext when (provider) {
                AIProvider.OPENAI -> callOpenAI(apiKey, prompt)
                AIProvider.DEEPSEEK -> callDeepSeek(apiKey, prompt)
                AIProvider.GEMINI -> callGemini(apiKey, prompt)
                else -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao chamar API de IA", e)
            null
        }
    }

    suspend fun validateKey(provider: AIProvider, apiKey: String): Boolean = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext false
        try {
            val testPrompt = "Respond with 'OK'"
            // For validation, we can just check if the call doesn't return null
            val result = when (provider) {
                AIProvider.OPENAI -> callOpenAI(apiKey, testPrompt)
                AIProvider.DEEPSEEK -> callDeepSeek(apiKey, testPrompt)
                AIProvider.GEMINI -> callGemini(apiKey, testPrompt)
                else -> null
            }
            !result.isNullOrBlank()
        } catch (e: Exception) {
            Log.e(TAG, "Key validation failed: ${e.message}")
            false
        }
    }

    private fun callOpenAI(apiKey: String, prompt: String): String? {
        val mediaType = "application/json".toMediaType()
        val json = JSONObject().apply {
            put("model", "gpt-5.4-mini") // Latest efficient model from OpenAI
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
            put("max_tokens", 100)
        }
        
        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .post(json.toString().toRequestBody(mediaType))
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("User-Agent", "CrossSset/1.0")
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string()
            if (!response.isSuccessful) {
                Log.e(TAG, "OpenAI Error: ${response.code} - $responseBody")
                return null
            }
            if (responseBody == null) return null
            return try {
                val respJson = JSONObject(responseBody)
                respJson.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim()
            } catch (e: Exception) {
                if (response.isSuccessful) "OK" else null
            }
        }
    }

    private fun callDeepSeek(apiKey: String, prompt: String): String? {
        val mediaType = "application/json".toMediaType()
        val json = JSONObject().apply {
            put("model", "deepseek-v4") // Current flagship open-weight model
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
            put("max_tokens", 100)
        }
        
        val request = Request.Builder()
            .url("https://api.deepseek.com/chat/completions")
            .post(json.toString().toRequestBody(mediaType))
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("User-Agent", "CrossSset/1.0")
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string()
            if (!response.isSuccessful) {
                Log.e(TAG, "DeepSeek Error: ${response.code} - $responseBody")
                return null
            }
            if (responseBody == null) return null
            return try {
                val respJson = JSONObject(responseBody)
                respJson.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim()
            } catch (e: Exception) {
                if (response.isSuccessful) "OK" else null
            }
        }
    }

    private fun callGemini(apiKey: String, prompt: String): String? {
        val mediaType = "application/json".toMediaType()
        val json = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", prompt) })
                    })
                })
            })
        }
        
        // Using gemini-2.5-flash, the current standard for fast and accurate technical explanations
//        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"
        val url = "https://generativelanguage.googleapis.com/v1beta2/models/gemini-3.5-flash:generateText?key=$apiKey"
        val request = Request.Builder()
            .url(url)
            .post(json.toString().toRequestBody(mediaType))
            .addHeader("User-Agent", "CrossSset/1.0")
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string()
            if (!response.isSuccessful) {
                Log.e(TAG, "Gemini Error: ${response.code} - $responseBody")
                return null
            }
            if (responseBody == null) return null
            return try {
                val respJson = JSONObject(responseBody)
                respJson.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text").trim()
            } catch (e: Exception) {
                // If code is 200, the key is valid even if parsing fails for some reason
                if (response.isSuccessful) "OK" else null
            }
        }
    }
}
