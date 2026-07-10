package com.example

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import kotlinx.coroutines.delay
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.CoachUiState
import com.example.viewmodel.RescueLog
import com.example.viewmodel.TouchLifeViewModel
import java.util.Locale
import kotlin.math.sin

enum class TouchLifeScreen {
    SPLASH,
    ONBOARDING,
    MAIN
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var isDarkTheme by remember { mutableStateOf(true) }
            var registeredName by remember { mutableStateOf("") }
            var registeredPhone by remember { mutableStateOf("") }
            var registeredEmail by remember { mutableStateOf("") }
            var registeredRole by remember { mutableStateOf("Citizen/User") }
            var registeredPassword by remember { mutableStateOf("") }

            MyApplicationTheme(darkTheme = isDarkTheme) {
                var currentScreen by remember { mutableStateOf(TouchLifeScreen.SPLASH) }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    when (currentScreen) {
                        TouchLifeScreen.SPLASH -> {
                            TouchLifeSplashScreen(
                                onDismiss = { currentScreen = TouchLifeScreen.ONBOARDING }
                            )
                        }
                        TouchLifeScreen.ONBOARDING -> {
                            TouchLifeOnboardingScreen(
                                onSkip = {
                                    registeredRole = "Guest Bystander"
                                    currentScreen = TouchLifeScreen.MAIN
                                },
                                onAuthenticate = { name, phone, email, password, role ->
                                    registeredName = name
                                    registeredPhone = phone
                                    registeredEmail = email
                                    registeredPassword = password
                                    registeredRole = role
                                    currentScreen = TouchLifeScreen.MAIN
                                }
                            )
                        }
                        TouchLifeScreen.MAIN -> {
                            TouchLifeEmergencyScreen(
                                isDarkTheme = isDarkTheme,
                                onThemeToggle = { isDarkTheme = !isDarkTheme },
                                userName = registeredName,
                                userPhone = registeredPhone,
                                userEmail = registeredEmail,
                                userPassword = registeredPassword,
                                userRole = registeredRole,
                                onUpdateEmail = { registeredEmail = it },
                                onUpdatePassword = { registeredPassword = it },
                                onLogout = {
                                    currentScreen = TouchLifeScreen.ONBOARDING
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

enum class MainTab {
    HOME,
    BOOKINGS,
    AI_COACH,
    PROFILE
}

@Composable
fun TouchLifeEmergencyScreen(
    modifier: Modifier = Modifier,
    viewModel: TouchLifeViewModel = viewModel(),
    isDarkTheme: Boolean,
    onThemeToggle: () -> Unit,
    userName: String,
    userPhone: String,
    userEmail: String,
    userPassword: String,
    userRole: String,
    onUpdateEmail: (String) -> Unit,
    onUpdatePassword: (String) -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    var activeTab by remember { mutableStateOf(MainTab.HOME) }
    var warningsExpanded by remember { mutableStateOf(false) }

    // Observe state from ViewModel
    val scenario by viewModel.scenario.collectAsState()
    val victimStatus by viewModel.victimStatus.collectAsState()
    val bystanderInput by viewModel.bystanderInput.collectAsState()
    val sessionLogs by viewModel.sessionLogs.collectAsState()
    val currentInstruction by viewModel.currentInstruction.collectAsState()
    val nextRecommendedAction by viewModel.nextRecommendedAction.collectAsState()
    val isWarningTriggered by viewModel.isWarningTriggered.collectAsState()
    val isSpeaking by viewModel.isSpeaking.collectAsState()
    val isMuted by viewModel.isMuted.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    // Live Booking Simulated coordinates/tracker
    val responderDistance by viewModel.responderDistance.collectAsState()
    val responderMinutes by viewModel.responderMinutes.collectAsState()
    val responderStatus by viewModel.responderStatus.collectAsState()
    val responderName by viewModel.responderName.collectAsState()

    // Activity launcher for Google's Speech-To-Text API
    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = results?.firstOrNull() ?: ""
            if (spokenText.isNotEmpty()) {
                viewModel.submitBystanderUpdate(spokenText)
                Toast.makeText(context, "Voice registered: \"$spokenText\"", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Launch speech trigger intent helper
    val triggerVoiceInput = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Tell the coach: Is the victim awake? Are they bleeding? Is the area safe?")
        }
        try {
            speechRecognizerLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Hands-free voice recognition not supported. Please type update.", Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            BottomNavigationBar(activeTab = activeTab, onTabSelect = { activeTab = it })
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (activeTab) {
                MainTab.HOME -> {
                    // Scroll container
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 1. BRAND HERO HEADER & SCREEN RE-ADER MODE
                        item {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("header_row")
                                    .semantics { heading() }, // Group as heading for screen readers
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Circular App Logo with modern glow/accent
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF1E2130))
                                            .border(2.dp, Color(0xFFFFB300), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Image(
                                            painter = painterResource(id = R.drawable.img_touchlife_logo),
                                            contentDescription = "TouchLife emergency companion logo showing green hand holding a warm gold heart",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "TouchLife",
                                            style = MaterialTheme.typography.titleLarge.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                letterSpacing = 0.5.sp
                                            )
                                        )
                                        Text(
                                            text = "NIGERIA'S FIRST AID & RESCUE NETWORK",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = 8.sp,
                                                color = Color(0xFF9E9E9E),
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        )
                                    }
                                }

                                // Pulsing GPS Status Badge
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .background(Color(0xFF1E2130), RoundedCornerShape(12.dp))
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                        .semantics(mergeDescendants = true) {
                                            contentDescription = "GPS and decentralised local rescue network status is active"
                                        }
                                ) {
                                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                                    val pulseAlpha by infiniteTransition.animateFloat(
                                        initialValue = 0.4f,
                                        targetValue = 1.0f,
                                        animationSpec = infiniteRepeatable(
                                            animation = tween(1000, easing = LinearEasing),
                                            repeatMode = RepeatMode.Reverse
                                        ),
                                        label = "alpha"
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF4CAF50).copy(alpha = pulseAlpha))
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "GPS ACTIVE",
                                        fontSize = 9.sp,
                                        color = Color(0xFF4CAF50),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // 2. COMPACT, ACCESSIBLE EMERGENCY RESPONDER HUD
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("responder_card")
                                    .semantics(mergeDescendants = true) {
                                        contentDescription = "Active responder status card. Responder is $responderName. Current status: $responderStatus. Distance: $responderDistance kilometers. Estimated time of arrival: $responderMinutes minutes."
                                    },
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2130)),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, Color(0xFF2E3B4E))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        // Circular Avatar placeholder representing emergency doctor
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF2E3B4E)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = if (responderName.isNotEmpty()) responderName.first().toString() else "D",
                                                style = MaterialTheme.typography.titleMedium.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFFFFB300)
                                                )
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column {
                                            Text(
                                                text = responderName,
                                                style = MaterialTheme.typography.titleMedium.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White,
                                                    fontSize = 14.sp
                                                )
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "Responder • $responderStatus • ${responderDistance}km",
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    color = Color(0xFF9E9E9E),
                                                    fontSize = 11.sp
                                                )
                                            )
                                        }
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // Quick Call Button with large Touch Target
                                        IconButton(
                                            onClick = {
                                                Toast.makeText(context, "Direct dialer initiated: calling $responderName...", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF4CAF50)),
                                            colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Phone,
                                                contentDescription = "Call Emergency Responder $responderName immediately",
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        // Quick Share Button
                                        IconButton(
                                            onClick = {
                                                Toast.makeText(context, "Lagos emergency coordinates updated with responder.", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF2E3B4E)),
                                            colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Share,
                                                contentDescription = "Share precise real-time rescue coordinates with responder",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 3. COLLAPSIBLE CRITICAL FIRST-AID SAFETY RULES (Accordion)
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("safety_accordion_card")
                                    .clickable(
                                        onClickLabel = if (warningsExpanded) "Collapse critical safety guidelines" else "Expand critical safety guidelines"
                                    ) {
                                        warningsExpanded = !warningsExpanded
                                    },
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFB71C1C).copy(alpha = 0.08f)),
                                border = BorderStroke(1.dp, Color(0xFFB71C1C).copy(alpha = 0.3f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Warning,
                                                contentDescription = null, // Screen reader reads explicit header content description instead
                                                tint = Color(0xFFE53935),
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "🚨 URGENT FIRST-AID SAFETY RULES",
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFFE53935),
                                                    letterSpacing = 0.5.sp
                                                )
                                            )
                                        }
                                        Icon(
                                            imageVector = if (warningsExpanded) Icons.Default.Favorite else Icons.AutoMirrored.Filled.ArrowForward, // Represent open/close elegantly using available icons
                                            contentDescription = if (warningsExpanded) "Guidelines expanded" else "Guidelines collapsed",
                                            tint = Color(0xFF9E9E9E),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    AnimatedVisibility(visible = warningsExpanded) {
                                        Column(
                                            modifier = Modifier.padding(top = 10.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Column {
                                                Text(
                                                    text = "1. Secure the Scene First",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                                Text(
                                                    text = "Do not approach the victim if oncoming traffic, fire, or hazards exist. Keep yourself safe.",
                                                    fontSize = 11.sp,
                                                    color = Color(0xFFD1D1D6)
                                                )
                                            }
                                            HorizontalDivider(color = Color(0xFFB71C1C).copy(alpha = 0.2f))
                                            Column {
                                                Text(
                                                    text = "2. Spinal Crash Safety",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                                Text(
                                                    text = "If victim fell from a motorcycle (Okada) or height, keep head/neck completely still unless in immediate danger.",
                                                    fontSize = 11.sp,
                                                    color = Color(0xFFD1D1D6)
                                                )
                                            }
                                        }
                                    }
                                    if (!warningsExpanded) {
                                        Text(
                                            text = "Tap to show secure scene and spinal safety guidelines.",
                                            fontSize = 10.sp,
                                            color = Color(0xFF9E9E9E),
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // 4. ACTIVE VOCAL COACH DIRECTIVES CONSOLE
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("coach_console_card")
                                    .border(2.dp, if (isWarningTriggered) Color(0xFFE53935) else Color(0xFF4CAF50), RoundedCornerShape(16.dp)),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2130)),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    // Header Status
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.semantics(mergeDescendants = true) {}
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isSpeaking) Color(0xFFFFB300) else Color(0xFF4CAF50))
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = if (isSpeaking) "COACH IS SPEAKING..." else "COACH IS LISTENING",
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSpeaking) Color(0xFFFFB300) else Color(0xFF4CAF50)
                                                )
                                            )
                                        }

                                        // Next Recommended Action Badge
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    if (isWarningTriggered) Color(0xFFE53935).copy(alpha = 0.2f) else Color(0xFF4CAF50).copy(alpha = 0.2f),
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = nextRecommendedAction,
                                                fontSize = 10.sp,
                                                color = if (isWarningTriggered) Color(0xFFE53935) else Color(0xFF4CAF50),
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    // LARGE READABLE ACTIVE DIRECTIVE TEXT
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFF12141C), RoundedCornerShape(12.dp))
                                            .padding(16.dp)
                                            .testTag("active_directive_box")
                                            .semantics(mergeDescendants = true) {
                                                contentDescription = "Current instruction from AI Coach is: $currentInstruction"
                                            }
                                    ) {
                                        Column {
                                            Text(
                                                text = "CURRENT DIRECTIVE",
                                                fontSize = 10.sp,
                                                color = Color(0xFF9E9E9E),
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 1.sp
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            
                                            if (uiState is CoachUiState.Loading) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                                    horizontalArrangement = Arrangement.Center,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    CircularProgressIndicator(color = Color(0xFFE53935), modifier = Modifier.size(24.dp))
                                                    Spacer(modifier = Modifier.width(12.dp))
                                                    Text("AI reasoning offline-fallback ready...", color = Color.White, fontSize = 13.sp)
                                                }
                                            } else {
                                                Text(
                                                    text = currentInstruction,
                                                    style = MaterialTheme.typography.titleMedium.copy(
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Medium,
                                                        lineHeight = 24.sp,
                                                        fontFamily = FontFamily.SansSerif
                                                    )
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    // Pulse Audio Sine-Wave Visualizer Canvas
                                    PulseVisualizer(isSpeaking = isSpeaking)

                                    Spacer(modifier = Modifier.height(12.dp))

                                    // VOICE INTERACTIVE TOOLBAR (Mute, Replay, Reset)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Replay instruction button
                                        IconButton(
                                            onClick = { viewModel.replayInstruction() },
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF2E3B4E))
                                                .testTag("replay_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = "Replay the voice coaching instructions aloud",
                                                tint = Color.White
                                            )
                                        }

                                        // Play spoken tutorial again label
                                        Text(
                                            text = "Tap circle to speak aloud",
                                            fontSize = 11.sp,
                                            color = Color(0xFF9E9E9E)
                                        )

                                        // Mute/Unmute audio button
                                        IconButton(
                                            onClick = { viewModel.toggleMute() },
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(CircleShape)
                                                .background(if (isMuted) Color(0xFFE53935).copy(alpha = 0.2f) else Color(0xFF2E3B4E))
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Notifications,
                                                contentDescription = if (isMuted) "Unmute emergency voice coach guidance" else "Mute emergency voice coach guidance",
                                                tint = if (isMuted) Color(0xFFE53935) else Color(0xFF4CAF50)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 5. CLEAN, SPACE-SAVING HORIZONTALLY SCROLLING RAPID REPORTS (LazyRow-like horizontal scroll)
                        item {
                            Column {
                                Text(
                                    text = "RAPID SITUATIONAL REPORTS (SWIPE & TAP TO UPDATE)",
                                    fontSize = 11.sp,
                                    color = Color(0xFF9E9E9E),
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp,
                                    modifier = Modifier.semantics { heading() }
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                // Horizontal scrolling container using standard horizontalScroll modifier to keep it extremely reliable and space-saving
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    QuickUpdateChip(
                                        label = "🏍️ Okada Crash",
                                        onClick = {
                                            viewModel.setScenario("Road Crash with Okada (Motorcycle)")
                                            viewModel.submitBystanderUpdate("An Okada crashed on the road, victim fell heavily.")
                                            Toast.makeText(context, "Okada crash scene reported.", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                    QuickUpdateChip(
                                        label = "🩸 Severe Bleeding",
                                        onClick = {
                                            viewModel.setScenario("Heavy Bleeding")
                                            viewModel.submitBystanderUpdate("There is massive blood squirting from a deep cut.")
                                            Toast.makeText(context, "Bleeding emergency reported.", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                    QuickUpdateChip(
                                        label = "🫁 Choking",
                                        onClick = {
                                            viewModel.setScenario("Choking emergency")
                                            viewModel.submitBystanderUpdate("The bystander has stopped speaking and is holding their throat.")
                                            Toast.makeText(context, "Choking scene reported.", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                    QuickUpdateChip(
                                        label = "⚡ Unconscious / No Pulse",
                                        onClick = {
                                            viewModel.setVictimStatus("Unconscious and flat")
                                            viewModel.submitBystanderUpdate("The victim is completely unresponsive and chest is not moving.")
                                            Toast.makeText(context, "Unconscious status reported.", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                    QuickUpdateChip(
                                        label = "🍃 Traditional Oil Applied (Danger)",
                                        onClick = {
                                            viewModel.submitBystanderUpdate("They are trying to pour engine oil and sand onto the bleeding cut.")
                                            Toast.makeText(context, "Engine oil hazard reported.", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                    QuickUpdateChip(
                                        label = "👍 Scene is Safe",
                                        onClick = {
                                            viewModel.submitBystanderUpdate("I have secured the area. There is no traffic or fire hazard.")
                                            Toast.makeText(context, "Scene safety verified.", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            }
                        }

                        // 6. CUSTOM BYSTANDER UPDATE INPUT PANEL
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("update_card")
                                    .semantics(mergeDescendants = true) {
                                        contentDescription = "Custom bystander update input panel. Tap microphone icon to speak updates hands free, or type scene details in the text field."
                                    },
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2130)),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, Color(0xFF2E3B4E))
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = "CUSTOM BYSTANDER STATEMENT",
                                        fontSize = 11.sp,
                                        color = Color(0xFF9E9E9E),
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // 🎙️ Direct Speech Recognition Mic Trigger
                                        IconButton(
                                            onClick = { triggerVoiceInput() },
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFFE53935))
                                                .testTag("mic_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = "Trigger voice input to speak report hands-free",
                                                tint = Color.White
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(10.dp))

                                        // Custom Text Entry
                                        OutlinedTextField(
                                            value = bystanderInput,
                                            onValueChange = { viewModel.setBystanderInput(it) },
                                            placeholder = { Text("Or type scene changes...", fontSize = 13.sp, color = Color(0xFF9E9E9E)) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("text_input_field"),
                                            maxLines = 2,
                                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                            keyboardActions = KeyboardActions(onSend = {
                                                viewModel.submitBystanderUpdate()
                                            })
                                        )

                                        Spacer(modifier = Modifier.width(8.dp))

                                        // Send Arrow
                                        IconButton(
                                            onClick = { viewModel.submitBystanderUpdate() },
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0xFF2E3B4E))
                                                .testTag("send_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                                contentDescription = "Submit written report text",
                                                tint = Color.White
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Tip: Tap red button to talk to the AI Coach, or type updates.",
                                        fontSize = 10.sp,
                                        color = Color(0xFF9E9E9E),
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }

                        // 7. EMERGENCY CHRONOLOGICAL LOGS TIMELINE HEADER
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "RESCUE SESSION TIMELINE",
                                    fontSize = 11.sp,
                                    color = Color(0xFF9E9E9E),
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp,
                                    modifier = Modifier.semantics { heading() }
                                )

                                // Reset emergency session button
                                Text(
                                    text = "RESET SESSION",
                                    fontSize = 11.sp,
                                    color = Color(0xFFE53935),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clickable(
                                            onClickLabel = "Reset the emergency rescue session logs and start fresh"
                                        ) {
                                            viewModel.resetSession()
                                            Toast.makeText(context, "Vocal Coach history cleared. Reset safe state.", Toast.LENGTH_SHORT).show()
                                        }
                                        .testTag("reset_session_btn")
                                        .padding(8.dp)
                                )
                            }
                        }

                        // 8. LOG ITEMS OR EMPTY VIEW
                        if (sessionLogs.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No logs yet. Scene timeline is empty.",
                                        style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF9E9E9E))
                                    )
                                }
                            }
                        } else {
                            items(sessionLogs.reversed(), key = { it.id }) { log ->
                                LogItemCard(log)
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                }
                MainTab.BOOKINGS -> {
                    BookingsView(
                        responderDistance = responderDistance,
                        responderMinutes = responderMinutes,
                        responderStatus = responderStatus,
                        responderName = responderName
                    )
                }
                MainTab.AI_COACH -> {
                    AiCoachView(
                        viewModel = viewModel,
                        triggerVoiceInput = triggerVoiceInput,
                        isSpeaking = isSpeaking,
                        isMuted = isMuted,
                        currentInstruction = currentInstruction,
                        uiState = uiState
                    )
                }
                MainTab.PROFILE -> {
                    ProfileView(
                        userName = userName,
                        userPhone = userPhone,
                        userEmail = userEmail,
                        userPassword = userPassword,
                        userRole = userRole,
                        onUpdateEmail = onUpdateEmail,
                        onUpdatePassword = onUpdatePassword,
                        isDarkTheme = isDarkTheme,
                        onThemeToggle = onThemeToggle,
                        onLogout = onLogout
                    )
                }
            }
        }
    }
}

@Composable
fun BottomNavigationBar(activeTab: MainTab, onTabSelect: (MainTab) -> Unit) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        NavigationBarItem(
            selected = activeTab == MainTab.HOME,
            onClick = { onTabSelect(MainTab.HOME) },
            icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
            label = { Text("Home") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                unselectedIconColor = Color(0xFF9E9E9E),
                unselectedTextColor = Color(0xFF9E9E9E),
                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            )
        )
        NavigationBarItem(
            selected = activeTab == MainTab.BOOKINGS,
            onClick = { onTabSelect(MainTab.BOOKINGS) },
            icon = { Icon(Icons.Default.LocationOn, contentDescription = "Bookings") },
            label = { Text("Bookings") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                unselectedIconColor = Color(0xFF9E9E9E),
                unselectedTextColor = Color(0xFF9E9E9E),
                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            )
        )
        NavigationBarItem(
            selected = activeTab == MainTab.AI_COACH,
            onClick = { onTabSelect(MainTab.AI_COACH) },
            icon = { Icon(Icons.Default.Call, contentDescription = "AI Coach") },
            label = { Text("AI Coach") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                unselectedIconColor = Color(0xFF9E9E9E),
                unselectedTextColor = Color(0xFF9E9E9E),
                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            )
        )
        NavigationBarItem(
            selected = activeTab == MainTab.PROFILE,
            onClick = { onTabSelect(MainTab.PROFILE) },
            icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
            label = { Text("Profile") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                unselectedIconColor = Color(0xFF9E9E9E),
                unselectedTextColor = Color(0xFF9E9E9E),
                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            )
        )
    }
}

@Composable
fun BookingsView(
    responderDistance: Float,
    responderMinutes: Int,
    responderStatus: String,
    responderName: String
) {
    val context = LocalContext.current
    var isMapSatellite by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1C29))
    ) {
        // 1. Google Map Simulator Canvas (Styled like Bolt)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Background base map
            drawRect(
                color = if (isMapSatellite) Color(0xFF13141F) else Color(0xFF1E2130)
            )

            // Draw Grid Street Networks (Bolt style)
            val strokeWidth = 8.dp.toPx()
            val streetColor = if (isMapSatellite) Color(0xFF232635) else Color(0xFF2E3B4E)

            // Horizontal Streets
            drawLine(streetColor, start = androidx.compose.ui.geometry.Offset(0f, h * 0.2f), end = androidx.compose.ui.geometry.Offset(w, h * 0.2f), strokeWidth = strokeWidth)
            drawLine(streetColor, start = androidx.compose.ui.geometry.Offset(0f, h * 0.5f), end = androidx.compose.ui.geometry.Offset(w, h * 0.5f), strokeWidth = strokeWidth)
            drawLine(streetColor, start = androidx.compose.ui.geometry.Offset(0f, h * 0.8f), end = androidx.compose.ui.geometry.Offset(w, h * 0.8f), strokeWidth = strokeWidth)

            // Vertical Streets
            drawLine(streetColor, start = androidx.compose.ui.geometry.Offset(w * 0.25f, 0f), end = androidx.compose.ui.geometry.Offset(w * 0.25f, h), strokeWidth = strokeWidth)
            drawLine(streetColor, start = androidx.compose.ui.geometry.Offset(w * 0.65f, 0f), end = androidx.compose.ui.geometry.Offset(w * 0.65f, h), strokeWidth = strokeWidth)

            // Diagonals for routing
            drawLine(streetColor, start = androidx.compose.ui.geometry.Offset(0f, 0f), end = androidx.compose.ui.geometry.Offset(w, h), strokeWidth = strokeWidth)

            // Landmarks (Green Parks or Building blocks)
            drawRect(
                color = Color(0xFF1B3D2A),
                size = androidx.compose.ui.geometry.Size(w * 0.15f, h * 0.12f),
                topLeft = androidx.compose.ui.geometry.Offset(w * 0.05f, h * 0.28f)
            )
            drawRect(
                color = Color(0xFF352B1E),
                size = androidx.compose.ui.geometry.Size(w * 0.15f, h * 0.12f),
                topLeft = androidx.compose.ui.geometry.Offset(w * 0.75f, h * 0.28f)
            )

            // User/Victim position (Fixed at bottom right grid block)
            val userX = w * 0.65f
            val userY = h * 0.8f

            // Responder position moves along the diagonal street block as responderDistance closes in
            val startX = w * 0.25f
            val startY = h * 0.2f
            
            // Normalized progression (0f = far away, 1f = arrived)
            val progress = ((1.8f - responderDistance) / 1.8f).coerceIn(0f, 1f)
            val respX = startX + (userX - startX) * progress
            val respY = startY + (userY - startY) * progress

            // Draw polyline path from responder to user
            drawLine(
                color = Color(0xFFFFB300),
                start = androidx.compose.ui.geometry.Offset(startX, startY),
                end = androidx.compose.ui.geometry.Offset(respX, respY),
                strokeWidth = 3.dp.toPx()
            )
            drawLine(
                color = Color(0xFF4CAF50),
                start = androidx.compose.ui.geometry.Offset(respX, respY),
                end = androidx.compose.ui.geometry.Offset(userX, userY),
                strokeWidth = 4.dp.toPx()
            )

            // Draw My Location marker (pulsing blue dot)
            drawCircle(
                color = Color(0xFF1E88E5).copy(alpha = 0.2f),
                radius = 24.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(userX, userY)
            )
            drawCircle(
                color = Color(0xFF1E88E5),
                radius = 8.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(userX, userY)
            )

            // Draw Responder Location marker (pulsing green dot or motorcycle badge)
            if (responderDistance > 0f) {
                drawCircle(
                    color = Color(0xFF4CAF50).copy(alpha = 0.2f),
                    radius = 24.dp.toPx(),
                    center = androidx.compose.ui.geometry.Offset(respX, respY)
                )
                drawCircle(
                    color = Color(0xFF4CAF50),
                    radius = 8.dp.toPx(),
                    center = androidx.compose.ui.geometry.Offset(respX, respY)
                )
            }
        }

        // Overlay buttons/HUD on Map
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Map Controls Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2130)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF4CAF50))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Bolt GPS Sync: ACTIVE", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                IconButton(
                    onClick = { isMapSatellite = !isMapSatellite },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E2130))
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Map Style Toggle",
                        tint = Color.White
                    )
                }
            }

            // Bottom overlay card containing live responder metadata
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(12.dp, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF12141C)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.5.dp, Color(0xFF2E3B4E))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "DECENTRALIZED RESPONDER",
                                fontSize = 10.sp,
                                color = Color(0xFF4CAF50),
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = responderName,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }

                        // ETA Badge
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF4CAF50).copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (responderMinutes > 0) "${responderMinutes} MINS" else "ARRIVED",
                                color = Color(0xFF4CAF50),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    HorizontalDivider(color = Color(0xFF2E3B4E))

                    Spacer(modifier = Modifier.height(12.dp))

                    // Route Stats Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("REMAINING DISTANCE", color = Color(0xFF9E9E9E), fontSize = 9.sp)
                            Text("${responderDistance} KM", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("SPEED", color = Color(0xFF9E9E9E), fontSize = 9.sp)
                            Text("45 km/h", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("VEHICLE", color = Color(0xFF9E9E9E), fontSize = 9.sp)
                            Text("Okada (Plate TL-29B)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Interaction Buttons Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { Toast.makeText(context, "Calling emergency responder...", Toast.LENGTH_SHORT).show() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Phone, contentDescription = "Call", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Call", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { Toast.makeText(context, "Opening chat with responder...", Toast.LENGTH_SHORT).show() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E3B4E)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Chat", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Message", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AiCoachView(
    viewModel: TouchLifeViewModel,
    triggerVoiceInput: () -> Unit,
    isSpeaking: Boolean,
    isMuted: Boolean,
    currentInstruction: String,
    uiState: CoachUiState
) {
    val context = LocalContext.current
    val sessionLogs by viewModel.sessionLogs.collectAsState()
    val bystanderInput by viewModel.bystanderInput.collectAsState()
    
    // Auto-scroll logic for Chat logs to keep recent messages visible
    val listState = rememberLazyListState()
    LaunchedEffect(sessionLogs.size) {
        if (sessionLogs.isNotEmpty()) {
            listState.animateScrollToItem(sessionLogs.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF12141C))
    ) {
        // 1. GEMINI LIVE VISUALIZER HEADER
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2130)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF2E3B4E))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "GEMINI LIVE CHAT",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFFFFB300),
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp
                            )
                        )
                        Text(
                            text = "Vocal AI Rescue Companion",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    // Restart Session button
                    IconButton(
                        onClick = {
                            viewModel.resetSession()
                            Toast.makeText(context, "Voice chat session restarted.", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset emergency chat session and start again",
                            tint = Color(0xFFFFB300)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Iconic Gemini Live style glowing/pulsating visualizer orb
                val infiniteTransition = rememberInfiniteTransition(label = "orb_pulse")
                val orbScale by infiniteTransition.animateFloat(
                    initialValue = 0.85f,
                    targetValue = if (isSpeaking) 1.25f else 1.0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "scale"
                )

                val orbAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.4f,
                    targetValue = if (isSpeaking) 0.8f else 0.5f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "alpha"
                )

                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .semantics {
                            contentDescription = if (isSpeaking) "Gemini Live Coach is currently speaking instructions" else "Gemini Live Coach is standby and listening. You can start by saying hi."
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Glowing outer ring aura
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = orbScale
                                scaleY = orbScale
                                alpha = orbAlpha
                            }
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        if (isSpeaking) Color(0xFFFFB300) else Color(0xFF4CAF50),
                                        Color.Transparent
                                    )
                                )
                            )
                    )

                    // Inner glossy core
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        if (isSpeaking) Color(0xFFFFD54F) else Color(0xFF81C784),
                                        if (isSpeaking) Color(0xFFFF8F00) else Color(0xFF2E7D32)
                                    )
                                )
                            )
                            .shadow(4.dp, CircleShape)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = if (isSpeaking) "COACH IS SPEAKING..." else "COACH ACTIVE • SAY 'HI' TO START",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = if (isSpeaking) Color(0xFFFFB300) else Color(0xFF4CAF50),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                )
            }
        }

        // 2. SCROLLABLE CHAT TIMELINE
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (sessionLogs.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Start by saying 'Hi' or typing below",
                                style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF9E9E9E)),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Example: \"Hi, an Okada rider fell and is bleeding heavily!\"",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFFFB300)),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(sessionLogs, key = { log -> log.id }) { log ->
                    val isBystander = log.sender == "Bystander"
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics(mergeDescendants = true) {
                                contentDescription = if (isBystander) "Bystander says: ${log.message}" else "Vocal Coach says: ${log.message}"
                            },
                        horizontalAlignment = if (isBystander) Alignment.End else Alignment.Start
                    ) {
                        Text(
                            text = log.sender.uppercase(),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isBystander) Color(0xFFFFB300) else Color(0xFF9E9E9E),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                        Box(
                            modifier = Modifier
                                .widthIn(max = 280.dp)
                                .clip(
                                    RoundedCornerShape(
                                        topStart = 16.dp,
                                        topEnd = 16.dp,
                                        bottomStart = if (isBystander) 16.dp else 2.dp,
                                        bottomEnd = if (isBystander) 2.dp else 16.dp
                                    )
                                )
                                .background(
                                    if (isBystander) MaterialTheme.colorScheme.primary else Color(0xFF1E2130)
                                )
                                .border(
                                    1.dp,
                                    if (isBystander) Color.Transparent else Color(0xFF2E3B4E),
                                    RoundedCornerShape(
                                        topStart = 16.dp,
                                        topEnd = 16.dp,
                                        bottomStart = if (isBystander) 16.dp else 2.dp,
                                        bottomEnd = if (isBystander) 2.dp else 16.dp
                                    )
                                )
                                .padding(12.dp)
                        ) {
                            Text(
                                text = log.message,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = Color.White,
                                    lineHeight = 20.sp
                                )
                            )
                        }
                        Text(
                            text = log.timestamp,
                            fontSize = 8.sp,
                            color = Color(0xFF757575),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        // 3. DOCKED BOTTOM INPUT BAR (TEXT + VOICE MIC)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2130)),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Color(0xFF2E3B4E))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Voice Mic Button
                IconButton(
                    onClick = { triggerVoiceInput() },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE53935))
                        .testTag("mic_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Trigger hands free voice input recognition mic",
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Outlined Text Field for typing problems
                OutlinedTextField(
                    value = bystanderInput,
                    onValueChange = { viewModel.setBystanderInput(it) },
                    placeholder = {
                        Text(
                            text = "Type your problem here...",
                            fontSize = 13.sp,
                            color = Color(0xFF9E9E9E)
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("text_input_field"),
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (bystanderInput.trim().isNotEmpty()) {
                            viewModel.submitBystanderUpdate()
                        }
                    })
                )

                Spacer(modifier = Modifier.width(4.dp))

                // Send Button
                IconButton(
                    onClick = {
                        if (bystanderInput.trim().isNotEmpty()) {
                            viewModel.submitBystanderUpdate()
                        }
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFFB300))
                        .testTag("send_button"),
                    enabled = bystanderInput.trim().isNotEmpty()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Send written message to AI Coach",
                        tint = Color(0xFF12141C)
                    )
                }
            }
        }
    }
}

