package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.auth.AuthScreen
import com.example.auth.authStateFlow
import com.example.auth.signOut
import com.example.core.configureAppCheck
import com.example.data.ai.TelecomAiRepository
import com.example.data.repository.TelecomRepository
import com.example.data.sync.CsvTelecomParser
import com.example.data.sync.DailySyncWorker
import com.example.data.sync.DriveAndZaloClient
import com.example.ui.screens.AiInsightsScreen
import com.example.ui.screens.CoverageMapScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DatabaseScreen
import com.example.ui.screens.SyncAndZaloScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.TelecomViewModel
import com.example.ui.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Serializable
sealed interface AppRoute {
    @Serializable data object Dashboard : AppRoute
    @Serializable data object Database : AppRoute
    @Serializable data object CoverageMap : AppRoute
    @Serializable data object AiInsights : AppRoute
    @Serializable data object SyncAndZalo : AppRoute
}

private data class NavTab(
    val route: AppRoute,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Step 1: Configure Firebase App Check with launch intent before any UI or ViewModels
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = { /* AuthStateListener drives transition */ })
    } else {
        key(user.uid) {
            MainFeatureShell(
                currentUserId = user.uid,
                userEmail = user.email.orEmpty()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainFeatureShell(
    currentUserId: String,
    userEmail: String
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: TelecomViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                TelecomViewModel(
                    repository = TelecomRepository(db, Firebase.auth),
                    aiRepository = TelecomAiRepository(),
                    currentUserId = currentUserId
                )
            }
        }
    )

    val recordsState by viewModel.recordsState.collectAsStateWithLifecycle()
    val filteredRecords by viewModel.filteredRecords.collectAsStateWithLifecycle()
    val selectedCarrier by viewModel.selectedCarrier.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val sortOption by viewModel.sortOption.collectAsStateWithLifecycle()
    val syncConfig by viewModel.syncConfig.collectAsStateWithLifecycle()
    val syncLogs by viewModel.syncLogs.collectAsStateWithLifecycle()
    val zaloReports by viewModel.zaloReports.collectAsStateWithLifecycle()
    val aiState by viewModel.aiState.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(statusBanner) {
        val msg = statusBanner
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusBanner()
        }
    }

    val csvPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            viewModel.importFromSafUri(context, uri.toString(), syncConfig.driveFolderPath)
        }
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = backStackEntry?.destination

    val tabs = remember {
        listOf(
            NavTab(AppRoute.Dashboard, "Tổng quan", Icons.Filled.Dashboard, Icons.Outlined.Dashboard, "tab_dashboard"),
            NavTab(AppRoute.Database, "CSDL", Icons.Filled.Storage, Icons.Outlined.Storage, "tab_database"),
            NavTab(AppRoute.CoverageMap, "Bản đồ", Icons.Filled.Map, Icons.Outlined.Map, "tab_map"),
            NavTab(AppRoute.AiInsights, "AI", Icons.Filled.Psychology, Icons.Outlined.Psychology, "tab_ai"),
            NavTab(AppRoute.SyncAndZalo, "Đồng bộ", Icons.Filled.CloudSync, Icons.Outlined.CloudSync, "tab_sync")
        )
    }

    fun navigateToTab(route: AppRoute) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val allRecords = (recordsState as? UiState.Success)?.data.orEmpty()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Telecom CSDL",
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (userEmail.isNotBlank()) {
                                Text(
                                    text = userEmail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    actions = {
                        IconButton(
                            onClick = {
                                signOut(credentialManager, coroutineScope)
                            },
                            modifier = Modifier.testTag("sign_out_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = "Đăng xuất"
                            )
                        }
                    }
                )
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar {
                        tabs.forEach { tab ->
                            val selected = currentDest?.hasRoute(tab.route::class) == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label, maxLines = 1) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail {
                        tabs.forEach { tab ->
                            val selected = currentDest?.hasRoute(tab.route::class) == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f).fillMaxSize()) {
                    if (isBusy) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        NavHost(
                            navController = navController,
                            startDestination = AppRoute.Dashboard,
                            modifier = Modifier
                                .widthIn(max = 900.dp)
                                .fillMaxSize()
                        ) {
                            composable<AppRoute.Dashboard> {
                                DashboardScreen(
                                    recordsState = recordsState,
                                    selectedCarrier = selectedCarrier,
                                    syncConfig = syncConfig,
                                    onSelectCarrier = viewModel::setCarrierFilter,
                                    onImportCsvClick = {
                                        csvPickerLauncher.launch(arrayOf("text/*", "application/csv", "*/*"))
                                    },
                                    onLoadDemoDataset = viewModel::loadDemoDataset,
                                    onQuickSyncClick = { navigateToTab(AppRoute.SyncAndZalo) },
                                    onQuickZaloClick = {
                                        val nowText = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
                                        val summary = DailySyncWorker.buildDailyZaloSummary(
                                            syncConfig.driveFolderPath,
                                            nowText,
                                            allRecords
                                        )
                                        val channel = DriveAndZaloClient.launchZaloShare(context, summary)
                                        viewModel.recordZaloShareAction(syncConfig.zaloTargetLabel, channel, summary)
                                    },
                                    onNavigateToDatabase = { navigateToTab(AppRoute.Database) }
                                )
                            }

                            composable<AppRoute.Database> {
                                DatabaseScreen(
                                    records = filteredRecords,
                                    selectedCarrier = selectedCarrier,
                                    selectedCategory = selectedCategory,
                                    searchQuery = searchQuery,
                                    sortOption = sortOption,
                                    onSelectCarrier = viewModel::setCarrierFilter,
                                    onSelectCategory = viewModel::setCategoryFilter,
                                    onSearchQueryChange = viewModel::setSearchQuery,
                                    onSortOptionChange = viewModel::setSortOption,
                                    onSaveRecord = viewModel::saveRecord,
                                    onDeleteRecord = viewModel::deleteRecord,
                                    onImportCsvClick = {
                                        csvPickerLauncher.launch(arrayOf("text/*", "application/csv", "*/*"))
                                    },
                                    onExportCsvClick = { exportList ->
                                        val csv = CsvTelecomParser.exportToCsv(exportList)
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_SUBJECT, "Telecom_CSDL_Export.csv")
                                            putExtra(Intent.EXTRA_TEXT, csv)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Xuất dữ liệu CSV"))
                                    }
                                )
                            }

                            composable<AppRoute.CoverageMap> {
                                CoverageMapScreen(
                                    records = allRecords,
                                    selectedCarrier = selectedCarrier,
                                    onSelectCarrier = viewModel::setCarrierFilter
                                )
                            }

                            composable<AppRoute.AiInsights> {
                                AiInsightsScreen(
                                    aiState = aiState,
                                    recordCount = allRecords.size,
                                    onAskAi = viewModel::runAiAnalysis,
                                    onSendTextToZalo = { text ->
                                        val channel = DriveAndZaloClient.launchZaloShare(context, text)
                                        viewModel.recordZaloShareAction(syncConfig.zaloTargetLabel, channel, text)
                                    }
                                )
                            }

                            composable<AppRoute.SyncAndZalo> {
                                SyncAndZaloScreen(
                                    syncConfig = syncConfig,
                                    records = allRecords,
                                    syncLogs = syncLogs,
                                    zaloReports = zaloReports,
                                    isBusy = isBusy,
                                    onSaveConfig = { cfg -> viewModel.saveSyncConfiguration(context, cfg) },
                                    onSyncNow = { cfg -> viewModel.syncNowFromConfig(context, cfg) },
                                    onPickLocalFile = {
                                        csvPickerLauncher.launch(arrayOf("text/*", "application/csv", "*/*"))
                                    },
                                    onShareToZaloApp = { target, text ->
                                        val channel = DriveAndZaloClient.launchZaloShare(context, text)
                                        viewModel.recordZaloShareAction(target, channel, text)
                                    },
                                    onSendViaWebhook = viewModel::sendZaloWebhookReport,
                                    onCopySummary = { text ->
                                        viewModel.recordZaloShareAction(syncConfig.zaloTargetLabel, "Sao chép", text)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
