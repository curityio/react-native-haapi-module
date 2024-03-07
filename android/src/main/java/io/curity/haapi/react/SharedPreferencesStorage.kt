package io.curity.haapi.react

import android.content.SharedPreferences
import com.facebook.react.bridge.ReactApplicationContext
import se.curity.identityserver.haapi.android.driver.Storage

class SharedPreferencesStorage(private val name: String, private val context: ReactApplicationContext) : Storage {
    override fun get(key: String): String? = getSharedPreferences().getString(key, null)

    override fun set(value: String, key: String) = getSharedPreferences().edit().putString(key, value).apply()

    override fun delete(key: String) = getSharedPreferences().edit().remove(key).apply()

    override fun getAll(): Map<String, String> {
        @Suppress("UNCHECKED_CAST")
        return getSharedPreferences().all.filterValues { it is String } as Map<String, String>
    }

    private fun getSharedPreferences(): SharedPreferences =
        context.getSharedPreferences(name, ReactApplicationContext.MODE_PRIVATE)
}