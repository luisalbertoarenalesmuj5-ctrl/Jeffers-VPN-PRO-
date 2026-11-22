package com.example

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DashboardCustomize
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.audio.ChristmasSoundEngine
import com.example.data.model.CountryRegion
import com.example.data.model.HomeLayoutStyle
import com.example.data.model.LogLevel
import com.example.data.model.PlanTier
import com.example.data.model.VpnConnectionStatus
import com.example.data.remote.GoogleAuthManager
import com.example.data.remote.GoogleSignInOutcome
import com.example.ui.VpnViewModel
import com.example.ui.components.LuxuryChristmasHat
import com.example.ui.screens.AdminPanelScreen
import com.example.ui.screens.CountriesListScreen
import com.example.ui.screens.CountryServersScreen
import com.example.ui.screens.FreeCinemaScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.HostCheckerScreen
import com.example.ui.screens.HotspotScreen
import com.example.ui.screens.LiveMatchesScreen
import com.example.ui.screens.LiveSeriesScreen
import com.example.ui.screens.LogsScreen
import com.example.ui.screens.OnboardingAuthScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.ThemesScreen
import com.example.ui.screens.VpnModeScreen
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.ChampagneGoldBright
import com.example.ui.theme.CranberryRed
import com.example.ui.theme.CyberEmerald
import com.example.ui.theme.FrostIce
import com.example.ui.theme.JeffersVpnTheme
import com.example.ui.theme.MonoTypography
import com.example.ui.theme.ObsidianCardElevated
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.OutlineSubtle
import com.example.ui.theme.TextMuted
import com.example.vpn.VpnStateHolder
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

const val ROUTE_ONBOARDING = "onboarding"
const val ROUTE_HOME = "home"
const val ROUTE_COUNTRIES = "countries"
const val ROUTE_COUNTRY_SERVERS = "country_servers"
const val ROUTE_PROFILE = "profile"
const val ROUTE_VPN_MODE = "vpn_mode"
const val ROUTE_SETTINGS = "settings"
const val ROUTE_HOTSPOT = "hotspot"
const val ROUTE_HOST_CHECKER = "host_checker"
const val ROUTE_THEMES = "themes"
const val ROUTE_LOGS = "logs"
const val ROUTE_ADMIN = "admin"
const val ROUTE_LIVE_MATCHES = "live_matches"
const val ROUTE_LIVE_SERIES = "live_series"
const val ROUTE_FREE_CINEMA = "free_cinema"

private const val WHATSAPP_GROUP_1_URL = "https://chat.whatsapp.com/Hw0hVryjj2LAjFS7RrB2RN?s=cl&p=a&iam=2"
private const val WHATSAPP_GROUP_2_URL = "https://chat.whatsapp.com/EQNUA6cvsMd8d8BasBI8pA?s=cl&p=a&iam=2"
private const val PLANS_CONTACT_DISPLAY = "4783 8004"
private const val PLANS_CONTACT_WA_URL =
    "https://wa.me/50247838004?text=Hola%2C%20quiero%20informaci%C3%B3n%20sobre%20los%20planes%20PRO%2C%20PREMIUM%20y%20SUPER%20PRO%20de%20Jeffers%20VPN%20PRO."
