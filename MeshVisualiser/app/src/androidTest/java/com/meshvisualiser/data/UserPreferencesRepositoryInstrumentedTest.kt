package com.meshvisualiser.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserPreferencesRepositoryInstrumentedTest {

    private lateinit var repo: UserPreferencesRepository

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        repo = UserPreferencesRepository(context)
    }

    @Test
    fun onboardingCompleted_defaults_to_false() = runBlocking {
        // First read may be true from a previous test run on the same device,
        // so we reset it first
        repo.setOnboardingCompleted(false)
        val result = repo.onboardingCompleted.first()
        assertFalse(result)
    }

    @Test
    fun setOnboardingCompleted_persists_true() = runBlocking {
        repo.setOnboardingCompleted(true)
        val result = repo.onboardingCompleted.first()
        assertTrue(result)
        // Reset for other tests
        repo.setOnboardingCompleted(false)
    }

    @Test
    fun lastGroupCode_defaults_to_empty_string() = runBlocking {
        repo.setLastGroupCode("")
        val result = repo.lastGroupCode.first()
        assertEquals("", result)
    }

    @Test
    fun setLastGroupCode_persists_value() = runBlocking {
        repo.setLastGroupCode("MESH-42")
        val result = repo.lastGroupCode.first()
        assertEquals("MESH-42", result)
        // Reset
        repo.setLastGroupCode("")
    }

    @Test
    fun displayName_defaults_to_empty_string() = runBlocking {
        repo.setDisplayName("")
        val result = repo.displayName.first()
        assertEquals("", result)
    }

    @Test
    fun setDisplayName_persists_value() = runBlocking {
        repo.setDisplayName("Alice")
        val result = repo.displayName.first()
        assertEquals("Alice", result)
        // Reset
        repo.setDisplayName("")
    }

    @Test
    fun aiServerUrl_defaults_to_empty_string() = runBlocking {
        repo.setAiServerUrl("")
        val result = repo.aiServerUrl.first()
        assertEquals("", result)
    }

    @Test
    fun setAiServerUrl_persists_value() = runBlocking {
        repo.setAiServerUrl("https://example.com")
        val result = repo.aiServerUrl.first()
        assertEquals("https://example.com", result)
        // Reset
        repo.setAiServerUrl("")
    }

    @Test
    fun multiple_writes_overwrite_previous_value() = runBlocking {
        repo.setDisplayName("Alice")
        repo.setDisplayName("Bob")
        val result = repo.displayName.first()
        assertEquals("Bob", result)
        // Reset
        repo.setDisplayName("")
    }

    @Test
    fun independent_keys_do_not_interfere() = runBlocking {
        repo.setDisplayName("Alice")
        repo.setLastGroupCode("GROUP-1")

        assertEquals("Alice", repo.displayName.first())
        assertEquals("GROUP-1", repo.lastGroupCode.first())

        // Changing one doesn't affect the other
        repo.setDisplayName("Bob")
        assertEquals("GROUP-1", repo.lastGroupCode.first())

        // Reset
        repo.setDisplayName("")
        repo.setLastGroupCode("")
    }
}
