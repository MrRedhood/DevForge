package com.mrredhood.devforge.core.security

/** Small abstraction for secrets that must never be exposed through ordinary app state. */
interface SecretStore {
    fun put(key: String, value: String)
    fun get(key: String): String?
    fun remove(key: String)
}
