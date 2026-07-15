package com.quicktodo.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.settingsStore by preferencesDataStore(name = "settings")

class SettingsDataStore(private val context: Context) {

    companion object {
        val STT_ENDPOINT = stringPreferencesKey("stt_endpoint")
        val STT_API_KEY = stringPreferencesKey("stt_api_key")
        val STT_MODEL = stringPreferencesKey("stt_model")
        val STT_RESOURCE_ID = stringPreferencesKey("stt_resource_id")
        val LLM_ENDPOINT = stringPreferencesKey("llm_endpoint")
        val LLM_API_KEY = stringPreferencesKey("llm_api_key")
        val LLM_MODEL = stringPreferencesKey("llm_model")
    }

    val sttEndpoint: Flow<String> = context.settingsStore.data.map { it[STT_ENDPOINT] ?: "" }
    val sttApiKey: Flow<String> = context.settingsStore.data.map { it[STT_API_KEY] ?: "" }
    val sttModel: Flow<String> = context.settingsStore.data.map { it[STT_MODEL] ?: "whisper-1" }
    val sttResourceId: Flow<String> = context.settingsStore.data.map { it[STT_RESOURCE_ID] ?: "" }
    val llmEndpoint: Flow<String> = context.settingsStore.data.map { it[LLM_ENDPOINT] ?: "" }
    val llmApiKey: Flow<String> = context.settingsStore.data.map { it[LLM_API_KEY] ?: "" }
    val llmModel: Flow<String> = context.settingsStore.data.map { it[LLM_MODEL] ?: "deepseek-chat" }

    fun getSttEndpointSync(): String = runBlocking { sttEndpoint.first() }
    fun getSttApiKeySync(): String = runBlocking { sttApiKey.first() }
    fun getSttModelSync(): String = runBlocking { sttModel.first() }
    fun getSttResourceIdSync(): String = runBlocking { sttResourceId.first() }
    fun getLlmEndpointSync(): String = runBlocking { llmEndpoint.first() }
    fun getLlmApiKeySync(): String = runBlocking { llmApiKey.first() }
    fun getLlmModelSync(): String = runBlocking { llmModel.first() }

    suspend fun saveSttSettings(endpoint: String, apiKey: String, model: String, resourceId: String) {
        context.settingsStore.edit { prefs ->
            prefs[STT_ENDPOINT] = endpoint
            prefs[STT_API_KEY] = apiKey
            prefs[STT_MODEL] = model
            prefs[STT_RESOURCE_ID] = resourceId
        }
    }

    suspend fun saveLlmSettings(endpoint: String, apiKey: String, model: String) {
        context.settingsStore.edit { prefs ->
            prefs[LLM_ENDPOINT] = endpoint
            prefs[LLM_API_KEY] = apiKey
            prefs[LLM_MODEL] = model
        }
    }
}
