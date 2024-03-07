/*
 *  Copyright 2024 Curity AB
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
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