package com.example.arushi.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.arushi.viewmodel.ArushiViewModel
import com.example.arushi.viewmodel.AssistantState
import kotlin.math.sin

// Futuristic Arushi theme colors
private val DeepSpace = Color(0xFF0A0714)
private val CardSurface = Color(0xFF160F2A)
private val NeonViolet = Color(0xFF8B5CF6)
private val NeonMagenta = Color(0xFFEC4899)
private val NeonCyan = Color(0xFF06B6D4)
private val GlowGold = Color(0xFFFBBF24)
private val SubtleBorder = Color(0xFF2E2252)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArushiScreen(
    viewModel: ArushiViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showDiagnosticsSheet by remember { mutableStateOf(false) }

    // Audio Permission Launcher
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startSession()
        }
    }

    // Contacts Permission Launcher (for callContact)
    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* handled in ViewModel / ActionManager */ }

    fun handleMicClick() {
        val hasMicPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasMicPermission) {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            viewModel.toggleSession()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        DeepSpace,
                        Color(0xFF120B24),
                        DeepSpace
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .widthIn(max = 600.dp)
                .align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Top Bar: Header, Status Pill, Diagnostic Action
            TopBarSection(
                state = uiState.state,
                isLiveConnected = uiState.isLiveConnected,
                onDiagnosticsClick = { showDiagnosticsSheet = true }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 2. Center: Dynamic Holographic Visualizer Orb
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                HolographicVoiceVisualizer(
                    state = uiState.state,
                    amplitude = uiState.amplitude,
                    modifier = Modifier.size(240.dp)
                )
            }

            // 3. Captions & Dialogue Card
            DialogueCard(
                state = uiState.state,
                userTranscript = uiState.userTranscript,
                arushiTranscript = uiState.arushiTranscript,
                executedAction = uiState.executedAction,
                onDismissAction = { viewModel.dismissAction() }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 4. Quick Action Chips
            QuickActionChipsRow(
                onPromptSelected = { prompt ->
                    if (prompt.contains("Mom", ignoreCase = true)) {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                            contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                        }
                    }
                    viewModel.sendPrompt(prompt)
                },
                onTestSpeaker = { viewModel.testSpeaker() }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 5. Bottom Controls: Power / Mic Main FAB & Test Speaker
            ControlsDock(
                state = uiState.state,
                isMicActive = uiState.isMicActive,
                onMicClick = { handleMicClick() },
                onSpeakerTestClick = { viewModel.testSpeaker() }
            )

            Spacer(modifier = Modifier.height(8.dp))
        }

        // Diagnostics BottomSheet
        if (showDiagnosticsSheet) {
            ModalBottomSheet(
                onDismissRequest = { showDiagnosticsSheet = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = CardSurface
            ) {
                DiagnosticsContent(
                    uiState = uiState,
                    onClose = { showDiagnosticsSheet = false },
                    onClearLogs = { viewModel.clearLogs() },
                    onTestSpeaker = { viewModel.testSpeaker() }
                )
            }
        }
    }
}

