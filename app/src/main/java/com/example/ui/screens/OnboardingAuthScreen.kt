package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.BackgroundEffect
import com.example.data.model.CountryRegion
import com.example.data.model.PlanTier
import com.example.ui.components.BackgroundEffectLayer
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.ChampagneGoldBright
import com.example.ui.theme.CyberEmerald
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.FrostIce
import com.example.ui.theme.MonoTypography
import com.example.ui.theme.ObsidianGlass
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.OutlineSubtle
import com.example.ui.theme.TextMuted

private enum class OnboardingStep {
    TERMS,
    COUNTRY,
    ACCOUNT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingAuthScreen(
    initialTermsAccepted: Boolean,
    initialCountry: CountryRegion,
    initialAlias: String,
    currentDeviceId: String,
    authFeedback: Pair<Boolean, String>?,
    onClearAuthFeedback: () -> Unit,
    onCountrySelected: (CountryRegion) -> Unit,
    onRegisterAccount: (alias: String, country: CountryRegion, activationCode: String?) -> Unit,
    onSignInPreviousDevice: (previousDeviceId: String, alias: String?, country: CountryRegion, activationCode: String?) -> Unit,
    onSignInPersonalClient: (username: String, secret: String?, country: CountryRegion) -> Unit,
    linkedGoogleEmail: String? = null,
    isLinkingGoogle: Boolean = false,
    googleLinkError: String? = null,
    onLinkGoogleClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var step by remember {
        mutableStateOf(if (initialTermsAccepted) OnboardingStep.ACCOUNT else OnboardingStep.TERMS)
    }
    var termsChecked by remember { mutableStateOf(initialTermsAccepted) }
    var selectedCountry by remember { mutableStateOf(initialCountry) }
    var isSignInMode by remember { mutableStateOf(false) }

    var displayName by remember {
        mutableStateOf(if (initialAlias == "jeffers_user") "" else initialAlias)
    }
    var previousDeviceId by remember { mutableStateOf("") }
    var signInCode by remember { mutableStateOf("") }
    var signInUsePersonalUser by remember { mutableStateOf(true) }
    var personalUsername by remember { mutableStateOf("") }
    var personalSecret by remember { mutableStateOf("") }
    var showCountryPicker by remember { mutableStateOf(false) }
    var showSubscriptionSheet by remember { mutableStateOf(false) }
    var sheetCodeInput by remember { mutableStateOf("") }

    val primaryColor = MaterialTheme.colorScheme.primary
    val accentColor = MaterialTheme.colorScheme.secondary

    BackHandler(enabled = step != OnboardingStep.TERMS || isSignInMode) {
        onClearAuthFeedback()
        if (step == OnboardingStep.ACCOUNT) {
            if (isSignInMode) {
                isSignInMode = false
            } else {
                step = OnboardingStep.COUNTRY
            }
        } else if (step == OnboardingStep.COUNTRY) {
            step = OnboardingStep.TERMS
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        BackgroundEffectLayer(
            effect = BackgroundEffect.CHRISTMAS_SNOW,
            primaryColor = primaryColor,
            accentColor = accentColor
        )

        AnimatedContent(
            targetState = step,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(180)) },
            label = "onboardingStep"
        ) { currentStep ->
            when (currentStep) {
                OnboardingStep.TERMS -> TermsStepContent(
                    termsChecked = termsChecked,
                    primaryColor = primaryColor,
                    accentColor = accentColor,
                    onToggleChecked = { termsChecked = it },
                    onContinue = {
                        onClearAuthFeedback()
                        step = OnboardingStep.COUNTRY
                    }
                )

                OnboardingStep.COUNTRY -> CountryStepContent(
                    selectedCountry = selectedCountry,
                    primaryColor = primaryColor,
                    accentColor = accentColor,
                    onBack = { step = OnboardingStep.TERMS },
                    onContinue = {
                        onCountrySelected(selectedCountry)
                        step = OnboardingStep.ACCOUNT
                    },
                    onChooseOther = { showCountryPicker = true }
                )

                OnboardingStep.ACCOUNT -> AccountStepContent(
                    isSignInMode = isSignInMode,
                    signInUsePersonalUser = signInUsePersonalUser,
                    selectedCountry = selectedCountry,
                    displayName = displayName,
                    currentDeviceId = currentDeviceId,
                    previousDeviceId = previousDeviceId,
                    signInCode = signInCode,
                    personalUsername = personalUsername,
                    personalSecret = personalSecret,
                    authFeedback = authFeedback,
                    primaryColor = primaryColor,
                    accentColor = accentColor,
                    onBack = {
                        onClearAuthFeedback()
                        if (isSignInMode) {
                            isSignInMode = false
                        } else {
                            step = OnboardingStep.COUNTRY
                        }
                    },
                    onChangeCountryClick = { showCountryPicker = true },
                    onDisplayNameChange = {
                        displayName = it
                        onClearAuthFeedback()
                    },
                    onPreviousDeviceIdChange = {
                        previousDeviceId = it
                        onClearAuthFeedback()
                    },
                    onSignInCodeChange = {
                        signInCode = it
                        onClearAuthFeedback()
                    },
                    onPersonalUsernameChange = {
                        personalUsername = it
                        onClearAuthFeedback()
                    },
                    onPersonalSecretChange = {
                        personalSecret = it
                        onClearAuthFeedback()
                    },
                    onSwitchSignInMethod = { usePersonal ->
                        onClearAuthFeedback()
                        signInUsePersonalUser = usePersonal
                    },
                    onToggleMode = {
                        onClearAuthFeedback()
                        isSignInMode = !isSignInMode
                    },
                    onOpenSubscriptionSheet = {
                        onClearAuthFeedback()
                        showSubscriptionSheet = true
                    },
                    onSubmitSignIn = {
                        if (signInUsePersonalUser) {
                            onSignInPersonalClient(
                                personalUsername,
                                personalSecret.ifBlank { null },
                                selectedCountry
                            )
                        } else {
                            onSignInPreviousDevice(
                                previousDeviceId,
                                displayName.ifBlank { null },
                                selectedCountry,
                                signInCode.ifBlank { null }
                            )
                        }
                    },
                    linkedGoogleEmail = linkedGoogleEmail,
                    isLinkingGoogle = isLinkingGoogle,
                    googleLinkError = googleLinkError,
                    onLinkGoogleClick = onLinkGoogleClick
                )
            }
        }
    }