enum class ProfileSection {
    MAIN,
    VERIFY_EMAIL,
    CHANGE_PASSWORD
}

@Composable
fun ProfileView(
    userName: String,
    userPhone: String,
    userEmail: String,
    userPassword: String,
    userRole: String,
    onUpdateEmail: (String) -> Unit,
    onUpdatePassword: (String) -> Unit,
    isDarkTheme: Boolean,
    onThemeToggle: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    var emailInput by remember { mutableStateOf(userEmail) }
    var oldPasswordInput by remember { mutableStateOf("") }
    var newPasswordInput by remember { mutableStateOf("") }
    var activeSection by remember { mutableStateOf(ProfileSection.MAIN) }

    if (activeSection == ProfileSection.MAIN) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // User Banner Avatar
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, Color(0xFF2E3B4E))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (userName.isNotEmpty()) userName.take(1).uppercase() else "G",
                                color = Color.White,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column {
                            Text(
                                text = if (userName.isNotEmpty()) userName else "Guest Bystander",
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "ROLE: $userRole",
                                color = Color(0xFFFFB300),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (userPhone.isNotEmpty()) userPhone else "No phone attached",
                                color = Color(0xFF9E9E9E),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // Email Verification Settings Item
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { activeSection = ProfileSection.VERIFY_EMAIL },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, Color(0xFF2E3B4E))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF1E2130)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Email,
                                    contentDescription = null,
                                    tint = Color(0xFFFFB300),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Verify Email Address",
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = if (userEmail.isNotEmpty()) userEmail else "Not configured",
                                    color = Color(0xFF9E9E9E),
                                    fontSize = 12.sp
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Go to verify email",
                            tint = Color(0xFF9E9E9E),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Change Password Settings Item
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { activeSection = ProfileSection.CHANGE_PASSWORD },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, Color(0xFF2E3B4E))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF1E2130)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Change Password",
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "Keep your account secure",
                                    color = Color(0xFF9E9E9E),
                                    fontSize = 12.sp
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Go to change password",
                            tint = Color(0xFF9E9E9E),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // App Theme Toggles Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, Color(0xFF2E3B4E))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "APPEARANCE SETTINGS",
                                fontSize = 11.sp,
                                color = Color(0xFFFFB300),
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isDarkTheme) "Dark Mode Active" else "Light Mode Active",
                                color = MaterialTheme.colorScheme.onBackground,
                                fontSize = 14.sp
                            )
                        }

                        Switch(
                            checked = isDarkTheme,
                            onCheckedChange = { onThemeToggle() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFFFFB300),
                                checkedTrackColor = Color(0xFF2E3B4E)
                            )
                        )
                    }
                }
            }

            // Logout block
            item {
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { onLogout() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Logout", tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Log Out Session", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    } else if (activeSection == ProfileSection.VERIFY_EMAIL) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Back Button Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { activeSection = ProfileSection.MAIN }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Back to Profile",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, Color(0xFF2E3B4E))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "EMAIL AUTHENTICATION",
                        fontSize = 11.sp,
                        color = Color(0xFFFFB300),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Update and verify your email address to receive emergency action certificates and backup rescue credentials.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color(0xFF9E9E9E),
                            lineHeight = 20.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = { emailInput = it },
                        label = { Text("Email Address") },
                        placeholder = { Text("Enter your email address") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onBackground,
                            unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color(0xFF2E3B4E)
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("profile_email_field")
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            if (emailInput.isNotBlank()) {
                                onUpdateEmail(emailInput)
                                Toast.makeText(context, "Email authenticated successfully!", Toast.LENGTH_SHORT).show()
                                activeSection = ProfileSection.MAIN
                            } else {
                                Toast.makeText(context, "Please enter a valid email", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Save & Verify Email", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }
    } else if (activeSection == ProfileSection.CHANGE_PASSWORD) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Back Button Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { activeSection = ProfileSection.MAIN }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Back to Profile",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, Color(0xFF2E3B4E))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "CHANGE SECURE PASSWORD",
                        fontSize = 11.sp,
                        color = Color(0xFFFFB300),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Secure your TouchLife account. Keep your emergency rescue capabilities protected by using a strong password.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color(0xFF9E9E9E),
                            lineHeight = 20.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    OutlinedTextField(
                        value = oldPasswordInput,
                        onValueChange = { oldPasswordInput = it },
                        label = { Text("Current Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onBackground,
                            unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color(0xFF2E3B4E)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = newPasswordInput,
                        onValueChange = { newPasswordInput = it },
                        label = { Text("New Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onBackground,
                            unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color(0xFF2E3B4E)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            if (oldPasswordInput == userPassword || userPassword.isEmpty()) {
                                if (newPasswordInput.isNotBlank()) {
                                    onUpdatePassword(newPasswordInput)
                                    Toast.makeText(context, "Password updated successfully!", Toast.LENGTH_SHORT).show()
                                    oldPasswordInput = ""
                                    newPasswordInput = ""
                                    activeSection = ProfileSection.MAIN
                                } else {
                                    Toast.makeText(context, "New password cannot be empty", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, "Incorrect current password!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Update Password", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}

/**
 * Renders a single log block inside the scrollable triage history.
 */
@Composable
fun LogItemCard(log: RescueLog) {
    val isBystander = log.sender == "Bystander"
    val cardColor = if (isBystander) Color(0xFF2E3B4E) else Color(0xFF1E2130)
    val alignment = if (isBystander) Alignment.End else Alignment.Start

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalAlignment = alignment
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Text(
                text = "${log.sender} • ${log.timestamp}",
                fontSize = 10.sp,
                color = Color(0xFF9E9E9E),
                fontWeight = FontWeight.Bold
            )
        }

        Card(
            modifier = Modifier
                .widthIn(max = 290.dp)
                .shadow(2.dp, RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(
                topStart = 12.dp,
                topEnd = 12.dp,
                bottomStart = if (isBystander) 12.dp else 0.dp,
                bottomEnd = if (isBystander) 0.dp else 12.dp
            ),
            colors = CardDefaults.cardColors(containerColor = cardColor),
            border = if (log.isWarning) BorderStroke(1.dp, Color(0xFFE53935)) else null
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = log.message,
                    fontSize = 13.sp,
                    color = Color.White,
                    lineHeight = 18.sp
                )

                if (log.actionCode != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .background(
                                if (log.isWarning) Color(0xFFE53935).copy(alpha = 0.2f) else Color(0xFF4CAF50).copy(alpha = 0.2f),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "ACTION: ${log.actionCode}",
                            fontSize = 9.sp,
                            color = if (log.isWarning) Color(0xFFE53935) else Color(0xFF4CAF50),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Ergonomic quick-reporting presets for high-stress accident scenes.
 */
@Composable
fun QuickUpdateChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .shadow(1.dp, RoundedCornerShape(10.dp)),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2130)),
        border = BorderStroke(1.dp, Color(0xFF2E3B4E))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

/**
 * Animated neon soundwave visualizer drawn via Canvas.
 */
@Composable
fun PulseVisualizer(isSpeaking: Boolean) {
    val transition = rememberInfiniteTransition(label = "pulse_vis")
    
    // Wave offsets animating continuously when speaking
    val waveOffset1 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave1"
    )

    val waveOffset2 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave2"
    )

    val activeColor = Color(0xFFFFB300)
    val inactiveColor = Color(0xFF2E3B4E)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(Color(0xFF12141C), RoundedCornerShape(8.dp))
    ) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        val amplitude = if (isSpeaking) 12f else 2f // Speak wave magnitude vs flat resting line

        val path1 = Path()
        val path2 = Path()

        path1.moveTo(0f, centerY)
        path2.moveTo(0f, centerY)

        for (x in 0..width.toInt() step 5) {
            val xFloat = x.toFloat()
            // Sine waves calculation
            val angle1 = (xFloat / width) * 4f * Math.PI.toFloat() + waveOffset1
            val y1 = centerY + sin(angle1) * amplitude
            path1.lineTo(xFloat, y1)

            val angle2 = (xFloat / width) * 6f * Math.PI.toFloat() - waveOffset2
            val y2 = centerY + sin(angle2) * (amplitude * 0.7f)
            path2.lineTo(xFloat, y2)
        }

        drawPath(
            path = path1,
            color = if (isSpeaking) activeColor else inactiveColor,
            style = Stroke(width = if (isSpeaking) 2.dp.toPx() else 1.dp.toPx())
        )

        if (isSpeaking) {
            drawPath(
                path = path2,
                color = Color(0xFFE53935).copy(alpha = 0.6f),
                style = Stroke(width = 1.5.dp.toPx())
            )
        }
    }
}

@Composable
fun TouchLifeSplashScreen(
    onDismiss: () -> Unit
) {
    // Stage controller states
    var logoVisible by remember { mutableStateOf(false) }
    var mottoVisible by remember { mutableStateOf(false) }

    // Start timers for sequence
    LaunchedEffect(Unit) {
        // Step 1: Logo appears
        delay(300)
        logoVisible = true
        // Step 2: Motto appears under already displaying logo (morph transition)
        delay(1200)
        mottoVisible = true
        // Step 3: Automatic advance
        delay(3200)
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF12141C))
            .testTag("splash_screen_container"),
        contentAlignment = Alignment.Center
    ) {
        // Decorative radial ambient glowing backgrounds
        Box(
            modifier = Modifier
                .size(350.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFE53935).copy(alpha = 0.08f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            // THE LOGO WILL APPEAR FIRST
            AnimatedVisibility(
                visible = logoVisible,
                enter = fadeIn(animationSpec = tween(1000, easing = EaseOutCubic)) +
                        scaleIn(initialScale = 0.5f, animationSpec = tween(1000, easing = EaseOutCubic)),
                exit = fadeOut(animationSpec = tween(500))
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Logo Image Circle with dynamic breathing effect
                    val infiniteTransition = rememberInfiniteTransition(label = "logo_breath")
                    val logoScale by infiniteTransition.animateFloat(
                        initialValue = 0.96f,
                        targetValue = 1.04f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(2200, easing = EaseInOutSine),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "scale"
                    )

                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .shadow(16.dp, CircleShape)
                            .border(2.dp, Color(0xFF4CAF50), CircleShape)
                            .clip(CircleShape)
                            .background(Color(0xFF1E2130)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_touchlife_logo),
                            contentDescription = "TouchLife Main Logo Asset",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(2.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Spacer(modifier = Modifier.height(28.dp))
                }
            }

            // MORPH TRANSITION OF THE MOTTO TEXT UNDER THE ALREADY DISPLAYED LOGO
            AnimatedVisibility(
                visible = mottoVisible,
                enter = expandVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ) + fadeIn(animationSpec = tween(1000)),
                exit = shrinkVertically(animationSpec = tween(500)) + fadeOut()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Brand Name with custom colors: "TOUCH" in white, "LIFE" in medical green
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TOUCH",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                fontSize = 38.sp,
                                letterSpacing = 2.sp
                            )
                        )
                        Text(
                            text = "LIFE",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF4CAF50),
                                fontSize = 38.sp,
                                letterSpacing = 2.sp
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Nigeria's First Aid & Rescue Network Motto under it
                    Text(
                        text = "Nigeria's First Aid & Rescue Network",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFFFB300),
                            fontSize = 15.sp,
                            letterSpacing = 0.5.sp,
                            textAlign = TextAlign.Center
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun DispatchAnimationDrawing(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "dispatch")
    
    val pulse1 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse1"
    )
    
    val pulse2 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, delayMillis = 600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse2"
    )
    
    val translationY by transition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "translationY"
    )

    Box(
        modifier = modifier
            .size(160.dp)
            .background(Color(0xFF1E2130), CircleShape)
            .border(1.dp, Color(0xFF2E3B4E), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = center
            val maxRadius = size.minDimension * 0.45f
            
            drawCircle(
                color = Color(0xFF4CAF50).copy(alpha = 0.15f),
                radius = maxRadius,
                style = Stroke(width = 1.dp.toPx())
            )
            drawCircle(
                color = Color(0xFF4CAF50).copy(alpha = 0.15f),
                radius = maxRadius * 0.6f,
                style = Stroke(width = 1.dp.toPx())
            )
            
            drawCircle(
                color = Color(0xFF4CAF50).copy(alpha = 0.3f * (1f - pulse1)),
                radius = maxRadius * pulse1,
                style = Stroke(width = 2.dp.toPx())
            )
            drawCircle(
                color = Color(0xFF4CAF50).copy(alpha = 0.3f * (1f - pulse2)),
                radius = maxRadius * pulse2,
                style = Stroke(width = 2.dp.toPx())
            )
            
            drawLine(
                color = Color(0xFF4CAF50).copy(alpha = 0.2f),
                start = androidx.compose.ui.geometry.Offset(center.x - maxRadius, center.y),
                end = androidx.compose.ui.geometry.Offset(center.x + maxRadius, center.y),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = Color(0xFF4CAF50).copy(alpha = 0.2f),
                start = androidx.compose.ui.geometry.Offset(center.x, center.y - maxRadius),
                end = androidx.compose.ui.geometry.Offset(center.x, center.y + maxRadius),
                strokeWidth = 1.dp.toPx()
            )
        }
        
        Column(
            modifier = Modifier.graphicsLayer { this.translationY = translationY },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(Color(0xFFFFB300), CircleShape)
                    .shadow(4.dp, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = Color(0xFF12141C),
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .background(Color(0xFF4CAF50), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "DISPATCHING",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun VocalCoachAnimationDrawing(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "vocal")
    
    val scaleWave1 by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scaleWave1"
    )
    
    val scaleWave2 by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scaleWave2"
    )

    val scaleWave3 by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scaleWave3"
    )

    Box(
        modifier = modifier
            .size(160.dp)
            .background(Color(0xFF1E2130), CircleShape)
            .border(1.dp, Color(0xFF2E3B4E), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.size(56.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    
                    drawCircle(
                        color = Color.White.copy(alpha = 0.15f),
                        radius = w * 0.28f,
                        center = androidx.compose.ui.geometry.Offset(w / 2f, h * 0.35f)
                    )
                    
                    val shoulderPath = Path()
                    shoulderPath.moveTo(w * 0.2f, h * 0.9f)
                    shoulderPath.quadraticTo(
                        w / 2f, h * 0.55f,
                        w * 0.8f, h * 0.9f
                    )
                    drawPath(
                        path = shoulderPath,
                        color = Color.White.copy(alpha = 0.15f)
                    )
                    
                    val micPath = Path()
                    micPath.moveTo(w * 0.6f, h * 0.35f)
                    micPath.quadraticTo(
                        w * 0.72f, h * 0.55f,
                        w * 0.52f, h * 0.62f
                    )
                    drawPath(
                        path = micPath,
                        color = Color(0xFFFFB300),
                        style = Stroke(width = 2.dp.toPx())
                    )
                    drawCircle(
                        color = Color(0xFFFFB300),
                        radius = 3.dp.toPx(),
                        center = androidx.compose.ui.geometry.Offset(w * 0.52f, h * 0.62f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(10.dp))
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(10.dp * scaleWave1)
                        .background(Color(0xFFFFB300), RoundedCornerShape(2.dp))
                )
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(20.dp * scaleWave2)
                        .background(Color(0xFFFFB300), RoundedCornerShape(2.dp))
                )
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(28.dp * scaleWave3)
                        .background(Color(0xFFFFB300), RoundedCornerShape(2.dp))
                )
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(16.dp * scaleWave2)
                        .background(Color(0xFFFFB300), RoundedCornerShape(2.dp))
                )
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(8.dp * scaleWave1)
                        .background(Color(0xFFFFB300), RoundedCornerShape(2.dp))
                )
            }
        }
    }
}