@Composable
private fun TopBarSection(
    state: AssistantState,
    isLiveConnected: Boolean,
    onDiagnosticsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(NeonMagenta, NeonViolet)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "Arushi Icon",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "Arushi",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 0.5.sp
                    )
                )
                Text(
                    text = "Real-time Voice AI",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = NeonCyan,
                        fontSize = 11.sp
                    )
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            // Status Pill
            StatusBadge(state = state, isConnected = isLiveConnected)

            Spacer(modifier = Modifier.width(8.dp))

            // Diagnostic button
            IconButton(
                onClick = onDiagnosticsClick,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(CardSurface)
                    .border(1.dp, SubtleBorder, CircleShape)
                    .testTag("diagnostics_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Diagnostics",
                    tint = Color(0xFFD4D4E8),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(state: AssistantState, isConnected: Boolean) {
    val (label, dotColor) = when (state) {
        AssistantState.IDLE -> if (isConnected) "Live Ready" to NeonCyan else "Tap to Start" to Color.Gray
        AssistantState.CONNECTING -> "Connecting..." to GlowGold
        AssistantState.LISTENING -> "Listening" to NeonCyan
        AssistantState.SPEAKING -> "Speaking" to NeonMagenta
        AssistantState.ERROR -> "Offline" to Color(0xFFEF4444)
    }

    Surface(
        color = CardSurface,
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, SubtleBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }
}

@Composable
private fun HolographicVoiceVisualizer(
    state: AssistantState,
    amplitude: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Dynamic responsive amplitude scale
    val dynamicAmpScale = remember(amplitude) {
        1f + (amplitude * 0.4f).coerceIn(0f, 0.5f)
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Outer glowing ripple rings
        Box(
            modifier = Modifier
                .fillMaxSize()
                .scale(pulseScale * dynamicAmpScale)
                .drawBehind {
                    val radius = size.minDimension / 2f
                    val center = Offset(size.width / 2f, size.height / 2f)

                    // Draw outer subtle gradient glow
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                when (state) {
                                    AssistantState.SPEAKING -> NeonMagenta.copy(alpha = 0.35f)
                                    AssistantState.LISTENING -> NeonCyan.copy(alpha = 0.35f)
                                    AssistantState.CONNECTING -> GlowGold.copy(alpha = 0.30f)
                                    else -> NeonViolet.copy(alpha = 0.20f)
                                },
                                Color.Transparent
                            ),
                            center = center,
                            radius = radius
                        ),
                        radius = radius,
                        center = center
                    )

                    // Inner geometric rings
                    drawCircle(
                        color = when (state) {
                            AssistantState.SPEAKING -> NeonMagenta.copy(alpha = 0.6f)
                            AssistantState.LISTENING -> NeonCyan.copy(alpha = 0.6f)
                            else -> NeonViolet.copy(alpha = 0.3f)
                        },
                        radius = radius * 0.85f,
                        center = center,
                        style = Stroke(width = 2.dp.toPx())
                    )

                    drawCircle(
                        color = when (state) {
                            AssistantState.SPEAKING -> NeonViolet.copy(alpha = 0.7f)
                            AssistantState.LISTENING -> NeonMagenta.copy(alpha = 0.7f)
                            else -> NeonCyan.copy(alpha = 0.3f)
                        },
                        radius = radius * 0.65f,
                        center = center,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
        )

        // Core Glowing Orb
        Box(
            modifier = Modifier
                .size(110.dp)
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            when (state) {
                                AssistantState.SPEAKING -> Color(0xFFF43F5E)
                                AssistantState.LISTENING -> Color(0xFF06B6D4)
                                AssistantState.CONNECTING -> Color(0xFFF59E0B)
                                else -> Color(0xFF8B5CF6)
                            },
                            Color(0xFF4C1D95),
                            DeepSpace
                        )
                    )
                )
                .border(
                    width = 2.dp,
                    brush = Brush.sweepGradient(
                        listOf(NeonMagenta, NeonCyan, NeonViolet, NeonMagenta)
                    ),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (state == AssistantState.SPEAKING) {
                // Multi-bar Equalizer animation when speaking
                AnimatedEqualizerBars(amplitude = amplitude)
            } else {
                Icon(
                    imageVector = when (state) {
                        AssistantState.LISTENING -> Icons.Default.Mic
                        AssistantState.CONNECTING -> Icons.Default.AutoAwesome
                        else -> Icons.Default.AutoAwesome
                    },
                    contentDescription = "Voice State Icon",
                    tint = Color.White,
                    modifier = Modifier.size(44.dp)
                )
            }
        }
    }
}

@Composable
private fun AnimatedEqualizerBars(amplitude: Float) {
    val infiniteTransition = rememberInfiniteTransition(label = "equalizer")
    val barCount = 5
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until barCount) {
            val animHeight by infiniteTransition.animateFloat(
                initialValue = 12f,
                targetValue = 38f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 350 + i * 80,
                        easing = FastOutSlowInEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar_$i"
            )
            val effectiveHeight = (animHeight * (1f + amplitude * 1.2f)).coerceIn(10f, 48f)

            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(effectiveHeight.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White)
            )
        }
    }
}