    if (showCountryPicker) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showCountryPicker = false },
            sheetState = sheetState,
            containerColor = Color(0xFF071711)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Selecciona tu país",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = ChampagneGoldBright
                )
                Text(
                    text = "Usaremos esta región para mostrar tus servidores principales.",
                    style = MaterialTheme.typography.bodySmall,
                    color = FrostIce.copy(alpha = 0.72f)
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    contentPadding = PaddingValues(vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(CountryRegion.entries, key = { it.name }) { region ->
                        val isSelected = region == selectedCountry
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) ChampagneGold.copy(alpha = 0.16f) else Color(0xCC06130E),
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) ChampagneGoldBright else ChampagneGold.copy(alpha = 0.25f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    selectedCountry = region
                                    onCountrySelected(region)
                                    showCountryPicker = false
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 13.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(text = region.flagEmoji, fontSize = 22.sp)
                                    Text(
                                        text = region.titleName,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = if (isSelected) ChampagneGoldBright else FrostIce
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = "Seleccionado",
                                        tint = ChampagneGoldBright,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    if (showSubscriptionSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showSubscriptionSheet = false },
            sheetState = sheetState,
            containerColor = Color(0xFF071711)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Activar Acceso Jeffers VPN",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = ChampagneGoldBright
                )
                Text(
                    text = "Elige comenzar gratis con el Plan FREE o ingresa un código de suscripción (PRO, VIP, PREMIUM o SUPER PRO).",
                    style = MaterialTheme.typography.bodySmall,
                    color = FrostIce.copy(alpha = 0.76f)
                )

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xCC06130E),
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.62f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Rounded.Shield, contentDescription = null, tint = accentColor)
                                Text(
                                    text = PlanTier.FREE.displayName,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = FrostIce
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = accentColor.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    text = "GRATIS",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = accentColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Text(
                            text = "Acceso inmediato a servidores gratuitos (${selectedCountry.titleName} y General) con BHTTP y 4 compañías por país.",
                            style = MaterialTheme.typography.bodySmall,
                            color = FrostIce.copy(alpha = 0.74f)
                        )
                        Button(
                            onClick = {
                                showSubscriptionSheet = false
                                onRegisterAccount(displayName, selectedCountry, null)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accentColor,
                                contentColor = ObsidianVoid
                            ),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("continue_free_plan_button")
                        ) {
                            Text("CONTINUAR CON PLAN FREE", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xCC06130E),
                    border = BorderStroke(1.dp, ChampagneGold.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.QrCode2, contentDescription = null, tint = ChampagneGoldBright)
                            Text(
                                text = "¿Tienes un código PRO / VIP / PREMIUM / SUPER PRO?",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = FrostIce
                            )
                        }
                        OutlinedTextField(
                            value = sheetCodeInput,
                            onValueChange = {
                                sheetCodeInput = it
                                onClearAuthFeedback()
                            },
                            label = { Text("Código de suscripción") },
                            placeholder = { Text("JFF-PRM-20261231-ALL-XXXX") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ChampagneGoldBright,
                                unfocusedBorderColor = ChampagneGold.copy(alpha = 0.35f),
                                focusedContainerColor = Color(0xCC030B08),
                                unfocusedContainerColor = Color(0xCC030B08),
                                focusedTextColor = FrostIce,
                                unfocusedTextColor = FrostIce
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (authFeedback != null) {
                            val (ok, msg) = authFeedback
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (ok) accentColor else MaterialTheme.colorScheme.error
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                onRegisterAccount(displayName, selectedCountry, sheetCodeInput)
                            },
                            enabled = sheetCodeInput.isNotBlank(),
                            border = BorderStroke(1.dp, primaryColor),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("activate_code_register_button")
                        ) {
                            Text("ACTIVAR CÓDIGO Y ENTRAR", color = primaryColor, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
            }
        }
    }
}