private const val PLANS_CONTACT_TEL_URI = "tel:47838004"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        setContent {
            val viewModel: VpnViewModel = viewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            JeffersVpnTheme(preset = uiState.settings.themePreset) {
                JeffersVpnRoot(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun JeffersVpnRoot(viewModel: VpnViewModel) {
    val context = LocalContext.current
    val initialUser = remember(context) {
        if (GoogleAuthManager.isFirebaseAvailable(context)) {
            try {
                Firebase.auth.currentUser
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
    }
    val currentUser by remember(context) {
        GoogleAuthManager.authStateFlow(context)
    }.collectAsStateWithLifecycle(initialValue = initialUser)

    val authScope = rememberCoroutineScope()
    var isLinkingGoogle by remember { mutableStateOf(false) }
    var googleLinkError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (currentUser == null) {
            GoogleAuthManager.attemptAutoSignIn(context)
        }
    }

    LaunchedEffect(currentUser?.uid) {
        val active = currentUser
        if (active != null) {
            googleLinkError = null
            viewModel.onAuthenticatedUserChanged(
                uid = active.uid,
                email = active.email,
                displayName = active.displayName
            )
        } else {
            viewModel.onSignedOut()
        }
    }

    val onLinkGoogleClick: () -> Unit = {
        if (!isLinkingGoogle) {
            authScope.launch {
                isLinkingGoogle = true
                googleLinkError = null
                when (val outcome = GoogleAuthManager.signInWithGoogle(context)) {
                    is GoogleSignInOutcome.Success -> {
                        Toast.makeText(
                            context,
                            "Google vinculado: ${outcome.user.email ?: "listo"}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    GoogleSignInOutcome.Cancelled -> Unit
                    is GoogleSignInOutcome.Error -> googleLinkError = outcome.message
                }
                isLinkingGoogle = false
            }
        }
    }

    JeffersVpnAppContent(
        viewModel = viewModel,
        firebaseUser = currentUser,
        isLinkingGoogle = isLinkingGoogle,
        googleLinkError = googleLinkError,
        onLinkGoogleClick = onLinkGoogleClick
    )
}

@Composable
private fun JeffersVpnAppContent(
    viewModel: VpnViewModel,
    firebaseUser: FirebaseUser?,
    isLinkingGoogle: Boolean,
    googleLinkError: String?,
    onLinkGoogleClick: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isCheckingHost by viewModel.isCheckingHost.collectAsStateWithLifecycle()
    val hostCheckResult by viewModel.hostCheckResult.collectAsStateWithLifecycle()
    val hotspotRunning by viewModel.hotspotRunning.collectAsStateWithLifecycle()
    val hotspotClients by viewModel.hotspotClients.collectAsStateWithLifecycle()
    val lastGeneratedLicense by viewModel.lastGeneratedLicense.collectAsStateWithLifecycle()
    val activationMessage by viewModel.activationMessage.collectAsStateWithLifecycle()
    val adminFeedback by viewModel.adminFeedback.collectAsStateWithLifecycle()
    val isCloudAdmin by viewModel.isCloudAdmin.collectAsStateWithLifecycle()
    val authenticatedEmail by viewModel.authenticatedEmail.collectAsStateWithLifecycle()
    val isAnalyzingVps by viewModel.isAnalyzingVps.collectAsStateWithLifecycle()
    val vpsAnalysisReport by viewModel.vpsAnalysisReport.collectAsStateWithLifecycle()
    val authFeedback by viewModel.authFeedback.collectAsStateWithLifecycle()

    val navController = rememberNavController()
    val startDestination = remember {
        if (viewModel.initialAccountRegistered) ROUTE_HOME else ROUTE_ONBOARDING
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> ChristmasSoundEngine.resumeAmbientFromForeground()
                Lifecycle.Event.ON_PAUSE -> ChristmasSoundEngine.pauseAmbientForAppBackground()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            ChristmasSoundEngine.pauseAmbientForAppBackground()
        }
    }
    LaunchedEffect(currentRoute) {
        if (currentRoute == ROUTE_LIVE_MATCHES ||
            currentRoute == ROUTE_LIVE_SERIES ||
            currentRoute == ROUTE_FREE_CINEMA
        ) {
            ChristmasSoundEngine.pauseAmbientForMedia()
        } else {
            ChristmasSoundEngine.resumeAmbientIfEnabled()
        }
    }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showHomeLayoutDialog by remember { mutableStateOf(false) }
    var showPlansContactDialog by remember { mutableStateOf(false) }
    val effectiveTier = uiState.settings.activeSubscription.effectiveTier()

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.startVpnConnection()
        } else {
            VpnStateHolder.appendLog(
                "TUN",
                "El usuario canceló el permiso del sistema para crear la VPN",
                LogLevel.WARNING
            )
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        val prepareIntent = VpnService.prepare(context)
        if (prepareIntent != null) {
            vpnPermissionLauncher.launch(prepareIntent)
        } else {
            viewModel.startVpnConnection()
        }
    }

    val initiateVpnConnect = {
        val status = uiState.telemetry.status
        if (status == VpnConnectionStatus.CONNECTED || status == VpnConnectionStatus.CONNECTING) {
            viewModel.stopVpnConnection()
        } else {
            val needsNotificationPermission =
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED

            if (needsNotificationPermission) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                val prepareIntent = VpnService.prepare(context)
                if (prepareIntent != null) {
                    vpnPermissionLauncher.launch(prepareIntent)
                } else {
                    viewModel.startVpnConnection()
                }
            }
        }
    }

    val navigateFromDrawer: (String) -> Unit = { route ->
        viewModel.playNavigationChimeSound()
        scope.launch {
            drawerState.close()
            navController.navigate(route) {
                launchSingleTop = true
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = currentRoute != ROUTE_ONBOARDING,
        drawerContent = {
            JeffersDrawerContent(
                drawerAlpha = uiState.settings.drawerAlpha,
                performanceModeTitle = uiState.settings.performanceMode.title,
                isChristmasTheme = uiState.settings.themePreset.isChristmas,
                effectiveTier = effectiveTier,
                userAlias = uiState.settings.userAlias,
                userCountryFlag = uiState.settings.userCountry.flagEmoji,
                isVpnConnected = uiState.telemetry.status == VpnConnectionStatus.CONNECTED,
                onProfileClick = { navigateFromDrawer(ROUTE_PROFILE) },
                onVpnModeClick = { navigateFromDrawer(ROUTE_VPN_MODE) },
                onSettingsClick = { navigateFromDrawer(ROUTE_SETTINGS) },
                onHotspotClick = { navigateFromDrawer(ROUTE_HOTSPOT) },
                onHostCheckerClick = { navigateFromDrawer(ROUTE_HOST_CHECKER) },
                onThemesClick = { navigateFromDrawer(ROUTE_THEMES) },
                onHomeStyleClick = {
                    scope.launch { drawerState.close() }
                    showHomeLayoutDialog = true
                },
                onLogsClick = { navigateFromDrawer(ROUTE_LOGS) },
                onLiveMatchesClick = { navigateFromDrawer(ROUTE_LIVE_MATCHES) },
                onLiveSeriesClick = { navigateFromDrawer(ROUTE_LIVE_SERIES) },
                onFreeCinemaClick = { navigateFromDrawer(ROUTE_FREE_CINEMA) },
                onWhatsAppGroup1Click = {
                    scope.launch { drawerState.close() }
                    openExternalAction(context, Intent.ACTION_VIEW, WHATSAPP_GROUP_1_URL)
                },
                onWhatsAppGroup2Click = {
                    scope.launch { drawerState.close() }
                    openExternalAction(context, Intent.ACTION_VIEW, WHATSAPP_GROUP_2_URL)
                },
                onPlansContactClick = {
                    scope.launch { drawerState.close() }
                    showPlansContactDialog = true
                }
            )
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            NavHost(
                navController = navController,
                startDestination = startDestination
            ) {
                composable(ROUTE_ONBOARDING) {
                    OnboardingAuthScreen(
                        initialTermsAccepted = uiState.settings.termsAccepted,
                        initialCountry = uiState.settings.userCountry,
                        initialAlias = uiState.settings.userAlias,
                        currentDeviceId = uiState.settings.deviceId,
                        authFeedback = authFeedback,
                        onClearAuthFeedback = viewModel::clearAuthFeedback,
                        onCountrySelected = viewModel::updateUserCountry,
                        onRegisterAccount = { alias, country, code ->
                            viewModel.registerNewAccount(alias, country, code) {
                                navController.navigate(ROUTE_HOME) {
                                    popUpTo(ROUTE_ONBOARDING) { inclusive = true }
                                }
                            }
                        },
                        onSignInPreviousDevice = { prevId, alias, country, code ->
                            viewModel.signInWithPreviousDevice(prevId, alias, country, code) {
                                navController.navigate(ROUTE_HOME) {
                                    popUpTo(ROUTE_ONBOARDING) { inclusive = true }
                                }
                            }
                        },
                        onSignInPersonalClient = { username, secret, country ->
                            viewModel.signInPersonalClient(username, secret, country) {
                                navController.navigate(ROUTE_HOME) {
                                    popUpTo(ROUTE_ONBOARDING) { inclusive = true }
                                }
                            }
                        },
                        linkedGoogleEmail = authenticatedEmail ?: firebaseUser?.email,
                        isLinkingGoogle = isLinkingGoogle,
                        googleLinkError = googleLinkError,
                        onLinkGoogleClick = onLinkGoogleClick
                    )
                }

                composable(ROUTE_HOME) {
                    LaunchedEffect(Unit) {
                        viewModel.playWelcomeChimeOnce()
                    }
                    HomeScreen(
                        uiState = uiState,
                        onOpenDrawer = {
                            viewModel.playDrawerOpenedSound()
                            scope.launch { drawerState.open() }
                        },
                        onToggleConnect = initiateVpnConnect,
                        onOpenCountries = {
                            viewModel.playNavigationChimeSound()
                            navController.navigate(ROUTE_COUNTRIES) { launchSingleTop = true }
                        },
                        onTestActiveProfile = viewModel::testProfileLatency,
                        onToggleChristmasAmbient = {
                            viewModel.setChristmasAmbientEnabled(!uiState.settings.christmasAmbientEnabled)
                        },
                        onCycleChristmasSound = viewModel::cycleChristmasSoundStyle,
                        onBellBurst = viewModel::playBellBurstSound
                    )
                }

                composable(ROUTE_COUNTRIES) {
                    CountriesListScreen(
                        uiState = uiState,
                        onBack = { navController.popBackStack() },
                        onOpenCountry = { country ->
                            navController.navigate("$ROUTE_COUNTRY_SERVERS/${country.name}")
                        }
                    )
                }

                composable(
                    route = "$ROUTE_COUNTRY_SERVERS/{country}",
                    arguments = listOf(navArgument("country") { type = NavType.StringType })
                ) { entry ->
                    val countryArg = entry.arguments?.getString("country") ?: CountryRegion.GENERAL.name
                    val country = CountryRegion.fromName(countryArg)
                    CountryServersScreen(
                        country = country,
                        uiState = uiState,
                        onBack = { navController.popBackStack() },
                        onSelectProfile = viewModel::selectProfile,
                        onSelectCarrier = { selectedCarrier, selectedTier ->
                            viewModel.selectOrProvisionCarrierProfile(country, selectedCarrier, selectedTier)
                        },
                        onTestCarrier = { selectedCarrier, selectedTier ->
                            viewModel.testCountryCarrierProfile(country, selectedCarrier, selectedTier)
                        },
                        onTestProfile = viewModel::testProfileLatency,
                        onOpenActivatePlan = {
                            navController.navigate(ROUTE_PROFILE) { launchSingleTop = true }
                        }
                    )
                }

                composable(ROUTE_VPN_MODE) {
                    VpnModeScreen(
                        uiState = uiState,
                        onSelectMode = viewModel::setPerformanceMode,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(ROUTE_SETTINGS) {
                    SettingsScreen(
                        uiState = uiState,
                        onSaveSettings = viewModel::saveEngineSettings,
                        onVerifyAdminPassword = viewModel::verifyAdminPassword,
                        onSavePersonalTunnelCredentials = viewModel::updatePersonalTunnelCredentials,
                        onOpenAdminPanel = {
                            navController.navigate(ROUTE_ADMIN) { launchSingleTop = true }
                        },
                        onOpenLiveMatches = {
                            navController.navigate(ROUTE_LIVE_MATCHES) { launchSingleTop = true }
                        },
                        onBack = { navController.popBackStack() },
                        onToggleChristmasSounds = viewModel::setChristmasSoundsEnabled,
                        onToggleChristmasAmbient = viewModel::setChristmasAmbientEnabled
                    )
                }

                composable(ROUTE_LIVE_MATCHES) {
                    LiveMatchesScreen(
                        hasAccess = effectiveTier.includesLiveFootball,
                        activeTier = effectiveTier,
                        onOpenActivatePlan = {
                            navController.navigate(ROUTE_PROFILE) { launchSingleTop = true }
                        },
                        onOpenContactPlans = { showPlansContactDialog = true },
                        backgroundEffect = uiState.settings.backgroundEffect,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(ROUTE_LIVE_SERIES) {
                    LiveSeriesScreen(
                        hasAccess = effectiveTier.includesChannels,
                        activeTier = effectiveTier,
                        onOpenActivatePlan = {
                            navController.navigate(ROUTE_PROFILE) { launchSingleTop = true }
                        },
                        onOpenContactPlans = { showPlansContactDialog = true },
                        backgroundEffect = uiState.settings.backgroundEffect,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(ROUTE_FREE_CINEMA) {
                    FreeCinemaScreen(
                        hasAccess = effectiveTier.includesChannels,
                        activeTier = effectiveTier,
                        onOpenActivatePlan = {
                            navController.navigate(ROUTE_PROFILE) { launchSingleTop = true }
                        },
                        onOpenContactPlans = { showPlansContactDialog = true },
                        backgroundEffect = uiState.settings.backgroundEffect,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(ROUTE_ADMIN) {
                    LaunchedEffect(authenticatedEmail) {
                        if (!authenticatedEmail.isNullOrBlank()) {
                            viewModel.syncAdminLicensesFromCloud()
                        }
                    }
                    AdminPanelScreen(
                        uiState = uiState,
                        lastGeneratedLicense = lastGeneratedLicense,
                        isAnalyzingVps = isAnalyzingVps,
                        vpsAnalysisReport = vpsAnalysisReport,
                        isCloudAdmin = isCloudAdmin,
                        authenticatedEmail = authenticatedEmail ?: firebaseUser?.email,
                        adminFeedback = adminFeedback,
                        onGenerateCode = viewModel::generatePlanCode,
                        onActivateById = viewModel::activatePlanById,
                        onCreatePersonalClient = viewModel::createPersonalClientLicense,
                        onExtendLicense = viewModel::extendLicense,
                        onToggleRevoke = viewModel::toggleLicenseRevoke,
                        onDeleteLicense = viewModel::deleteLicense,
                        onChangeAdminPassword = viewModel::changeAdminPassword,
                        onSaveProfile = viewModel::saveProfile,
                        onSelectProfile = viewModel::selectProfile,
                        onDeleteProfile = viewModel::deleteProfile,
                        onRunVpsAnalysis = viewModel::analyzeVps,
                        onSyncCloudLicenses = viewModel::syncAdminLicensesFromCloud,
                        onClearAdminFeedback = viewModel::clearAdminFeedback,
                        isLinkingGoogle = isLinkingGoogle,
                        onLinkGoogleClick = onLinkGoogleClick,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(ROUTE_PROFILE) {
                    ProfileScreen(
                        uiState = uiState,
                        activationMessage = activationMessage,
                        onUpdateAlias = viewModel::updateUserAlias,
                        onRedeemCode = viewModel::redeemActivationCode,
                        onRedeemPersonalUsername = viewModel::redeemPersonalUsername,
                        onSyncById = viewModel::refreshActivationByDeviceId,
                        onClearActivationMessage = viewModel::clearActivationMessage,
                        onSignOut = {
                            scope.launch {
                                GoogleAuthManager.signOut(context)
                                viewModel.signOutAccount()
                                navController.navigate(ROUTE_ONBOARDING) {
                                    popUpTo(0) { inclusive = true }
                                }
                            }
                        },
                        onBack = { navController.popBackStack() },
                        authenticatedEmail = authenticatedEmail ?: firebaseUser?.email,
                        isLinkingGoogle = isLinkingGoogle,
                        googleLinkError = googleLinkError,
                        onLinkGoogleClick = onLinkGoogleClick
                    )
                }

                composable(ROUTE_HOST_CHECKER) {
                    HostCheckerScreen(
                        isChecking = isCheckingHost,
                        result = hostCheckResult,
                        surfaceAlpha = uiState.settings.cardAlpha,
                        onVerify = viewModel::runHostCheck,
                        onBack = { navController.popBackStack() },
                        backgroundEffect = uiState.settings.backgroundEffect
                    )
                }

                composable(ROUTE_HOTSPOT) {
                    HotspotScreen(
                        isRunning = hotspotRunning,
                        activeClients = hotspotClients,
                        surfaceAlpha = uiState.settings.cardAlpha,
                        onToggleHotspot = viewModel::toggleHotspotProxy,
                        onBack = { navController.popBackStack() },
                        backgroundEffect = uiState.settings.backgroundEffect
                    )
                }

                composable(ROUTE_THEMES) {
                    ThemesScreen(
                        uiState = uiState,
                        onSelectPreset = viewModel::setThemePreset,
                        onSelectBackground = viewModel::setBackgroundEffect,
                        onSelectServerAnimation = viewModel::setServerAnimation,
                        onUpdateAlphas = viewModel::updateTransparencies,
                        onResetAppearance = viewModel::resetAppearance,
                        onBack = { navController.popBackStack() },
                        onToggleChristmasSounds = viewModel::setChristmasSoundsEnabled,
                        onToggleChristmasAmbient = viewModel::setChristmasAmbientEnabled,
                        onSelectChristmasSoundStyle = viewModel::setChristmasSoundStyle,
                        onPreviewChristmasSound = viewModel::previewChristmasSound
                    )
                }

                composable(ROUTE_LOGS) {
                    LogsScreen(
                        uiState = uiState,
                        onClearLogs = viewModel::clearLogs,
                        onClearHistory = viewModel::clearSessionHistory,
                        onRunDiagnostics = viewModel::runNetworkDiagnostics,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }

    if (showHomeLayoutDialog) {
        AlertDialog(
            onDismissRequest = { showHomeLayoutDialog = false },
            title = { Text("Diseño de Inicio") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HomeLayoutStyle.entries.forEach { style ->
                        val selected = uiState.settings.homeLayoutStyle == style
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    viewModel.setHomeLayoutStyle(style)
                                    showHomeLayoutDialog = false
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = {
                                    viewModel.setHomeLayoutStyle(style)
                                    showHomeLayoutDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(style.title, style = MaterialTheme.typography.titleSmall)
                                Text(style.subtitle, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHomeLayoutDialog = false }) {
                    Text("Cerrar")
                }
            }
        )
    }
    if (showPlansContactDialog) {
        val primaryColor = MaterialTheme.colorScheme.primary
        val proColor = Color(PlanTier.PRO.colorHex)
        val premiumColor = Color(PlanTier.PREMIUM.colorHex)
        val superProColor = Color(PlanTier.SUPER_PRO.colorHex)
        AlertDialog(
            onDismissRequest = { showPlansContactDialog = false },
            title = { Text("Planes PRO • PREMIUM • SUPER PRO") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Beneficios de cada plan y contacto oficial para activarlo:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = proColor.copy(alpha = 0.10f),
                        border = BorderStroke(1.dp, proColor.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("PLAN PRO", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = proColor)
                            Text("Incluye Canales en Vivo (Series y Cine TV) + Servidores PRO.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = premiumColor.copy(alpha = 0.10f),
                        border = BorderStroke(1.dp, premiumColor.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("PLAN PREMIUM", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = premiumColor)
                            Text("Incluye Fútbol en Vivo HD + VPSpremiumPRO + Hotspot.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = superProColor.copy(alpha = 0.12f),
                        border = BorderStroke(1.2.dp, superProColor.copy(alpha = 0.65f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("PLAN SUPER PRO ★", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = superProColor)
                            Text("Incluye Fútbol en Vivo + Canales TV + todo VIP/PRO/PREMIUM.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = primaryColor.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "NÚMERO DE CONTACTO",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted
                                )
                                Text(
                                    text = PLANS_CONTACT_DISPLAY,
                                    style = MonoTypography.address.copy(
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = primaryColor
                                )
                            }
                            TextButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(PLANS_CONTACT_DISPLAY))
                                    Toast.makeText(
                                        context,
                                        "Número copiado: $PLANS_CONTACT_DISPLAY",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ContentCopy,
                                    contentDescription = "Copiar número",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copiar")
                            }
                        }
                    }

                    Button(
                        onClick = {
                            showPlansContactDialog = false
                            openExternalAction(context, Intent.ACTION_VIEW, PLANS_CONTACT_WA_URL)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyberEmerald,
                            contentColor = ObsidianVoid
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(Icons.Rounded.Forum, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Escribir por WhatsApp", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            showPlansContactDialog = false
                            openExternalAction(context, Intent.ACTION_DIAL, PLANS_CONTACT_TEL_URI)
                        },
                        border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.55f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(Icons.Rounded.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Llamar al $PLANS_CONTACT_DISPLAY")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPlansContactDialog = false }) {
                    Text("Cerrar")
                }
            }
        )
    }
}

private fun openExternalAction(context: Context, action: String, uriString: String) {
    try {
        val intent = Intent(action, Uri.parse(uriString)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(
            context,
            "No se encontró una app disponible para abrir el enlace.",
            Toast.LENGTH_SHORT
        ).show()
    }
}

@Composable
private fun JeffersDrawerContent(
    drawerAlpha: Float,
    performanceModeTitle: String,
    isChristmasTheme: Boolean,
    effectiveTier: PlanTier,
    userAlias: String,
    userCountryFlag: String,
    isVpnConnected: Boolean,
    onProfileClick: () -> Unit,
    onVpnModeClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onHotspotClick: () -> Unit,
    onHostCheckerClick: () -> Unit,
    onThemesClick: () -> Unit,
    onHomeStyleClick: () -> Unit,
    onLogsClick: () -> Unit,
    onLiveMatchesClick: () -> Unit,
    onLiveSeriesClick: () -> Unit,
    onFreeCinemaClick: () -> Unit,
    onWhatsAppGroup1Click: () -> Unit,
    onWhatsAppGroup2Click: () -> Unit,
    onPlansContactClick: () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val accentColor = MaterialTheme.colorScheme.secondary
    val tierColor = Color(effectiveTier.colorHex)

    ModalDrawerSheet(
        drawerContainerColor = if (isChristmasTheme) {
            Color(0xFF05130E).copy(alpha = drawerAlpha)
        } else {
            Color(0xFF0A0F16).copy(alpha = drawerAlpha)
        },
        modifier = Modifier.width(304.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color.Transparent,
                border = BorderStroke(
                    1.dp,
                    if (isChristmasTheme) ChampagneGold.copy(alpha = 0.56f) else OutlineSubtle.copy(alpha = 0.4f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = if (isChristmasTheme) {
                            Brush.linearGradient(
                                listOf(
                                    CranberryRed.copy(alpha = 0.24f),
                                    Color(0xFF071B14),
                                    ChampagneGold.copy(alpha = 0.16f)
                                )
                            )
                        } else {
                            Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                        },
                        shape = RoundedCornerShape(18.dp)
                    )
            ) {
                Column {
                    if (isChristmasTheme) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.5.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            Color.Transparent,
                                            ChampagneGoldBright.copy(alpha = 0.90f),
                                            accentColor.copy(alpha = 0.70f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.jeffers_vpn_icon),
                            contentDescription = "Jeffers VPN PRO",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .border(
                                    1.5.dp,
                                    if (isChristmasTheme) ChampagneGoldBright.copy(alpha = 0.88f)
                                    else primaryColor.copy(alpha = 0.7f),
                                    RoundedCornerShape(14.dp)
                                )
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "JEFFERS VPN PRO",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(if (isVpnConnected) CyberEmerald else ChampagneGold)
                                )
                                Text(
                                    text = if (isVpnConnected) "Online • BHTTP" else "Listo • BHTTP",
                                    style = MonoTypography.terminal,
                                    color = if (isVpnConnected) CyberEmerald else FrostIce.copy(alpha = 0.78f)
                                )
                            }
                            if (isChristmasTheme) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = CranberryRed.copy(alpha = 0.26f),
                                    border = BorderStroke(0.8.dp, ChampagneGold.copy(alpha = 0.65f))
                                ) {
                                    Text(
                                        text = "✦ EDICIÓN NAVIDAD PRO",
                                        style = MonoTypography.terminal.copy(
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = ChampagneGoldBright,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(
                color = if (isChristmasTheme) ChampagneGold.copy(alpha = 0.22f) else OutlineSubtle
            )
            Spacer(modifier = Modifier.height(14.dp))

            DrawerSectionLabel("CUENTA")
            DrawerMenuItem(
                icon = Icons.Rounded.Person,
                title = "Mi Perfil",
                subtitle = "$userCountryFlag $userAlias • Licencia VIP",
                badgeText = effectiveTier.badgeLabel,
                badgeColor = tierColor,
                hasChristmasHat = isChristmasTheme,
                onClick = onProfileClick,
                testTag = "drawer_profile"
            )

            Spacer(modifier = Modifier.height(14.dp))
            DrawerSectionLabel("CONEXIÓN")
            DrawerMenuItem(
                icon = Icons.Rounded.Tune,
                title = "Modo VPN",
                subtitle = "Perfil de rendimiento",
                badgeText = performanceModeTitle,
                badgeColor = accentColor,
                onClick = onVpnModeClick,
                testTag = "drawer_vpn_mode"
            )
            DrawerMenuItem(
                icon = Icons.Rounded.Settings,
                title = "Configuración",
                subtitle = "DNS, red y avanzado",
                onClick = onSettingsClick,
                testTag = "drawer_settings"
            )

            Spacer(modifier = Modifier.height(14.dp))
            DrawerSectionLabel("HERRAMIENTAS")
            DrawerMenuItem(
                icon = Icons.Rounded.WifiTethering,
                title = "Hotspot",
                subtitle = "Compartir VPN por WiFi",
                onClick = onHotspotClick,
                testTag = "drawer_hotspot"
            )
            DrawerMenuItem(
                icon = Icons.Rounded.Radar,
                title = "Host Checker",
                subtitle = "Cabeceras, SNI, IP y CDN",
                onClick = onHostCheckerClick,
                testTag = "drawer_host_checker"
            )
            DrawerMenuItem(
                icon = Icons.Rounded.Palette,
                title = "Temas",
                subtitle = "Colores, fondos y editor",
                onClick = onThemesClick,
                testTag = "drawer_themes"
            )
            DrawerMenuItem(
                icon = Icons.Rounded.DashboardCustomize,
                title = "Diseño de Inicio",
                subtitle = "Esfera, botón central o clásico",
                onClick = onHomeStyleClick,
                testTag = "drawer_home_style"
            )
            DrawerMenuItem(
                icon = Icons.Rounded.Terminal,
                title = "Diagnóstico",
                subtitle = "Log de conexión en vivo",
                badgeText = "LOG",
                badgeColor = accentColor,
                onClick = onLogsClick,
                testTag = "drawer_logs"
            )

            Spacer(modifier = Modifier.height(14.dp))
            DrawerSectionLabel("ENTRETENIMIENTO")
            val footballUnlocked = effectiveTier.includesLiveFootball
            val channelsUnlocked = effectiveTier.includesChannels
            DrawerMenuItem(
                icon = Icons.Rounded.SportsSoccer,
                title = "Fútbol en Vivo",
                subtitle = if (footballUnlocked) "Plan ${effectiveTier.badgeLabel} • Partidos y canales" else "Exclusivo Plan PREMIUM o SUPER PRO",
                badgeText = if (footballUnlocked) "EN VIVO" else "PREMIUM",
                badgeColor = if (footballUnlocked) CyberEmerald else Color(PlanTier.PREMIUM.colorHex),
                onClick = onLiveMatchesClick,
                testTag = "drawer_live_matches"
            )
            DrawerMenuItem(
                icon = Icons.Rounded.LiveTv,
                title = "Series en Vivo",
                subtitle = if (channelsUnlocked) "Plan ${effectiveTier.badgeLabel} • Canales 24/7" else "Exclusivo Plan PRO o SUPER PRO",
                badgeText = if (channelsUnlocked) "EN VIVO" else "PRO",
                badgeColor = if (channelsUnlocked) CyberEmerald else Color(PlanTier.PRO.colorHex),
                onClick = onLiveSeriesClick,
                testTag = "drawer_live_series"
            )
            DrawerMenuItem(
                icon = Icons.Rounded.Movie,
                title = "Cine y Películas TV",
                subtitle = if (channelsUnlocked) "Canales HD de cine y animación" else "Exclusivo Plan PRO o SUPER PRO",
                badgeText = if (channelsUnlocked) "CANALES" else "PRO",
                badgeColor = if (channelsUnlocked) ChampagneGold else Color(PlanTier.PRO.colorHex),
                onClick = onFreeCinemaClick,
                testTag = "drawer_free_cinema"
            )

            Spacer(modifier = Modifier.height(14.dp))
            DrawerSectionLabel("COMUNIDAD Y PLANES")
            DrawerMenuItem(
                icon = Icons.Rounded.Groups,
                title = "Grupo WhatsApp 1",
                subtitle = "Comunidad oficial Jeffers VPN",
                badgeText = "UNIRSE",
                badgeColor = CyberEmerald,
                onClick = onWhatsAppGroup1Click,
                testTag = "drawer_whatsapp_group_1"
            )
            DrawerMenuItem(
                icon = Icons.Rounded.Groups,
                title = "Grupo WhatsApp 2",
                subtitle = "Grupo alternativo de soporte",
                badgeText = "UNIRSE",
                badgeColor = CyberEmerald,
                onClick = onWhatsAppGroup2Click,
                testTag = "drawer_whatsapp_group_2"
            )
            DrawerMenuItem(
                icon = Icons.Rounded.WorkspacePremium,
                title = "Planes PRO • PREMIUM • SUPER PRO",
                subtitle = "Contacto para planes: $PLANS_CONTACT_DISPLAY",
                badgeText = "PLANES",
                badgeColor = Color(PlanTier.SUPER_PRO.colorHex),
                onClick = onPlansContactClick,
                testTag = "drawer_plans_contact"
            )
        }
    }
}

@Composable
private fun DrawerSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(
            letterSpacing = 1.6.sp,
            fontWeight = FontWeight.Bold
        ),
        color = ChampagneGold.copy(alpha = 0.78f),
        modifier = Modifier.padding(bottom = 6.dp, start = 4.dp)
    )
}

@Composable
private fun DrawerMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    badgeText: String? = null,
    badgeColor: Color = MaterialTheme.colorScheme.secondary,
    hasChristmasHat: Boolean = false,
    onClick: () -> Unit,
    testTag: String
) {
    val iconTint = if (badgeText != null) badgeColor else MaterialTheme.colorScheme.primary.copy(alpha = 0.92f)

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0x550C1522),
        border = BorderStroke(0.8.dp, iconTint.copy(alpha = 0.22f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(
                        top = if (hasChristmasHat) 5.dp else 0.dp,
                        start = if (hasChristmasHat) 3.dp else 0.dp
                    )
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = iconTint.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, iconTint.copy(alpha = 0.34f)),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = iconTint,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    if (hasChristmasHat) {
                        LuxuryChristmasHat(
                            baseTiltDegrees = -16f,
                            animated = false,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .offset(x = (-6).dp, y = (-8).dp)
                                .size(22.dp)
                                .testTag("drawer_profile_christmas_hat")
                        )
                    }
                }
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
            }
            if (badgeText != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = badgeColor.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.42f))
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = badgeColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}
