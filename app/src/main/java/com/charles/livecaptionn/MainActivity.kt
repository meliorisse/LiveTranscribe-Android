package com.charles.livecaptionn

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.provider.Settings
import androidx.activity.enableEdgeToEdge
import com.charles.livecaptionn.ui.theme.LiveCaptionTheme
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.charles.livecaptionn.ads.AdUnits
import com.charles.livecaptionn.ads.BannerAd
import com.charles.livecaptionn.service.CaptionForegroundService
import com.charles.livecaptionn.service.MediaProjectionHolder
import com.charles.livecaptionn.settings.AudioSource
import com.charles.livecaptionn.ui.HistoryScreen
import com.charles.livecaptionn.ui.MainScreen
import com.charles.livecaptionn.ui.MainViewModel
import com.charles.livecaptionn.ui.MainViewModelFactory
import com.charles.livecaptionn.ui.OnboardingScreen
import com.charles.livecaptionn.ui.l10n.UiStringsProvider
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {

    companion object {
        const val ACTION_QUICK_START = "com.charles.livecaptionn.action.QUICK_START"
    }

    private var quickStartRequested = false
    private var returnToPreviousApp = false

    private var showHistory by mutableStateOf(false)

    /** Stripe Checkout session ID carried back by the /checkout/success deep link (github flavor). */
    private var pendingCheckoutSessionId by mutableStateOf<String?>(null)

    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCaptioning() else returnToPreviousApp = false
    }

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            MediaProjectionHolder.set(result.resultCode, result.data!!.clone() as Intent)
            startCaptionService(AudioSource.SYSTEM)
        } else {
            returnToPreviousApp = false
            (application as LiveCaptionApp).container.runtimeStore.update {
                it.copy(
                    status = com.charles.livecaptionn.speech.RecognitionStatus.ERROR,
                    lastError = "System-audio capture permission was not granted."
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as LiveCaptionApp
        pendingCheckoutSessionId = extractCheckoutSessionId(intent)
        quickStartRequested = intent.action == ACTION_QUICK_START
        lifecycleScope.launch {
            app.container.runtimeStore.state.collect { runtime ->
                if (returnToPreviousApp && runtime.running) {
                    returnToPreviousApp = false
                    moveTaskToBack(true)
                } else if (returnToPreviousApp && runtime.lastError != null) {
                    returnToPreviousApp = false
                }
            }
        }
        setContent {
            val vm: MainViewModel = viewModel(factory = MainViewModelFactory(app.container, application))
            val settings by app.container.settingsRepository.settingsFlow
                .collectAsStateWithLifecycle(initialValue = null)
            val onboardingComplete = settings?.onboardingComplete ?: true
            UiStringsProvider(app.container) {
                LiveCaptionTheme {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        if (!onboardingComplete) {
                            OnboardingScreen(
                                viewModel = vm,
                                onRequestAudioPermission = { requestAudioPermission() },
                                onOpenOverlaySettings = { vm.openOverlayPermissionSettings(this@MainActivity) },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Column(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
                                // Main content fills remaining space above the
                                // persistent banner ad at the bottom.
                                if (showHistory) {
                                    HistoryScreen(
                                        historyStore = app.container.transcriptHistory,
                                        onBack = { showHistory = false },
                                        modifier = Modifier.weight(1f)
                                    )
                                } else {
                                    MainScreen(
                                        viewModel = vm,
                                        onRequestAudioPermission = { requestAudioPermission() },
                                        onStart = { startCaptioning() },
                                        onStop = { stopCaptionService() },
                                        onOpenOverlaySettings = { vm.openOverlayPermissionSettings(this@MainActivity) },
                                        onHistory = { showHistory = true },
                                        pendingCheckoutSessionId = pendingCheckoutSessionId,
                                        onCheckoutSessionConsumed = { pendingCheckoutSessionId = null },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (AdUnits.ENABLED && AdUnits.BANNER.isNotBlank()) {
                                    BannerAd()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        quickStartRequested = intent.action == ACTION_QUICK_START
        extractCheckoutSessionId(intent)?.let { pendingCheckoutSessionId = it }
    }

    override fun onResume() {
        super.onResume()
        if (!quickStartRequested) return
        quickStartRequested = false
        // Consume once: rotating or returning from a permission dialog must not restart capture.
        intent.action = Intent.ACTION_MAIN
        lifecycleScope.launch {
            val app = application as LiveCaptionApp
            val settings = app.container.settingsRepository.settingsFlow.first()
            if (!settings.onboardingComplete || !Settings.canDrawOverlays(this@MainActivity)) {
                android.widget.Toast.makeText(
                    this@MainActivity, R.string.quick_caption_setup, android.widget.Toast.LENGTH_LONG
                ).show()
                return@launch
            }
            if (app.container.runtimeStore.state.value.running) {
                moveTaskToBack(true)
                return@launch
            }
            returnToPreviousApp = true
            startCaptioning()
        }
    }

    private fun extractCheckoutSessionId(intent: Intent?): String? {
        val data = intent?.data ?: return null
        if (data.scheme != "livecaptionn" || data.host != "premium-return") return null
        return data.getQueryParameter("session_id")
    }

    private fun requestAudioPermission() {
        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun startCaptioning() {
        val app = application as LiveCaptionApp
        lifecycleScope.launch {
            if (!Settings.canDrawOverlays(this@MainActivity)) {
                app.container.runtimeStore.update {
                    it.copy(
                        status = com.charles.livecaptionn.speech.RecognitionStatus.ERROR,
                        lastError = "Overlay permission is required. Enable it in system settings and try again."
                    )
                }
                return@launch
            }
            val settings = app.container.settingsRepository.settingsFlow.first()
            when (settings.audioSource) {
                AudioSource.SYSTEM -> {
                    val mpManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    val captureIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        mpManager.createScreenCaptureIntent(
                            MediaProjectionConfig.createConfigForDefaultDisplay()
                        )
                    } else {
                        mpManager.createScreenCaptureIntent()
                    }
                    mediaProjectionLauncher.launch(captureIntent)
                }
                AudioSource.MIC -> {
                    val hasMic = ContextCompat.checkSelfPermission(
                        this@MainActivity, Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    if (hasMic) {
                        startCaptionService(AudioSource.MIC)
                    } else {
                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            }
        }
    }

    private fun startCaptionService(audioSource: AudioSource) {
        val intent = Intent(this, CaptionForegroundService::class.java).apply {
            action = CaptionForegroundService.ACTION_START
            // Passed eagerly so the service can call startForeground() with the
            // correct service type synchronously inside Android's 5-second
            // post-startForegroundService deadline (DataStore reads were racing
            // it on slow devices, crashing the app).
            putExtra(CaptionForegroundService.EXTRA_AUDIO_SOURCE, audioSource.name)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopCaptionService() {
        val intent = Intent(this, CaptionForegroundService::class.java).apply {
            action = CaptionForegroundService.ACTION_STOP
        }
        startService(intent)
    }
}