@Composable
private fun TermsStepContent(
    termsChecked: Boolean,
    primaryColor: Color,
    accentColor: Color,
    onToggleChecked: (Boolean) -> Unit,
    onContinue: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 520.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(28.dp))

            JeffersOrbLogo(primaryColor = primaryColor, accentColor = accentColor)

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "JEFFERS VPN PRO",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                ),
                color = FrostIce
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "✦ EDICIÓN NAVIDAD PRO • Términos y Condiciones",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = ChampagneGoldBright
            )

            Spacer(modifier = Modifier.height(22.dp))

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = ObsidianGlass,
                border = BorderStroke(1.dp, ChampagneGold.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "TÉRMINOS Y CONDICIONES DE USO — JEFFERS VPN",
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = Color(0xFFCBD5E1),
                            letterSpacing = 0.4.sp
                        )
                    )
                    TermClause(
                        title = "1. ACEPTACIÓN",
                        body = "Al usar Jeffers VPN aceptas cumplir estos términos y condiciones del servicio."
                    )
                    TermClause(
                        title = "2. USO PERMITIDO",
                        body = "Jeffers VPN es exclusivamente para uso personal y legal. Queda prohibido usar el servicio para actividades ilegales."
                    )
                    TermClause(
                        title = "3. CUENTA Y SUSCRIPCIONES",
                        body = "Cada usuario es responsable de sus credenciales, Device ID y códigos de suscripción."
                    )
                    TermClause(
                        title = "4. MODALIDADES DE SERVICIO",
                        body = "Free: Acceso básico. Pro: Modo avanzado. VIP y Premium: Mayor velocidad, prioridad y soporte."
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = ObsidianGlass,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (termsChecked) accentColor.copy(alpha = 0.72f) else ChampagneGold.copy(alpha = 0.30f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onToggleChecked(!termsChecked) }
                    .testTag("terms_checkbox_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Checkbox(
                        checked = termsChecked,
                        onCheckedChange = onToggleChecked,
                        colors = CheckboxDefaults.colors(
                            checkedColor = accentColor,
                            uncheckedColor = ChampagneGold.copy(alpha = 0.70f),
                            checkmarkColor = ObsidianVoid
                        )
                    )
                    Text(
                        text = "He leído y acepto los Términos y Condiciones de Uso",
                        style = MaterialTheme.typography.bodyMedium,
                        color = FrostIce
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = onContinue,
                enabled = termsChecked,
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor,
                    contentColor = ObsidianVoid,
                    disabledContainerColor = Color(0xFF1C2733),
                    disabledContentColor = Color(0xFF6B7D8F)
                ),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("terms_continue_button")
            ) {
                Text(
                    text = "CONTINUAR",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun CountryStepContent(
    selectedCountry: CountryRegion,
    primaryColor: Color,
    accentColor: Color,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onChooseOther: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        val isCompactHeight = maxHeight < 560.dp
        val contentModifier = if (isCompactHeight) {
            Modifier
                .fillMaxSize()
                .widthIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
        } else {
            Modifier
                .fillMaxSize()
                .widthIn(max = 520.dp)
        }

        Column(
            modifier = contentModifier,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Volver a términos",
                        tint = primaryColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            JeffersOrbLogo(primaryColor = primaryColor, accentColor = accentColor)

            if (isCompactHeight) {
                Spacer(modifier = Modifier.height(24.dp))
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "¿Desde dónde te conectas?",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                Text(
                    text = "Detectamos tu país automáticamente — puedes cambiarlo si no es correcto.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF8899AA)
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = ObsidianGlass,
                border = BorderStroke(1.dp, ChampagneGold.copy(alpha = 0.38f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = selectedCountry.flagEmoji,
                        fontSize = 42.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Detectamos que estás en",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF8899AA)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = selectedCountry.titleName,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(22.dp))
                    Button(
                        onClick = onContinue,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accentColor,
                            contentColor = Color(0xFF041008)
                        ),
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("country_continue_button")
                    ) {
                        Text(
                            text = "CONTINUAR",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            TextButton(
                onClick = onChooseOther,
                modifier = Modifier
                    .height(48.dp)
                    .testTag("choose_other_country_button")
            ) {
                Text(
                    text = "Elegir otro país",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = primaryColor
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

@Composable
private fun AccountStepContent(
    isSignInMode: Boolean,
    signInUsePersonalUser: Boolean,
    selectedCountry: CountryRegion,
    displayName: String,
    currentDeviceId: String,
    previousDeviceId: String,
    signInCode: String,
    personalUsername: String,
    personalSecret: String,
    authFeedback: Pair<Boolean, String>?,
    primaryColor: Color,
    accentColor: Color,
    onBack: () -> Unit,
    onChangeCountryClick: () -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onPreviousDeviceIdChange: (String) -> Unit,
    onSignInCodeChange: (String) -> Unit,
    onPersonalUsernameChange: (String) -> Unit,
    onPersonalSecretChange: (String) -> Unit,
    onSwitchSignInMethod: (Boolean) -> Unit,
    onToggleMode: () -> Unit,
    onOpenSubscriptionSheet: () -> Unit,
    onSubmitSignIn: () -> Unit,
    linkedGoogleEmail: String?,
    isLinkingGoogle: Boolean,
    googleLinkError: String?,
    onLinkGoogleClick: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val trimmedAlias = displayName.trim()
    val isAliasValid = trimmedAlias.length >= 6 && trimmedAlias.any { it.isDigit() }
    val isSignInValid = if (signInUsePersonalUser) {
        personalUsername.trim().length >= 3
    } else {
        previousDeviceId.trim().length >= 6
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 520.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Volver",
                        tint = primaryColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            JeffersOrbLogo(primaryColor = primaryColor, accentColor = accentColor)

            Spacer(modifier = Modifier.height(22.dp))

            Text(
                text = if (isSignInMode) "Iniciar sesión" else "Crear cuenta",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isSignInMode) {
                    if (signInUsePersonalUser) {
                        "Acceso para clientes personales con usuario"
                    } else {
                        "Vincula tu dispositivo anterior o código de plan"
                    }
                } else {
                    "Registro rápido — sin contraseña"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF8899AA)
            )

            Spacer(modifier = Modifier.height(22.dp))

            // Country selector pill card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = ObsidianGlass,
                border = BorderStroke(1.dp, ChampagneGold.copy(alpha = 0.36f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onChangeCountryClick)
                    .testTag("account_country_selector")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(text = selectedCountry.flagEmoji, fontSize = 20.sp)
                        Text(
                            text = selectedCountry.titleName,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                            color = FrostIce
                        )
                    }
                    Text(
                        text = "Cambiar",
                        style = MaterialTheme.typography.labelLarge,
                        color = ChampagneGoldBright
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (!isSignInMode) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = onDisplayNameChange,
                    placeholder = { Text("Nombre para mostrar") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ChampagneGoldBright,
                        unfocusedBorderColor = ChampagneGold.copy(alpha = 0.42f),
                        focusedContainerColor = Color(0xCC030B08),
                        unfocusedContainerColor = Color(0xCC030B08),
                        focusedTextColor = FrostIce,
                        unfocusedTextColor = FrostIce,
                        focusedPlaceholderColor = FrostIce.copy(alpha = 0.55f),
                        unfocusedPlaceholderColor = FrostIce.copy(alpha = 0.55f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("register_display_name_input")
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isAliasValid) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Text(
                        text = "Mínimo 6 caracteres, incluye un número",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isAliasValid) accentColor else FrostIce.copy(alpha = 0.65f)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = ObsidianGlass,
                    border = BorderStroke(1.dp, ChampagneGold.copy(alpha = 0.34f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Device ID (para soporte)",
                                style = MaterialTheme.typography.bodySmall,
                                color = ChampagneGold.copy(alpha = 0.85f)
                            )
                            Text(
                                text = currentDeviceId,
                                style = MonoTypography.address.copy(fontSize = 14.sp),
                                color = FrostIce
                            )
                        }
                        IconButton(
                            onClick = { clipboard.setText(AnnotatedString(currentDeviceId)) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = "Copiar Device ID",
                                tint = ChampagneGoldBright,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val userActive = signInUsePersonalUser
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (userActive) ChampagneGold.copy(alpha = 0.18f) else ObsidianGlass,
                        border = BorderStroke(
                            width = if (userActive) 1.5.dp else 1.dp,
                            color = if (userActive) ChampagneGoldBright else ChampagneGold.copy(alpha = 0.28f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onSwitchSignInMethod(true) }
                            .testTag("signin_method_personal_user")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "Usuario Personal",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = if (userActive) ChampagneGoldBright else FrostIce.copy(alpha = 0.72f)
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (!userActive) ChampagneGold.copy(alpha = 0.18f) else ObsidianGlass,
                        border = BorderStroke(
                            width = if (!userActive) 1.5.dp else 1.dp,
                            color = if (!userActive) ChampagneGoldBright else ChampagneGold.copy(alpha = 0.28f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onSwitchSignInMethod(false) }
                            .testTag("signin_method_device_id")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "Device ID",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = if (!userActive) ChampagneGoldBright else FrostIce.copy(alpha = 0.72f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (signInUsePersonalUser) {
                    OutlinedTextField(
                        value = personalUsername,
                        onValueChange = onPersonalUsernameChange,
                        label = { Text("Usuario personal") },
                        placeholder = { Text("Escribe tu usuario asignado") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ChampagneGoldBright,
                            unfocusedBorderColor = ChampagneGold.copy(alpha = 0.42f),
                            focusedContainerColor = Color(0xCC030B08),
                            unfocusedContainerColor = Color(0xCC030B08),
                            focusedTextColor = FrostIce,
                            unfocusedTextColor = FrostIce
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("signin_personal_username_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = personalSecret,
                        onValueChange = onPersonalSecretChange,
                        label = { Text("Contraseña o código (si aplica)") },
                        placeholder = { Text("Opcional si el usuario es de acceso directo") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ChampagneGoldBright,
                            unfocusedBorderColor = ChampagneGold.copy(alpha = 0.28f),
                            focusedContainerColor = Color(0xCC030B08),
                            unfocusedContainerColor = Color(0xCC030B08),
                            focusedTextColor = FrostIce,
                            unfocusedTextColor = FrostIce
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("signin_personal_secret_input")
                    )
                } else {
                    OutlinedTextField(
                        value = previousDeviceId,
                        onValueChange = onPreviousDeviceIdChange,
                        label = { Text("Device ID anterior") },
                        placeholder = { Text("Ej: 0922cd8ca3dcfb39") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ChampagneGoldBright,
                            unfocusedBorderColor = ChampagneGold.copy(alpha = 0.42f),
                            focusedContainerColor = Color(0xCC030B08),
                            unfocusedContainerColor = Color(0xCC030B08),
                            focusedTextColor = FrostIce,
                            unfocusedTextColor = FrostIce
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("signin_device_id_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = displayName,
                        onValueChange = onDisplayNameChange,
                        label = { Text("Nombre para mostrar (opcional)") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ChampagneGoldBright,
                            unfocusedBorderColor = ChampagneGold.copy(alpha = 0.28f),
                            focusedContainerColor = Color(0xCC030B08),
                            unfocusedContainerColor = Color(0xCC030B08),
                            focusedTextColor = FrostIce,
                            unfocusedTextColor = FrostIce
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = signInCode,
                        onValueChange = onSignInCodeChange,
                        label = { Text("Código de plan (opcional)") },
                        placeholder = { Text("JFF-VIP-20261231-ALL-XXXX") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ChampagneGoldBright,
                            unfocusedBorderColor = ChampagneGold.copy(alpha = 0.28f),
                            focusedContainerColor = Color(0xCC030B08),
                            unfocusedContainerColor = Color(0xCC030B08),
                            focusedTextColor = FrostIce,
                            unfocusedTextColor = FrostIce
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            AnimatedVisibility(visible = authFeedback != null) {
                if (authFeedback != null) {
                    val (ok, msg) = authFeedback
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (ok) accentColor else MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onToggleMode)
                    .testTag("toggle_auth_mode_link"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isSignInMode) {
                        buildAnnotatedString {
                            append("¿No tienes cuenta? ")
                            withStyle(SpanStyle(color = primaryColor, fontWeight = FontWeight.Bold)) {
                                append("Crear cuenta nueva")
                            }
                        }
                    } else {
                        buildAnnotatedString {
                            append("¿Ya tenías una cuenta? ")
                            withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Bold)) {
                                append("Vincula tu dispositivo anterior")
                            }
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFCBD5E1),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            val buttonReady = if (isSignInMode) isSignInValid else isAliasValid
            val buttonBrush = if (buttonReady) {
                Brush.horizontalGradient(listOf(accentColor, primaryColor))
            } else {
                Brush.horizontalGradient(listOf(Color(0xFF3E4A56), Color(0xFF7B8591)))
            }

            Button(
                onClick = {
                    if (isSignInMode) onSubmitSignIn() else onOpenSubscriptionSheet()
                },
                enabled = buttonReady,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent
                ),
                contentPadding = PaddingValues(),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(buttonBrush)
                    .testTag("auth_primary_button")
            ) {
                Text(
                    text = if (isSignInMode) "INICIAR SESIÓN" else "OBTENER SUSCRIPCIÓN",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = if (buttonReady) ObsidianVoid else Color.White.copy(alpha = 0.9f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            GoogleLinkFooterCard(
                linkedEmail = linkedGoogleEmail,
                isLinking = isLinkingGoogle,
                errorMessage = googleLinkError,
                accentColor = accentColor,
                onLinkClick = onLinkGoogleClick
            )

            Spacer(modifier = Modifier.height(18.dp))
        }
    }
}

@Composable
private fun GoogleLinkFooterCard(
    linkedEmail: String?,
    isLinking: Boolean,
    errorMessage: String?,
    accentColor: Color,
    onLinkClick: () -> Unit
) {
    val isLinked = !linkedEmail.isNullOrBlank()
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = ObsidianGlass,
        border = BorderStroke(
            width = 1.dp,
            color = if (isLinked) CyberEmerald.copy(alpha = 0.60f) else ChampagneGold.copy(alpha = 0.36f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = CircleShape,
                    color = (if (isLinked) CyberEmerald else ChampagneGoldBright).copy(alpha = 0.16f),
                    border = BorderStroke(
                        1.dp,
                        (if (isLinked) CyberEmerald else ChampagneGoldBright).copy(alpha = 0.50f)
                    ),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isLinked) Icons.Rounded.CloudDone else Icons.Rounded.CloudSync,
                            contentDescription = null,
                            tint = if (isLinked) CyberEmerald else ChampagneGoldBright,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = if (isLinked) "Cuenta Google vinculada" else "Vincular con Google (Nube)",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (isLinked) CyberEmerald else ChampagneGoldBright
                    )
                    Text(
                        text = if (isLinked) {
                            "$linkedEmail • Planes por ID listos en tiempo real."
                        } else {
                            "Vincula tu cuenta Google aquí abajo para recibir planes activados por ID al instante."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = FrostIce.copy(alpha = 0.76f)
                    )
                }
            }

            if (!errorMessage.isNullOrBlank()) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            OutlinedButton(
                onClick = onLinkClick,
                enabled = !isLinking,
                border = BorderStroke(
                    1.dp,
                    if (isLinked) CyberEmerald.copy(alpha = 0.65f) else ChampagneGoldBright.copy(alpha = 0.72f)
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("onboarding_link_google_button")
            ) {
                if (isLinking) {
                    CircularProgressIndicator(
                        color = ChampagneGoldBright,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Vinculando con Google…", color = ChampagneGoldBright, fontWeight = FontWeight.SemiBold)
                } else {
                    Icon(
                        imageVector = Icons.Rounded.AccountCircle,
                        contentDescription = null,
                        tint = if (isLinked) CyberEmerald else ChampagneGoldBright,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isLinked) "Cambiar cuenta Google vinculada" else "Vincular cuenta Google",
                        color = if (isLinked) accentColor else ChampagneGoldBright,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun TermClause(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = Color(0xFFDDE6EE)
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF8C9EAE)
        )
    }
}

@Composable
private fun JeffersOrbLogo(
    primaryColor: Color,
    accentColor: Color
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orbLogoAnim")
    val orbitAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbitAngle"
    )

    Box(
        modifier = Modifier.size(116.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.28f),
                        accentColor.copy(alpha = 0.08f),
                        Color.Transparent
                    )
                ),
                radius = size.minDimension / 2f
            )

            val ringMargin = 20.dp.toPx()
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(Color.Transparent, ChampagneGoldBright.copy(alpha = 0.85f), accentColor.copy(alpha = 0.72f), Color.Transparent)
                ),
                startAngle = orbitAngle,
                sweepAngle = 155f,
                useCenter = false,
                topLeft = Offset(ringMargin, ringMargin),
                size = Size(size.width - ringMargin * 2, size.height - ringMargin * 2),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                color = ChampagneGold.copy(alpha = 0.35f),
                startAngle = -orbitAngle * 0.7f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(ringMargin - 5.dp.toPx(), ringMargin - 5.dp.toPx()),
                size = Size(size.width - (ringMargin - 5.dp.toPx()) * 2, size.height - (ringMargin - 5.dp.toPx()) * 2),
                style = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        Image(
            painter = painterResource(id = R.drawable.jeffers_vpn_icon),
            contentDescription = "Jeffers VPN PRO",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(18.dp))
                .border(
                    width = 1.6.dp,
                    brush = Brush.linearGradient(
                        listOf(ChampagneGoldBright.copy(alpha = 0.90f), accentColor.copy(alpha = 0.65f))
                    ),
                    shape = RoundedCornerShape(18.dp)
                )
        )
    }
}
