package com.rawpos.scanner.feature.settings

import com.rawpos.scanner.core.AppInfo
import com.rawpos.scanner.core.settings.LanguageMode
import com.rawpos.scanner.core.settings.SettingsRepository
import com.rawpos.scanner.core.settings.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val appInfo = AppInfo(
        packageName = "com.rawpos.scanner",
        versionName = "1.0.0-test",
        buildType = "debug",
        minSdk = 26,
        targetSdk = 36,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `state reflects the repository initial values`() = runTest {
        val repository = FakeSettingsRepository(LanguageMode.ARABIC, ThemeMode.DARK)

        val viewModel = SettingsViewModel(repository, appInfo)

        assertEquals(LanguageMode.ARABIC, viewModel.state.value.language)
        assertEquals(ThemeMode.DARK, viewModel.state.value.theme)
        assertEquals("1.0.0-test", viewModel.state.value.versionName)
        assertEquals("debug", viewModel.state.value.buildType)
    }

    @Test
    fun `setLanguage writes through and updates state`() = runTest {
        val repository = FakeSettingsRepository(LanguageMode.SYSTEM, ThemeMode.SYSTEM)
        val viewModel = SettingsViewModel(repository, appInfo)

        viewModel.setLanguage(LanguageMode.ENGLISH)

        assertEquals(LanguageMode.ENGLISH, repository.language.value)
        assertEquals(LanguageMode.ENGLISH, viewModel.state.value.language)
    }

    @Test
    fun `setTheme writes through and updates state`() = runTest {
        val repository = FakeSettingsRepository(LanguageMode.SYSTEM, ThemeMode.SYSTEM)
        val viewModel = SettingsViewModel(repository, appInfo)

        viewModel.setTheme(ThemeMode.LIGHT)

        assertEquals(ThemeMode.LIGHT, repository.theme.value)
        assertEquals(ThemeMode.LIGHT, viewModel.state.value.theme)
    }

    @Test
    fun `setScanFeedback writes through and updates state`() = runTest {
        val repository = FakeSettingsRepository(LanguageMode.SYSTEM, ThemeMode.SYSTEM)
        val viewModel = SettingsViewModel(repository, appInfo)

        viewModel.setScanFeedback(false)

        assertEquals(false, repository.scanFeedback.value)
        assertEquals(false, viewModel.state.value.scanFeedback)
    }
}

private class FakeSettingsRepository(
    initialLanguage: LanguageMode,
    initialTheme: ThemeMode,
    initialScanFeedback: Boolean = true,
) : SettingsRepository {

    private val _language = MutableStateFlow(initialLanguage)
    private val _theme = MutableStateFlow(initialTheme)
    private val _scanFeedback = MutableStateFlow(initialScanFeedback)

    override val language: StateFlow<LanguageMode> = _language.asStateFlow()
    override val theme: StateFlow<ThemeMode> = _theme.asStateFlow()
    override val scanFeedback: StateFlow<Boolean> = _scanFeedback.asStateFlow()

    override fun setLanguage(mode: LanguageMode) {
        _language.value = mode
    }

    override fun setTheme(mode: ThemeMode) {
        _theme.value = mode
    }

    override fun setScanFeedback(enabled: Boolean) {
        _scanFeedback.value = enabled
    }
}