@Composable
fun RescueNetworkAnimationDrawing(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "rescue")
    
    val pulseScale by transition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heartPulse"
    )
    
    val ringAlpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ringAlpha"
    )

    Box(
        modifier = modifier
            .size(160.dp)
            .background(Color(0xFF1E2130), CircleShape)
            .border(1.dp, Color(0xFF2E3B4E), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = center
            val maxRadius = size.minDimension * 0.45f
            
            drawCircle(
                color = Color(0xFFE53935).copy(alpha = 0.3f * ringAlpha),
                radius = maxRadius * (1f - ringAlpha + 0.2f),
                style = Stroke(width = 1.5.dp.toPx())
            )
        }
        
        Box(
            modifier = Modifier.size(72.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                
                val handLeft = Path()
                handLeft.moveTo(w * 0.05f, h * 0.75f)
                handLeft.cubicTo(
                    w * 0.15f, h * 0.95f,
                    w * 0.4f, h * 0.9f,
                    w * 0.48f, h * 0.75f
                )
                handLeft.quadraticTo(w * 0.4f, h * 0.6f, w * 0.35f, h * 0.72f)
                
                drawPath(
                    path = handLeft,
                    color = Color(0xFF4CAF50),
                    style = Stroke(width = 2.5.dp.toPx())
                )

                val handRight = Path()
                handRight.moveTo(w * 0.95f, h * 0.75f)
                handRight.cubicTo(
                    w * 0.85f, h * 0.95f,
                    w * 0.6f, h * 0.9f,
                    w * 0.52f, h * 0.75f
                )
                handRight.quadraticTo(w * 0.6f, h * 0.6f, w * 0.65f, h * 0.72f)
                
                drawPath(
                    path = handRight,
                    color = Color(0xFF4CAF50),
                    style = Stroke(width = 2.5.dp.toPx())
                )
            }
            
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = pulseScale
                        scaleY = pulseScale
                    }
                    .size(36.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    
                    val heartPath = Path()
                    heartPath.moveTo(w / 2f, h * 0.25f)
                    heartPath.cubicTo(
                        w * 0.15f, h * 0.05f,
                        w * 0.05f, h * 0.5f,
                        w / 2f, h * 0.92f
                    )
                    heartPath.cubicTo(
                        w * 0.95f, h * 0.5f,
                        w * 0.85f, h * 0.05f,
                        w / 2f, h * 0.25f
                    )
                    
                    drawPath(
                        path = heartPath,
                        color = Color(0xFFE53935)
                    )
                    
                    val crossWidth = w * 0.1f
                    val crossLength = w * 0.32f
                    
                    drawRect(
                        color = Color.White,
                        topLeft = androidx.compose.ui.geometry.Offset((w - crossLength) / 2f, (h - crossWidth) / 2f - h * 0.04f),
                        size = androidx.compose.ui.geometry.Size(crossLength, crossWidth)
                    )
                    drawRect(
                        color = Color.White,
                        topLeft = androidx.compose.ui.geometry.Offset((w - crossWidth) / 2f, (h - crossLength) / 2f - h * 0.04f),
                        size = androidx.compose.ui.geometry.Size(crossWidth, crossLength)
                    )
                }
            }
        }
    }
}

