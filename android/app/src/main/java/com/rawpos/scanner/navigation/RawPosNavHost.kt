package com.rawpos.scanner.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rawpos.scanner.R
import com.rawpos.scanner.core.AppContainer
import com.rawpos.scanner.feature.connection.ConnectionScreen
import com.rawpos.scanner.feature.connection.ConnectionViewModel
import com.rawpos.scanner.feature.diagnostics.DiagnosticsScreen
import com.rawpos.scanner.feature.diagnostics.DiagnosticsViewModel
import com.rawpos.scanner.feature.scanner.ScannerScreen
import com.rawpos.scanner.feature.scanner.ScannerViewModel
import com.rawpos.scanner.feature.settings.SettingsScreen
import com.rawpos.scanner.feature.settings.SettingsUiState
import com.rawpos.scanner.feature.settings.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RawPosNavHost(
    container: AppContainer,
    settingsViewModel: SettingsViewModel,
    settings: SettingsUiState,
    onLanguageChanged: () -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val current = RawPosDestination.fromRoute(backStackEntry?.destination?.route)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(current.titleRes)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
                navigationIcon = {
                    if (current != RawPosDestination.CONNECTION) {
                        TextButton(onClick = { navController.popBackStack() }) {
                            Text(stringResource(R.string.common_back))
                        }
                    }
                },
                actions = {
                    if (current != RawPosDestination.SETTINGS) {
                        TextButton(
                            onClick = {
                                navController.navigate(RawPosDestination.SETTINGS.route) {
                                    launchSingleTop = true
                                }
                            },
                        ) {
                            Text(stringResource(R.string.common_settings))
                        }
                    } else {
                        TextButton(
                            onClick = {
                                navController.navigate(RawPosDestination.DIAGNOSTICS.route) {
                                    launchSingleTop = true
                                }
                            },
                        ) {
                            Text(stringResource(R.string.common_diagnostics))
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = RawPosDestination.CONNECTION.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(RawPosDestination.CONNECTION.route) {
                val viewModel: ConnectionViewModel = viewModel(factory = container.viewModelFactory)
                val state by viewModel.state.collectAsStateWithLifecycle()
                ConnectionScreen(
                    state = state,
                    onPreviewScanner = {
                        navController.navigate(RawPosDestination.SCANNER.route) {
                            launchSingleTop = true
                        }
                    },
                )
            }

            composable(RawPosDestination.SCANNER.route) {
                val viewModel: ScannerViewModel = viewModel(factory = container.viewModelFactory)
                val state by viewModel.state.collectAsStateWithLifecycle()
                ScannerScreen(
                    state = state,
                    onFrame = viewModel::onFrame,
                    onTogglePause = viewModel::togglePause,
                    onTorchChanged = viewModel::setTorch,
                    onCameraStarted = viewModel::onCameraStarted,
                )
            }

            composable(RawPosDestination.SETTINGS.route) {
                SettingsScreen(
                    state = settings,
                    onLanguageSelected = { mode ->
                        if (mode != settings.language) {
                            settingsViewModel.setLanguage(mode)
                            onLanguageChanged()
                        }
                    },
                    onThemeSelected = settingsViewModel::setTheme,
                    onOpenDiagnostics = {
                        navController.navigate(RawPosDestination.DIAGNOSTICS.route) {
                            launchSingleTop = true
                        }
                    },
                )
            }

            composable(RawPosDestination.DIAGNOSTICS.route) {
                val viewModel: DiagnosticsViewModel = viewModel(factory = container.viewModelFactory)
                DiagnosticsScreen(info = viewModel.appInfo)
            }
        }
    }
}
