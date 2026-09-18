package com.mrredhood.devforge.core.security

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidSecretStoreInstrumentedTest {
    @Test
    fun canEncryptAndDecryptCredentialWithAndroidKeystore() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = AndroidSecretStore(context)
        val key = "devforge.test.credential." + System.nanoTime()
        val value = "keystore-test-value"

        try {
            store.put(key, value)

            assertTrue(store.contains(key))
            assertEquals(value, store.get(key))
        } finally {
            store.remove(key)
        }

        assertFalse(store.contains(key))
        assertEquals(null, store.get(key))
    }
}