@Composable
fun TouchLifeOnboardingScreen(
    onSkip: () -> Unit,
    onAuthenticate: (name: String, phone: String, email: String, password: String, role: String) -> Unit
) {
    var showSignIn by remember { mutableStateOf(false) }
    var onboardingStep by remember { mutableStateOf(0) }
    var isFirstAiderSelected by remember { mutableStateOf(true) }
    var userName by remember { mutableStateOf("") }
    var userPhone by remember { mutableStateOf("") }
    var userEmail by remember { mutableStateOf("") }
    var userPassword by remember { mutableStateOf("") }
    var certId by remember { mutableStateOf("") }
    var showRecoveryDialog by remember { mutableStateOf(false) }
    var recoveryEmailOrPhone by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF12141C))
            .testTag("onboarding_container")
    ) {
        // Decorative background glows
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(260.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF4CAF50).copy(alpha = 0.06f),
                            Color.Transparent
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .size(260.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFE53935).copy(alpha = 0.06f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
        ) {
            // 1. HEADER ROW WITH NAVIGATION CONTROLS
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back Button / App Title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(enabled = showSignIn) { showSignIn = false }
                ) {
                    if (showSignIn) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Info",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    } else {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E2130))
                                .border(1.dp, Color(0xFF4CAF50), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.img_touchlife_logo),
                                contentDescription = "Mini Logo",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = "TOUCHLIFE",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 1.sp
                        )
                    )
                }

                // Skip Button
                if (!showSignIn) {
                    Button(
                        onClick = {
                            // Tapping skip on info summary pops up the sign in page!
                            showSignIn = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2130)),
                        border = BorderStroke(1.dp, Color(0xFF2E3B4E)),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("skip_onboarding_btn")
                    ) {
                        Text(
                            text = "Skip ➔",
                            fontSize = 12.sp,
                            color = Color(0xFF9E9E9E),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // 2. CENTRAL CONTENT (CHANGES ACCORDING TO STATE)
            if (!showSignIn) {
                // STAGE 1: Simplified interactive tips with faceless animation drawing for each
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Spacer(modifier = Modifier.height(16.dp))

                    // 1. Faceless animation drawing card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1.3f)
                            .shadow(4.dp, RoundedCornerShape(24.dp)),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2130)),
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.dp, Color(0xFF2E3B4E))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            when (onboardingStep) {
                                0 -> DispatchAnimationDrawing()
                                1 -> VocalCoachAnimationDrawing()
                                2 -> RescueNetworkAnimationDrawing()
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // 2. Feature text block under the drawing
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val titleText = when (onboardingStep) {
                            0 -> "RAPID SCENE DISPATCH"
                            1 -> "VOCAL HANDS-FREE COACH"
                            else -> "COOPERATIVE NETWORK"
                        }
                        
                        val descText = when (onboardingStep) {
                            0 -> "Routes the closest certified responder to motorcycle (Okada) or road crash coordinates immediately."
                            1 -> "Step-by-step interactive audio instructions guide you in life-saving first-aid triage."
                            else -> "Uniting trained volunteers, bystanders, and operations into a decentralized survival loop to save lives."
                        }

                        Text(
                            text = titleText,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFFFB300),
                                letterSpacing = 1.2.sp
                            ),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = descText,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color.White.copy(alpha = 0.8f),
                                lineHeight = 20.sp,
                                textAlign = TextAlign.Center
                            ),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }

                    // 3. Dot Indicators
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 12.dp)
                    ) {
                        repeat(3) { index ->
                            val isActive = index == onboardingStep
                            Box(
                                modifier = Modifier
                                    .size(if (isActive) 10.dp else 8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isActive) Color(0xFF4CAF50) else Color(0xFF2E3B4E)
                                    )
                                    .clickable { onboardingStep = index }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 4. Primary Onboarding Action button
                    Button(
                        onClick = {
                            if (onboardingStep < 2) {
                                onboardingStep++
                            } else {
                                showSignIn = true
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("continue_to_auth_btn"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (onboardingStep == 2) Color(0xFF4CAF50) else Color(0xFF1E2130)
                        ),
                        border = if (onboardingStep == 2) null else BorderStroke(1.dp, Color(0xFF4CAF50)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (onboardingStep == 2) "Get Started ➔" else "Next Feature ➔",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                }
            } else {
                // STAGE 2: Sign-in and Registration form (pops up when skip/get started clicked)
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "SIGN IN OR REGISTER",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 18.sp
                            )
                        )
                        Text(
                            text = "Register as a trained medical volunteer (First-Aider), a citizen bystander, or simply continue as a guest.",
                            color = Color(0xFF9E9E9E),
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }

                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2130)),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, Color(0xFF2E3B4E))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "SELECT YOUR PROFILE TYPE",
                                    fontSize = 11.sp,
                                    color = Color(0xFF9E9E9E),
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.height(12.dp))

                                // Custom role toggle tabs
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF12141C), RoundedCornerShape(10.dp))
                                        .padding(4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Button(
                                        onClick = { isFirstAiderSelected = true },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isFirstAiderSelected) Color(0xFF4CAF50) else Color.Transparent
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(vertical = 10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = "First-Aider Role Select",
                                            modifier = Modifier.size(16.dp),
                                            tint = if (isFirstAiderSelected) Color.White else Color(0xFF9E9E9E)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "First-Aider",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isFirstAiderSelected) Color.White else Color(0xFF9E9E9E)
                                        )
                                    }

                                    Button(
                                        onClick = { isFirstAiderSelected = false },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (!isFirstAiderSelected) Color(0xFFE53935) else Color.Transparent
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(vertical = 10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = "User Role Select",
                                            modifier = Modifier.size(16.dp),
                                            tint = if (!isFirstAiderSelected) Color.White else Color(0xFF9E9E9E)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Citizen/User",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (!isFirstAiderSelected) Color.White else Color(0xFF9E9E9E)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Name Input
                                OutlinedTextField(
                                    value = userName,
                                    onValueChange = { userName = it },
                                    label = { Text("Full Name") },
                                    placeholder = { Text(if (isFirstAiderSelected) "e.g. Dr. Emeka Obi" else "e.g. Kola Adesina") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = "Name Input Field",
                                            tint = Color(0xFF9E9E9E)
                                        )
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("name_field"),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = if (isFirstAiderSelected) Color(0xFF4CAF50) else Color(0xFFE53935),
                                        unfocusedBorderColor = Color(0xFF2E3B4E)
                                    )
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                // Email Input
                                OutlinedTextField(
                                    value = userEmail,
                                    onValueChange = { userEmail = it },
                                    label = { Text("Email Address") },
                                    placeholder = { Text("e.g. name@example.com") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Email,
                                            contentDescription = "Email Input Field",
                                            tint = Color(0xFF9E9E9E)
                                        )
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("email_field"),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = if (isFirstAiderSelected) Color(0xFF4CAF50) else Color(0xFFE53935),
                                        unfocusedBorderColor = Color(0xFF2E3B4E)
                                    )
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                // Phone input
                                OutlinedTextField(
                                    value = userPhone,
                                    onValueChange = { userPhone = it },
                                    label = { Text("Phone Number") },
                                    placeholder = { Text("e.g. +234 803 123 4567") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Phone,
                                            contentDescription = "Phone Input Field",
                                            tint = Color(0xFF9E9E9E)
                                        )
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("phone_field"),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = if (isFirstAiderSelected) Color(0xFF4CAF50) else Color(0xFFE53935),
                                        unfocusedBorderColor = Color(0xFF2E3B4E)
                                    )
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                // Password input with visibility toggle
                                OutlinedTextField(
                                    value = userPassword,
                                    onValueChange = { userPassword = it },
                                    label = { Text("Password") },
                                    placeholder = { Text("Enter a secure password") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = "Password Input Field",
                                            tint = Color(0xFF9E9E9E)
                                        )
                                    },
                                    trailingIcon = {
                                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                            Icon(
                                                imageVector = if (isPasswordVisible) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                                contentDescription = "Toggle password visibility",
                                                tint = Color(0xFF9E9E9E)
                                            )
                                        }
                                    },
                                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("password_field"),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = if (isFirstAiderSelected) Color(0xFF4CAF50) else Color(0xFFE53935),
                                        unfocusedBorderColor = Color(0xFF2E3B4E)
                                    )
                                )

                                // Forgot password link row
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Text(
                                        text = "Forgot Password?",
                                        color = Color(0xFFFFB300),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .clickable { showRecoveryDialog = true }
                                            .padding(4.dp)
                                            .testTag("forgot_password_link")
                                    )
                                }

                                // Certified First-Aider inputs
                                if (isFirstAiderSelected) {
                                    Spacer(modifier = Modifier.height(12.dp))

                                    OutlinedTextField(
                                        value = certId,
                                        onValueChange = { certId = it },
                                        label = { Text("Medical Certification ID (Optional)") },
                                        placeholder = { Text("e.g. NMCN/2026/0511") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "License Input Field",
                                                tint = Color(0xFF9E9E9E)
                                            )
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("cert_id_field"),
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White,
                                            focusedBorderColor = Color(0xFF4CAF50),
                                            unfocusedBorderColor = Color(0xFF2E3B4E)
                                        )
                                    )
                                }

                                Spacer(modifier = Modifier.height(18.dp))

                                // Registration submit action button
                                Button(
                                    onClick = {
                                        if (userName.isBlank() || userPhone.isBlank() || userEmail.isBlank() || userPassword.isBlank()) {
                                            Toast.makeText(context, "Please fill in all fields to register", Toast.LENGTH_SHORT).show()
                                        } else {
                                            val roleStr = if (isFirstAiderSelected) "First-Aider" else "Citizen User"
                                            Toast.makeText(context, "Registered successfully as $roleStr: $userName", Toast.LENGTH_SHORT).show()
                                            onAuthenticate(userName, userPhone, userEmail, userPassword, roleStr)
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("submit_auth_btn"),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isFirstAiderSelected) Color(0xFF4CAF50) else Color(0xFFE53935)
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isFirstAiderSelected) Icons.Default.Done else Icons.Default.Add,
                                        contentDescription = "Register",
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isFirstAiderSelected) "Register as First-Aider" else "Register as User",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Or Guest button
                                Button(
                                    onClick = {
                                        Toast.makeText(context, "Signed in as Guest bystander", Toast.LENGTH_SHORT).show()
                                        onAuthenticate("Guest Bystander", "+234 800 GUEST", "guest@touchlife.ng", "guest_pwd", "Guest Bystander")
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("guest_auth_btn"),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2130)),
                                    border = BorderStroke(1.dp, Color(0xFF4CAF50).copy(alpha = 0.5f)),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Guest play icon",
                                        tint = Color(0xFF4CAF50),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Sign in as Guest",
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Account Recovery / Forgot Password Dialog
    if (showRecoveryDialog) {
        AlertDialog(
            onDismissRequest = { showRecoveryDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Recovery Icon",
                        tint = Color(0xFFFFB300)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Account Recovery", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text(
                        text = "Enter your registered Email or Phone Number to retrieve your account recovery token.",
                        color = Color(0xFF9E9E9E),
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = recoveryEmailOrPhone,
                        onValueChange = { recoveryEmailOrPhone = it },
                        label = { Text("Email or Phone") },
                        placeholder = { Text("e.g. name@example.com") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFFFB300),
                            unfocusedBorderColor = Color(0xFF2E3B4E)
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("recovery_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (recoveryEmailOrPhone.isBlank()) {
                            Toast.makeText(context, "Please enter your recovery address", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Recovery code sent successfully to $recoveryEmailOrPhone!", Toast.LENGTH_LONG).show()
                            showRecoveryDialog = false
                            recoveryEmailOrPhone = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB300))
                ) {
                    Text("Send Recovery Code", color = Color(0xFF12141C), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRecoveryDialog = false }) {
                    Text("Cancel", color = Color(0xFF9E9E9E))
                }
            },
            containerColor = Color(0xFF1E2130)
        )
    }
}