@Composable
private fun DialogueCard(
    state: AssistantState,
    userTranscript: String,
    arushiTranscript: String,
    executedAction: String?,
    onDismissAction: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp)),
        color = CardSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, SubtleBorder)
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            // Executed action feedback chip (Requirement 19 & 29)
            AnimatedVisibility(
                visible = executedAction != null,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E3A8A).copy(alpha = 0.5f))
                        .border(1.dp, NeonCyan.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Action Result",
                            tint = NeonCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = executedAction ?: "",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                    IconButton(
                        onClick = onDismissAction,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = Color.Gray,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // User speech if present
            if (userTranscript.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "You: ",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = NeonCyan,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = userTranscript,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color(0xFFD4D4E8)
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Arushi response
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "Arushi: ",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = NeonMagenta,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = arushiTranscript,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = Color.White,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun QuickActionChipsRow(
    onPromptSelected: (String) -> Unit,
    onTestSpeaker: () -> Unit
) {
    val quickPrompts = listOf(
        "💬 WhatsApp kholo",
        "📞 Call Mom",
        "▶️ Open YouTube",
        "🗺️ Open Maps",
        "✨ Tell me a joke",
        "🇮🇳 Hindi mein baat karo"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        quickPrompts.forEach { prompt ->
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onPromptSelected(prompt) }
                    .testTag("action_chip_${prompt.take(6)}"),
                color = CardSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, SubtleBorder)
            ) {
                Text(
                    text = prompt,
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = Color(0xFFE2E8F0)
                    ),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun ControlsDock(
    state: AssistantState,
    isMicActive: Boolean,
    onMicClick: () -> Unit,
    onSpeakerTestClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Speaker Test Button (Requirement 15)
        OutlinedButton(
            onClick = onSpeakerTestClick,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = NeonCyan
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, SubtleBorder),
            modifier = Modifier.testTag("test_speaker_button")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = "Test Speaker",
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = "Test Audio", fontSize = 12.sp)
        }

        // Main Giant Microphone FAB
        val micGlowBrush = if (isMicActive) {
            Brush.sweepGradient(listOf(NeonMagenta, NeonViolet, NeonCyan, NeonMagenta))
        } else {
            Brush.linearGradient(listOf(NeonViolet, NeonMagenta))
        }

        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(micGlowBrush)
                .clickable { onMicClick() }
                .testTag("main_mic_button"),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(if (isMicActive) NeonMagenta else DeepSpace),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isMicActive) Icons.Default.Mic else Icons.Default.MicOff,
                    contentDescription = if (isMicActive) "Stop Session" else "Start Session",
                    tint = Color.White,
                    modifier = Modifier.size(34.dp)
                )
            }
        }

        // Auto Multi-Language Chip Indicator
        Surface(
            color = CardSurface,
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, SubtleBorder)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🌐 Multi-Lang",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color(0xFFD4D4E8),
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }
    }
}

@Composable
private fun DiagnosticsContent(
    uiState: com.example.arushi.viewmodel.ArushiUiState,
    onClose: () -> Unit,
    onClearLogs: () -> Unit,
    onTestSpeaker: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Audio Pipeline & Diagnostics",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            )
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Metrics Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricCard(
                title = "Audio Input",
                value = "16kHz PCM Mono",
                subtitle = if (uiState.isMicActive) "Microphone Active" else "Mic Inactive",
                color = if (uiState.isMicActive) NeonCyan else Color.Gray,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "Audio Output",
                value = "24kHz AudioTrack",
                subtitle = if (uiState.isSpeaking) "Playing Voice" else "Ready",
                color = if (uiState.isSpeaking) NeonMagenta else NeonViolet,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricCard(
                title = "Gemini Live",
                value = if (uiState.isLiveConnected) "WebSocket Connected" else "Standby",
                subtitle = "gemini-2.5-flash-native-audio",
                color = if (uiState.isLiveConnected) NeonCyan else GlowGold,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "Assistant State",
                value = uiState.state.name,
                subtitle = "Personality: Arushi",
                color = NeonViolet,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Diagnostic Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onTestSpeaker,
                colors = ButtonDefaults.buttonColors(containerColor = NeonViolet),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Test Speaker (440Hz)")
            }

            OutlinedButton(
                onClick = onClearLogs,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, SubtleBorder),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Clear Logs")
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "Live Activity Log (${uiState.logs.size} entries):",
            style = MaterialTheme.typography.labelSmall.copy(color = Color.Gray)
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Log Viewer
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(12.dp)),
            color = DeepSpace,
            border = androidx.compose.foundation.BorderStroke(1.dp, SubtleBorder)
        ) {
            if (uiState.logs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No logs yet. Start speaking or run speaker test.", color = Color.Gray, fontSize = 12.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp)
                ) {
                    items(uiState.logs) { log ->
                        Text(
                            text = log,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = when {
                                    log.contains("ERROR", true) -> Color(0xFFEF4444)
                                    log.contains("SUCCESS", true) || log.contains("acknowledged", true) -> NeonCyan
                                    log.contains("audio", true) -> Color(0xFFC084FC)
                                    else -> Color(0xFF94A3B8)
                                },
                                fontSize = 11.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            ),
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    subtitle: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(12.dp)),
        color = DeepSpace,
        border = androidx.compose.foundation.BorderStroke(1.dp, SubtleBorder)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = title, style = MaterialTheme.typography.labelSmall.copy(color = Color.Gray))
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = value, style = MaterialTheme.typography.bodyMedium.copy(color = color, fontWeight = FontWeight.Bold))
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8), fontSize = 10.sp))
        }
    }
}
