package com.introcarbon.beatrice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.introcarbon.beatrice.jni.BeatriceJni
import com.introcarbon.beatrice.model.BeatriceModelInfo
import com.introcarbon.beatrice.model.ModelManager
import com.introcarbon.beatrice.service.VoiceChangerService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val recordGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        if (!recordGranted) {
            Toast.makeText(this, "Microphone permission is required for voice conversion!", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkPermissions()

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF64B5F6),
                    secondary = Color(0xFF81C784),
                    background = Color(0xFF121212),
                    surface = Color(0xFF1E1E1E),
                    onBackground = Color.White,
                    onSurface = Color.White
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BeatriceMainScreen()
                }
            }
        }
    }

    private fun checkPermissions() {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun BeatriceMainScreen() {
        val coroutineScope = rememberCoroutineScope()
        val scrollState = rememberScrollState()

        var selectedModel by remember { mutableStateOf(ModelManager.AVAILABLE_MODELS[0]) }
        var isRunning by remember { mutableStateOf(VoiceChangerService.isServiceRunning) }
        var pitchShift by remember { mutableFloatStateOf(selectedModel.defaultPitch) }
        var noiseGateDb by remember { mutableFloatStateOf(-45.0f) }
        var isDownloading by remember { mutableStateOf(false) }
        var downloadProgress by remember { mutableFloatStateOf(0.0f) }

        var installedModelIds by remember {
            mutableStateOf(ModelManager.getInstalledModelIds(this@MainActivity))
        }

        var inputLevel by remember { mutableFloatStateOf(0.0f) }
        var latencyMs by remember { mutableFloatStateOf(0.0f) }

        DisposableEffect(Unit) {
            installedModelIds = ModelManager.getInstalledModelIds(this@MainActivity)
            onDispose {}
        }

        // Live telemetry loop
        LaunchedEffect(isRunning) {
            while (isRunning) {
                if (!VoiceChangerService.isServiceRunning) {
                    isRunning = false
                    break
                }
                inputLevel = BeatriceJni.getInputLevel()
                latencyMs = BeatriceJni.getLatencyMs()
                delay(50)
            }
            inputLevel = 0.0f
            latencyMs = 0.0f
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Beatrice 2 Voice Changer", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            Text(
                                if (isRunning) "Active (Foreground Service)" else "Ready (Zero Background Overhead)",
                                fontSize = 12.sp,
                                color = if (isRunning) Color(0xFF81C784) else Color.Gray
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF181818),
                        titleContentColor = Color.White
                    ),
                    actions = {
                        IconButton(onClick = {
                            installedModelIds = ModelManager.getInstalledModelIds(this@MainActivity)
                            Toast.makeText(this@MainActivity, "Models refreshed", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White)
                        }
                    }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. Live VU Meter & Latency Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Microphone Activity", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            Text(
                                if (isRunning) "Buffer Latency: ${String.format("%.1f", latencyMs)} ms" else "Engine Idle",
                                fontSize = 12.sp,
                                color = if (isRunning) Color(0xFF64B5F6) else Color.Gray
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))

                        // Dynamic audio level bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF2C2C2C))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fraction = (inputLevel * 4.0f).coerceIn(0.0f, 1.0f))
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (inputLevel > 0.05f) Color(0xFF81C784) else Color(0xFF64B5F6))
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 2. Big Start / Stop Button
                val isInstalled = installedModelIds.contains(selectedModel.id)

                Button(
                    onClick = {
                        if (!isInstalled) {
                            coroutineScope.launch {
                                isDownloading = true
                                val success = ModelManager.downloadAndInstallModel(
                                    this@MainActivity,
                                    selectedModel
                                ) { progress ->
                                    downloadProgress = progress
                                }
                                isDownloading = false
                                installedModelIds = ModelManager.getInstalledModelIds(this@MainActivity)
                                if (success) {
                                    Toast.makeText(this@MainActivity, "Model installed!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(this@MainActivity, "Failed to download model.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            if (!isRunning) {
                                val modelDir = ModelManager.getModelDir(this@MainActivity, selectedModel.id)
                                val intent = Intent(this@MainActivity, VoiceChangerService::class.java).apply {
                                    action = VoiceChangerService.ACTION_START
                                    putExtra(VoiceChangerService.EXTRA_MODEL_PATH, modelDir.absolutePath)
                                    putExtra(VoiceChangerService.EXTRA_MODEL_NAME, selectedModel.name)
                                    putExtra(VoiceChangerService.EXTRA_PITCH, pitchShift)
                                    putExtra(VoiceChangerService.EXTRA_GATE, noiseGateDb)
                                }
                                ContextCompat.startForegroundService(this@MainActivity, intent)
                                isRunning = true
                            } else {
                                val intent = Intent(this@MainActivity, VoiceChangerService::class.java).apply {
                                    action = VoiceChangerService.ACTION_STOP
                                }
                                startService(intent)
                                isRunning = false
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (!isInstalled) Color(0xFFFFA726) else if (isRunning) Color(0xFFE57373) else Color(0xFF64B5F6)
                    )
                ) {
                    if (isDownloading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Downloading Model (${(downloadProgress * 100).toInt()}%)...", fontSize = 16.sp)
                    } else if (!isInstalled) {
                        Icon(Icons.Default.Download, contentDescription = "Download")
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Install ${selectedModel.name}", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    } else if (isRunning) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop")
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Stop Voice Conversion", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Start")
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Start Voice Conversion", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 3. Model Selector Cards
                Text(
                    "Target Voice Models",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(10.dp))

                ModelManager.AVAILABLE_MODELS.forEach { model ->
                    val isCurrent = model.id == selectedModel.id
                    val isModelReady = installedModelIds.contains(model.id)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable {
                                selectedModel = model
                                pitchShift = model.defaultPitch
                                if (isRunning) {
                                    if (installedModelIds.contains(model.id)) {
                                        // Switch model on the fly
                                        val modelDir = ModelManager.getModelDir(this@MainActivity, model.id)
                                        val intent = Intent(this@MainActivity, VoiceChangerService::class.java).apply {
                                            action = VoiceChangerService.ACTION_START
                                            putExtra(VoiceChangerService.EXTRA_MODEL_PATH, modelDir.absolutePath)
                                            putExtra(VoiceChangerService.EXTRA_MODEL_NAME, model.name)
                                            putExtra(VoiceChangerService.EXTRA_PITCH, pitchShift)
                                            putExtra(VoiceChangerService.EXTRA_GATE, noiseGateDb)
                                        }
                                        ContextCompat.startForegroundService(this@MainActivity, intent)
                                    } else {
                                        Toast.makeText(this@MainActivity, "Please install ${model.name} first.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isCurrent) Color(0xFF263238) else Color(0xFF1E1E1E)
                        ),
                        border = if (isCurrent) CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF64B5F6))) else null
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(model.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text(model.description, fontSize = 12.sp, color = Color.Gray)
                            }
                            if (isModelReady) {
                                Icon(Icons.Default.CheckCircle, contentDescription = "Ready", tint = Color(0xFF81C784))
                            } else {
                                Icon(Icons.Default.CloudDownload, contentDescription = "Download Needed", tint = Color.Gray)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 4. Real-time Acoustic Tuning
                Text(
                    "Real-Time DSP Tuning",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(10.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Pitch Shift Slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Pitch Tune Slider", fontSize = 14.sp)
                            Text("${String.format("%.1f", pitchShift)} semitones", fontSize = 14.sp, color = Color(0xFF64B5F6))
                        }
                        Slider(
                            value = pitchShift,
                            onValueChange = {
                                pitchShift = it
                                if (isRunning) {
                                    val intent = Intent(this@MainActivity, VoiceChangerService::class.java).apply {
                                        action = VoiceChangerService.ACTION_SET_PITCH
                                        putExtra(VoiceChangerService.EXTRA_PITCH, pitchShift)
                                    }
                                    startService(intent)
                                }
                            },
                            valueRange = -12.0f..12.0f,
                            steps = 47
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Noise Gate Slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Input Noise Gate", fontSize = 14.sp)
                            Text("${noiseGateDb.toInt()} dBFS", fontSize = 14.sp, color = Color(0xFF81C784))
                        }
                        Slider(
                            value = noiseGateDb,
                            onValueChange = {
                                noiseGateDb = it
                                if (isRunning) {
                                    val intent = Intent(this@MainActivity, VoiceChangerService::class.java).apply {
                                        action = VoiceChangerService.ACTION_SET_GATE
                                        putExtra(VoiceChangerService.EXTRA_GATE, noiseGateDb)
                                    }
                                    startService(intent)
                                }
                            },
                            valueRange = -60.0f..-20.0f,
                            steps = 39
                        )
                    }
                }
            }
        }
    }
}
