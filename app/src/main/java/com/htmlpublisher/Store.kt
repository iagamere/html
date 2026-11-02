package com.htmlpublisher

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

class HistoryItem(val name: String, val url: String, val at: Long)

class Store(context: Context) {
    // الرمز يُحفظ مشفّراً بمفتاح في Android Keystore. إذا تعذّر ذلك لا يُحفظ أبداً.
    private val secure: SharedPreferences? = try {
        val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context, "secure_prefs", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        null
    }
    private val plain = context.getSharedPreferences("history", Context.MODE_PRIVATE)

    var token: String?
        get() = try { secure?.getString("token", null) } catch (e: Exception) { null }
        set(v) {
            val ed = secure?.edit() ?: return
            if (v == null) ed.remove("token") else ed.putString("token", v)
            ed.apply()
        }

    fun history(): List<HistoryItem> = try {
        val arr = JSONArray(plain.getString("items", "[]"))
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            HistoryItem(o.getString("name"), o.getString("url"), o.getLong("at"))
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun addHistory(name: String, url: String): List<HistoryItem> {
        val list = listOf(HistoryItem(name, url, System.currentTimeMillis())) + history().filter { it.url != url }
        save(list.take(8))
        return history()
    }

    fun clearHistory() { plain.edit().remove("items").apply() }

    private fun save(list: List<HistoryItem>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("name", it.name).put("url", it.url).put("at", it.at)) }
        plain.edit().putString("items", arr.toString()).apply()
    }
}
