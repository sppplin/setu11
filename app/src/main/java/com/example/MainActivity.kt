package com.example

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import android.speech.RecognizerIntent
import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.api.GeminiClient
import com.example.data.AiChatMessage
import com.example.data.JobApplication
import com.example.data.UserProfile
import com.example.ui.theme.*
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.MockJob
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {
    private var tts: TextToSpeech? = null
    var isTtsReady by mutableStateOf(false)
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Setup Accessibility Speech
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("hi", "IN")
                isTtsReady = true
            }
        }

        setContent {
            MyApplicationTheme {
                MainAppScreen(
                    onSpeak = { text ->
                        if (isTtsReady) {
                            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
                        } else {
                            Toast.makeText(this, "साउंड चालू हो रहा है...: $text", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(
    viewModel: MainViewModel = viewModel(),
    onSpeak: (String) -> Unit
) {
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val appliedJobs by viewModel.appliedJobs.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var isBilingualHindi by remember { mutableStateOf(true) }

    // Navigation subscreen switches
    var activeTrackingJob by remember { mutableStateOf<MockJob?>(null) }
    var selectedJobForDetail by remember { mutableStateOf<MockJob?>(null) }
    var showAiCounselorSheet by remember { mutableStateOf(false) }

    var incomingNearbyJob by remember { mutableStateOf<MockJob?>(null) }
    var showAnotherWorkerAcceptedDialog by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.incomingJobRequest.collect { job ->
            incomingNearbyJob = job
        }
    }

    // Dynamic Header Title
    val currentHeaderTitle = when {
        activeTrackingJob != null -> if (isBilingualHindi) "लोकेशन ट्रैक" else "Track Location"
        (userProfile.userRoleType == "Employer" || userProfile.userRoleType == "Contractor") -> {
            when (currentTab) {
                0 -> if (isBilingualHindi) "मजदूर रडार" else "Worker Radar"
                1 -> if (isBilingualHindi) "पोस्ट किये काम" else "Posted Jobs"
                else -> if (isBilingualHindi) "मालिक प्रोफाइल" else "Employer Profile"
            }
        }
        else -> {
            when (currentTab) {
                0 -> if (isBilingualHindi) "नजदीकी काम" else "Jobs Near You"
                1 -> if (isBilingualHindi) "कमाई" else "My Earnings"
                else -> if (isBilingualHindi) "अपनी प्रोफाइल बनाएं" else "Build Profile"
            }
        }
    }

    if (userProfile.phone.isEmpty() || userProfile.name.isEmpty()) {
        LoginSignupScreen(
            viewModel = viewModel,
            isHindi = isBilingualHindi,
            onLanguageToggle = {
                isBilingualHindi = !isBilingualHindi
                onSpeak(if (isBilingualHindi) "हिंदी भाषा चुनी गई" else "English language selected")
            },
            onSpeak = onSpeak
        )
    } else {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (activeTrackingJob != null) {
                                IconButton(onClick = { activeTrackingJob = null }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                            Text(
                                text = currentHeaderTitle,
                                fontWeight = FontWeight.Black,
                                fontSize = 23.sp,
                                color = Color.White
                            )
                        }

                        // Easy Hindi / English toggle button for low literate audience
                        TextButton(
                            onClick = {
                                isBilingualHindi = !isBilingualHindi
                                val speakText = if (isBilingualHindi) "हिंदी भाषा चुनी गई" else "English language selected"
                                onSpeak(speakText)
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                        ) {
                            Text(
                                text = if (isBilingualHindi) "हिन्दी / Eng" else "Eng / हिन्दी",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                modifier = Modifier
                                    .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = HeaderBg,
                    titleContentColor = Color.White
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color.White,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .drawBehind {
                        drawLine(
                            color = GrayBorder,
                            start = Offset(0f, 0f),
                            end = Offset(size.width, 0f),
                            strokeWidth = 2f
                        )
                    }
            ) {
                if (userProfile.userRoleType == "Employer" || userProfile.userRoleType == "Contractor") {
                    // --- RECRUITER / EMPLOYER TABS ---
                    // Tab 0: Find Workers
                    NavigationBarItem(
                        selected = currentTab == 0 && activeTrackingJob == null,
                        onClick = {
                            activeTrackingJob = null
                            viewModel.selectTab(0)
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Group,
                                contentDescription = "मजदूर रडार",
                                tint = if (currentTab == 0 && activeTrackingJob == null) OrangePrimary else Color.Gray,
                                modifier = Modifier.size(32.dp)
                            )
                        },
                        label = {
                            Text(
                                text = if (isBilingualHindi) "मजदूर खोजें" else "Workers",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (currentTab == 0 && activeTrackingJob == null) OrangePrimary else Color.Gray
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = ContrastLight
                        )
                    )

                    // Tab 1: Post Job
                    NavigationBarItem(
                        selected = currentTab == 1,
                        onClick = {
                            activeTrackingJob = null
                            viewModel.selectTab(1)
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Assignment,
                                contentDescription = "काम पोस्ट",
                                tint = if (currentTab == 1) OrangePrimary else Color.Gray,
                                modifier = Modifier.size(32.dp)
                            )
                        },
                        label = {
                            Text(
                                text = if (isBilingualHindi) "काम पोस्ट" else "My Jobs",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (currentTab == 1) OrangePrimary else Color.Gray
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = ContrastLight
                        )
                    )

                    // Tab 2: Employer Profile
                    NavigationBarItem(
                        selected = currentTab == 2,
                        onClick = {
                            activeTrackingJob = null
                            viewModel.selectTab(2)
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Business,
                                contentDescription = "मालिक",
                                tint = if (currentTab == 2) OrangePrimary else Color.Gray,
                                modifier = Modifier.size(32.dp)
                            )
                        },
                        label = {
                            Text(
                                text = if (isBilingualHindi) "मालिक प्रोफाइल" else "Employer",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (currentTab == 2) OrangePrimary else Color.Gray
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = ContrastLight
                        )
                    )
                } else {
                    // --- WORKER / SEEKER TABS ---
                    // Tab 0: Find Work
                    NavigationBarItem(
                        selected = currentTab == 0 && activeTrackingJob == null,
                        onClick = {
                            activeTrackingJob = null
                            viewModel.selectTab(0)
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = "काम ढूंढ़ें",
                                tint = if (currentTab == 0 && activeTrackingJob == null) OrangePrimary else Color.Gray,
                                modifier = Modifier.size(32.dp)
                            )
                        },
                        label = {
                            Text(
                                text = if (isBilingualHindi) "काम ढूंढ़ें" else "Find Work",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (currentTab == 0 && activeTrackingJob == null) OrangePrimary else Color.Gray
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = ContrastLight
                        )
                    )

                    // Tab 1: Earnings
                    NavigationBarItem(
                        selected = currentTab == 1,
                        onClick = {
                            activeTrackingJob = null
                            viewModel.selectTab(1)
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.CurrencyRupee,
                                contentDescription = "कमाई",
                                tint = if (currentTab == 1) OrangePrimary else Color.Gray,
                                modifier = Modifier.size(32.dp)
                            )
                        },
                        label = {
                            Text(
                                text = if (isBilingualHindi) "कमाई" else "Earnings",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (currentTab == 1) OrangePrimary else Color.Gray
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = ContrastLight
                        )
                    )

                    // Tab 2: Profile Setup
                    NavigationBarItem(
                        selected = currentTab == 2,
                        onClick = {
                            activeTrackingJob = null
                            viewModel.selectTab(2)
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "प्रोफाइल",
                                tint = if (currentTab == 2) OrangePrimary else Color.Gray,
                                modifier = Modifier.size(32.dp)
                            )
                        },
                        label = {
                            Text(
                                text = if (isBilingualHindi) "प्रोफाइल" else "Profile",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (currentTab == 2) OrangePrimary else Color.Gray
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = ContrastLight
                        )
                    )
                }
            }
        },
        floatingActionButton = {
            // Highly helpful floating career assistant chatbot
            ExtendedFloatingActionButton(
                onClick = { showAiCounselorSheet = true },
                containerColor = OrangePrimary,
                contentColor = Color.White,
                shape = RoundedCornerShape(24.dp),
                elevation = FloatingActionButtonDefaults.elevation(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SupportAgent, contentDescription = "AI Buddy")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isBilingualHindi) "करियर मित्र AI" else "AI Chat Mitra",
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp
                    )
                }
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = SandyBg
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AnimatedContent(
                    targetState = if (activeTrackingJob != null) -1 else currentTab,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "tab_views"
                ) { viewState ->
                    when (viewState) {
                        -1 -> {
                            // Location Track subscreen shown in Image 4
                            activeTrackingJob?.let { job ->
                                LocationTrackScreen(
                                    viewModel = viewModel,
                                    job = job,
                                    isHindi = isBilingualHindi,
                                    onSpeak = onSpeak,
                                    onComplete = {
                                        activeTrackingJob = null
                                        viewModel.selectTab(1) // Go to Earnings tab!
                                    }
                                )
                            }
                        }
                        0 -> {
                            if (userProfile.userRoleType == "Employer" || userProfile.userRoleType == "Contractor") {
                                EmployerRadarTabScreen(
                                    viewModel = viewModel,
                                    isHindi = isBilingualHindi,
                                    onSpeak = onSpeak
                                )
                            } else {
                                // Image 2: नजदीकी काम (Jobs list)
                                JobsTabScreen(
                                    viewModel = viewModel,
                                    isHindi = isBilingualHindi,
                                    onJobClicked = { selectedJobForDetail = it },
                                    onAcceptJob = { job ->
                                        viewModel.applyToJob(job)
                                        onSpeak(if (isBilingualHindi) "काम स्वीकार किया गया। लोकेशन ट्रैक चालू है!" else "Job Accepted! Track path is online.")
                                        activeTrackingJob = job
                                    },
                                    onSpeak = onSpeak
                                )
                            }
                        }
                        1 -> {
                            if (userProfile.userRoleType == "Employer" || userProfile.userRoleType == "Contractor") {
                                EmployerJobsTabScreen(
                                    viewModel = viewModel,
                                    isHindi = isBilingualHindi,
                                    onSpeak = onSpeak
                                )
                            } else {
                                // Image 5: प्रोफाइल और कमाई dashboard
                                EarningsTabScreen(
                                    viewModel = viewModel,
                                    userProfile = userProfile,
                                    isHindi = isBilingualHindi,
                                    onSpeak = onSpeak
                                )
                            }
                        }
                        2 -> {
                            if (userProfile.userRoleType == "Employer" || userProfile.userRoleType == "Contractor") {
                                EmployerProfileTabScreen(
                                    viewModel = viewModel,
                                    isHindi = isBilingualHindi,
                                    onSpeak = onSpeak
                                )
                            } else {
                                // Image 1: अपनी प्रोफाइल बनाएं (Registration fields)
                                ProfileTabScreen(
                                    viewModel = viewModel,
                                    isHindi = isBilingualHindi,
                                    onSpeak = onSpeak
                                )
                            }
                        }
                    }
                }

                // Image 3: काम का विवरण dialog with Speaker Play assistance
                selectedJobForDetail?.let { job ->
                    JobDetailDialog(
                        job = job,
                        isHindi = isBilingualHindi,
                        onCancel = { selectedJobForDetail = null },
                        onAccept = {
                            viewModel.applyToJob(job)
                            selectedJobForDetail = null
                            onSpeak(if (isBilingualHindi) "काम स्वीकार किया गया। लोकेशन ट्रैक चालू है!" else "Job Accepted! Track path is online.")
                            activeTrackingJob = job
                        },
                        onSpeak = onSpeak
                    )
                }

                // AI Counselor overlay dialog to keep Server-Side AI active and fully interactive
                if (showAiCounselorSheet) {
                    AiCounselorOverlay(
                        viewModel = viewModel,
                        isHindi = isBilingualHindi,
                        onClose = { showAiCounselorSheet = false }
                    )
                }

                // Realistic incoming nearby job popup matching Uber/Ola
                incomingNearbyJob?.let { job ->
                    IncomingJobRequestScreen(
                        job = job,
                        isHindi = isBilingualHindi,
                        onAccept = {
                            viewModel.applyToJob(job)
                            incomingNearbyJob = null
                            onSpeak(if (isBilingualHindi) "बधाई हो! काम स्वीकार किया गया। लोकेशन ट्रैक चालू है!" else "Job Accepted! Track path is online.")
                            activeTrackingJob = job
                        },
                        onDecline = {
                            incomingNearbyJob = null
                            onSpeak(if (isBilingualHindi) "काम हटा दिया गया।" else "Job offer declined.")
                        },
                        onAnotherPersonAccepted = {
                            incomingNearbyJob = null
                            showAnotherWorkerAcceptedDialog = true
                            onSpeak(if (isBilingualHindi) "माफ़ी चाहते हैं, दूसरे मजदूर ने काम स्वीकार कर लिया।" else "Sorry, another worker accepted this job.")
                        },
                        onSpeak = onSpeak
                    )
                }

                if (showAnotherWorkerAcceptedDialog) {
                    Dialog(onDismissRequest = { showAnotherWorkerAcceptedDialog = false }) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.padding(16.dp),
                            border = BorderStroke(2.dp, DeclineRed)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.Group, contentDescription = null, tint = DeclineRed, modifier = Modifier.size(54.dp))
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = if (isBilingualHindi) "काम समाप्त हो गया!" else "Job No Longer Available!",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 18.sp,
                                    color = DarkCharcoal,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if (isBilingualHindi) {
                                        "दूसरे नजदीकी मजदूर ने इस काम को स्वीकार कर लिया है!"
                                    } else {
                                        "Another worker nearby accepted this job just now!"
                                    },
                                    fontSize = 14.sp,
                                    color = Color.Gray,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                                Button(
                                    onClick = { showAnotherWorkerAcceptedDialog = false },
                                    colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary)
                                ) {
                                    Text(if (isBilingualHindi) "ठीक है" else "Ok", color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    }
}

// --- TAB SCREEN 0: नजदीकी काम (FIND JOB SCREEN) ---
@Composable
fun JobsTabScreen(
    viewModel: MainViewModel,
    isHindi: Boolean,
    onJobClicked: (MockJob) -> Unit,
    onAcceptJob: (MockJob) -> Unit,
    onSpeak: (String) -> Unit
) {
    val filteredJobs by viewModel.filteredJobs.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategoryFilter.collectAsStateWithLifecycle()

    // Pre-select category based on saved preferred role
    LaunchedEffect(userProfile) {
        if (userProfile.preferredRole.isNotEmpty() && viewModel.selectedCategoryFilter.value == "All") {
            viewModel.updateSearchFilters("", userProfile.preferredRole, "All")
        }
    }

    // Simulated live state to support declining (removing) jobs from list dynamically
    var trackingDeclinedJobs by remember { mutableStateOf(setOf<String>()) }
    var showOnlySuperNearby by remember { mutableStateOf(false) }

    val displayListRaw = filteredJobs.filterNot { trackingDeclinedJobs.contains(it.id) }
    val displayList = remember(displayListRaw, showOnlySuperNearby) {
        if (showOnlySuperNearby) {
            displayListRaw.filter { job ->
                val distanceKm = job.distance.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: 5.0
                distanceKm <= 3.0
            }
        } else {
            displayListRaw
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
        // Interactive Proximity Control Center
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.5.dp, OrangePrimary)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(OrangePrimary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.MyLocation, contentDescription = null, tint = OrangePrimary, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isHindi) "लाइव नजदीकी रडार (Nearby Radar)" else "Live Nearby Job Radar",
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp,
                            color = OrangeClassic
                        )
                    }
                    
                    // Trigger Simulated Job Card Button
                    Button(
                        onClick = {
                            onSpeak(if (isHindi) "नजदीकी ग्राहक से लाइव काम की खोज जारी है..." else "Scanning nearby active clients...")
                            viewModel.postSimulatedNearbyJob()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text(
                            text = if (isHindi) "⚡ लाइव काम भेजें" else "⚡ Send Live Offer",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (isHindi) "दूरी के आधार पर केवल नजदीकी काम ढूंढें" else "Only show super nearby jobs (< 3 km)",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.Bold
                    )
                    
                    // Quick filter nearby toggle
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isHindi) "३ किमी से कम" else "< 3 km",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = if (showOnlySuperNearby) AcceptGreen else Color.Gray,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        Switch(
                            checked = showOnlySuperNearby,
                            onCheckedChange = { 
                                showOnlySuperNearby = it
                                onSpeak(if (it) {
                                    if (isHindi) "केवल ३ किमी से कम दूरी के काम दिखाए जा रहे हैं" else "Showing tasks under three kilometers only."
                                } else {
                                    if (isHindi) "सभी नजदीकी काम दिखाए जा रहे हैं" else "Showing all nearby tasks."
                                })
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = AcceptGreen,
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = GrayBorder
                            ),
                            modifier = Modifier.scale(0.7f)
                        )
                    }
                }
            }
        }

        // Accessibility alert card
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            colors = CardDefaults.cardColors(containerColor = ContrastLight),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, BorderColor)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Campaign,
                    contentDescription = "Alert",
                    tint = OrangePrimary,
                    modifier = Modifier.size(34.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = if (isHindi) "📣 आवाज से सुनने के लिए काम पर छुएं!" else "📣 Tap any job to listen details speak out!",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        color = OrangeClassic
                    )
                }
            }
        }

        // Category Filter Row
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            val categories = listOf("All", "मजदूर", "मिस्त्री", "बिजली मिस्त्री", "प्लंबर")
            items(categories) { cat ->
                val isSelected = selectedCategory == cat
                val label = when (cat) {
                    "All" -> if (isHindi) "सभी काम" else "All Jobs"
                    "मजदूर" -> if (isHindi) "मजदूर" else "Helper/Labor"
                    "मिस्त्री" -> if (isHindi) "मिस्त्री" else "Carpenter/Mason"
                    "बिजली मिस्त्री" -> if (isHindi) "बिजली मिस्त्री" else "Electrician"
                    "प्लंबर" -> if (isHindi) "प्लंबर" else "Plumber"
                    else -> cat
                }
                Surface(
                    modifier = Modifier
                        .clickable {
                            viewModel.updateSearchFilters("", cat, "All")
                            val announce = if (isHindi) "$label चुना गया" else "Selected $label"
                            onSpeak(announce)
                        },
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSelected) OrangePrimary else Color.White,
                    border = BorderStroke(1.dp, if (isSelected) OrangePrimary else BorderColor),
                    shadowElevation = 1.dp
                ) {
                    Text(
                        text = label,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        color = if (isSelected) Color.White else DarkCharcoal
                    )
                }
            }
        }

        if (displayList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (isHindi) "कोई नया काम नहीं मिला। नए काम देखने के लिए इंतजार करें।" else "No active jobs found right now.",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = Color.Gray
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(displayList) { job ->
                    JobCardItem(
                        job = job,
                        isHindi = isHindi,
                        onCardTouch = { onJobClicked(job) },
                        onAccept = { onAcceptJob(job) },
                        onDecline = {
                            trackingDeclinedJobs = trackingDeclinedJobs + job.id
                            onSpeak(if (isHindi) "काम अस्वीकार कर दिया गया है।" else "Job declined.")
                        }
                    )
                }
            }
        }
    }
}

// --- JOB CARD WITH SLIDER DETAILS & GIANT TOUCH BUTTONS ---
@Composable
fun JobCardItem(
    job: MockJob,
    isHindi: Boolean,
    onCardTouch: () -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    val subtitleMap = if (isHindi) job.hindiTitle else job.title
    val employerName = if (isHindi) job.hindiCompany else job.company
    val wageAmount = if (isHindi) job.hindiSalary else job.salary
    val distanceText = if (isHindi) job.hindiDistance else job.distance
    val daysCount = if (isHindi) "${job.tenureDays} दिन" else "${job.tenureDays} Days"

    val iconVisual = when {
        job.category.contains("मजदूर") -> Icons.Default.Engineering
        job.category.contains("मिस्त्री") -> Icons.Default.Construction
        job.category.contains("बिजली मिस्त्री") -> Icons.Default.FlashOn
        job.category.contains("प्लंबर") -> Icons.Default.WaterDrop
        else -> Icons.Default.Engineering
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCardTouch),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(ContrastLight)
                            .border(2.dp, OrangePrimary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = iconVisual,
                            contentDescription = "Avatar",
                            tint = OrangePrimary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = subtitleMap,
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            color = DarkCharcoal,
                            maxLines = 2,
                            lineHeight = 22.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = employerName,
                            fontSize = 14.sp,
                            color = OrangeClassic,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = distanceText,
                            fontSize = 14.sp,
                            color = Color.Gray,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Daily rate payment panel
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = wageAmount,
                        fontWeight = FontWeight.Black,
                        fontSize = 19.sp,
                        color = AcceptGreen
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Tenure visual slider bar matching mockups
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isHindi) "समय:" else "Tenure:",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray,
                    modifier = Modifier.width(60.dp)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(GrayBorder)
                ) {
                    val progressFraction = when (job.tenureDays) {
                        2 -> 0.25f
                        3 -> 0.45f
                        5 -> 0.65f
                        else -> 0.9f
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progressFraction)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(5.dp))
                            .background(OrangePrimary)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = daysCount,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    color = OrangeClassic
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Two high contrast accessible giant touch buttons (मना करें / स्वीकार करें)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Button(
                    onClick = onDecline,
                    colors = ButtonDefaults.buttonColors(containerColor = DeclineRed),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Decline", tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isHindi) "मना करें" else "Decline",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    }
                }

                Button(
                    onClick = onAccept,
                    colors = ButtonDefaults.buttonColors(containerColor = AcceptGreen),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Accept", tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isHindi) "स्वीकार करें" else "Accept",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

// --- TAB SCREEN 1: लोकेशन ट्रैक (TRACKING SCREEN INTERACTIVE CANVAS DRAWING) ---
@Composable
fun LocationTrackScreen(
    viewModel: MainViewModel,
    job: MockJob,
    isHindi: Boolean,
    onSpeak: (String) -> Unit,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    var isRecordingLocation by remember { mutableStateOf(false) }

    val supervisorText = if (isHindi) job.hindiCompany else job.company
    val wageAmount = if (isHindi) job.hindiSalary else job.salary
    val distanceText = if (isHindi) job.hindiDistance else job.distance

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (isHindi) "🚩 काम पर जाने का रास्ता" else "🚩 Route towards Active Job",
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            color = DarkCharcoal,
            modifier = Modifier.align(Alignment.Start)
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Image 4: Canvas Map Drawing representing route towards destination!
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .border(2.dp, OrangePrimary, RoundedCornerShape(16.dp))
                .clickable {
                    launchGoogleMapsNavigation(context, job.location)
                },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Drawing custom high contrast vector map path inside canvas
                Canvas(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                    val width = size.width
                    val height = size.height

                    // Background street grid simulation lines
                    drawLine(
                        color = Color(0xFFEEEEEE),
                        start = Offset(0f, height * 0.4f),
                        end = Offset(width, height * 0.4f),
                        strokeWidth = 16f
                    )
                    drawLine(
                        color = Color(0xFFEEEEEE),
                        start = Offset(width * 0.3f, 0f),
                        end = Offset(width * 0.3f, height),
                        strokeWidth = 16f
                    )

                    // Draw the primary active green dotted indicator route
                    val pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
                    val routePoints = listOf(
                        Offset(width * 0.15f, height * 0.15f),
                        Offset(width * 0.5f, height * 0.3f),
                        Offset(width * 0.65f, height * 0.65f),
                        Offset(width * 0.85f, height * 0.85f)
                    )

                    for (i in 0 until routePoints.size - 1) {
                        drawLine(
                            color = AcceptGreen,
                            start = routePoints[i],
                            end = routePoints[i + 1],
                            strokeWidth = 10f,
                            pathEffect = pathEffect
                        )
                    }
                }

                // Overlay Home and Worker pin icons at start & endpoints
                Box(modifier = Modifier.fillMaxSize()) {
                    // Home Pin (Top Left)
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 24.dp, top = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(AcceptGreen),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Home, contentDescription = "Home", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Text("घर (Home)", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = DarkCharcoal)
                    }

                    // Map Text Overlay markings
                    Text(
                        text = "Jogeshwari-Vikhroli Link Rd",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .background(Color.White.copy(alpha = 0.8f))
                            .padding(4.dp)
                    )

                    // Target Pinpoint Worker Avatar (Bottom Right)
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 24.dp, bottom = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(OrangePrimary)
                                .border(2.dp, Color.White, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Engineering, contentDescription = "Worker", tint = Color.White)
                        }
                        Text(if (isHindi) "काम का पता" else "Workplace", fontWeight = FontWeight.Black, fontSize = 12.sp, color = OrangeClassic)
                    }

                    // Floating GPS/Maps Ola/Uber Navigation Pill
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color.Black.copy(alpha = 0.85f))
                            .clickable {
                                launchGoogleMapsNavigation(context, job.location)
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Navigation, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isHindi) "रास्ता दिखाएं (Start Map Navigation)" else "Navigate on Google Maps",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Worker Info Panel in Route Page
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, BorderColor)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(ContrastLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Person, contentDescription = "Supervisor", tint = OrangePrimary, modifier = Modifier.size(32.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = supervisorText,
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                color = DarkCharcoal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = wageAmount,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = AcceptGreen
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Green Active Status Checked badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(AcceptGreen.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "Active", tint = AcceptGreen, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isHindi) "काम चालू" else "On Duty",
                                fontWeight = FontWeight.Bold,
                                color = AcceptGreen,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = BorderColor)
                Spacer(modifier = Modifier.height(12.dp))

                // Distance duration high visibility highlights
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isHindi) "$distanceText (10 मिनट दूर)" else "$distanceText (10 mins away)",
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp,
                        color = OrangeClassic
                    )
                    Text(
                        text = if (isHindi) "ठेकेदार: $supervisorText" else "Owner: $supervisorText",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color.Gray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.End
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Huge Call & Share Location interactive buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Button(
                onClick = {
                    onSpeak(if (isHindi) "मैनेजर को कॉल मिलाया जा रहा है..." else "Calling supervisor now...")
                    try {
                        val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                            data = Uri.parse("tel:+919876543210")
                        }
                        context.startActivity(dialIntent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "📞 Calling: +91 98765 43210", Toast.LENGTH_LONG).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = AcceptGreen),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Phone, contentDescription = "Call", tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isHindi) "कॉल करें" else "Call",
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        color = Color.White
                    )
                }
            }

            Button(
                onClick = {
                    isRecordingLocation = true
                    onSpeak(if (isHindi) "लोकेशन शेयर की गई।" else "Location shared.")
                    try {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            val shareBody = if (isHindi) {
                                "मैं इस काम पर जा रहा हूँ:\nकाम: ${job.hindiTitle}\nपता: ${job.hindiLocation}\nगूगल मैप्स: https://www.google.com/maps/search/?api=1&query=${Uri.encode(job.location)}"
                            } else {
                                "I am on my way to this job:\nJob: ${job.title}\nAddress: ${job.location}\nGoogle Maps: https://www.google.com/maps/search/?api=1&query=${Uri.encode(job.location)}"
                            }
                            putExtra(Intent.EXTRA_TEXT, shareBody)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, if (isHindi) "शेयर करें (Share)" else "Share Location"))
                    } catch (e: Exception) {
                        Toast.makeText(context, if (isHindi) "📍 लाइव लोकेशन शेयर की गई!" else "📍 Live Location Shared!", Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isHindi) "भेजें" else "Share Map",
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        color = Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Dynamic Complete Shift Button - FINISH WORK!
        Button(
            onClick = {
                viewModel.completeJob(job.id)
                onSpeak(if (isHindi) "बधाई हो! आपका काम पूरा हुआ। वेतन आपकी कमाई में जोड़ दिया गया है।" else "Congratulations! Your work session is completed. Daily wages are added to your wallet balance.")
                onComplete()
            },
            colors = ButtonDefaults.buttonColors(containerColor = AcceptGreen),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Complete Shift",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isHindi) "काम पूरा हुआ (Complete Work)" else "Complete Work Shift",
                    fontWeight = FontWeight.Black,
                    fontSize = 19.sp,
                    color = Color.White
                )
            }
        }
    }
}

// --- TAB SCREEN 2: कमाई (EARNINGS & WALLET DASHBOARD) ---
@Composable
fun EarningsTabScreen(
    viewModel: MainViewModel,
    userProfile: UserProfile,
    isHindi: Boolean,
    onSpeak: (String) -> Unit
) {
    val context = LocalContext.current
    var showWithdrawConfirm by remember { mutableStateOf(false) }

    val currentEarnings by viewModel.walletEarnings.collectAsStateWithLifecycle()
    val totalDaysWorked by viewModel.walletDays.collectAsStateWithLifecycle()
    val appliedJobsList by viewModel.appliedJobs.collectAsStateWithLifecycle()

    val completedApps = appliedJobsList.filter { it.status == "Completed" }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Upper Profile ID Badge containing L3 Badge and Aadhaar verify
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(ContrastLight)
                                    .border(2.dp, OrangePrimary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Engineering, contentDescription = "Worker", tint = OrangePrimary, modifier = Modifier.size(34.dp))
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = userProfile.name.ifEmpty { "रमेश यादव" },
                                        fontWeight = FontWeight.Black,
                                        fontSize = 20.sp,
                                        color = DarkCharcoal
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    // Level L3 green badge
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(AcceptGreen)
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("L3", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Text(
                                    text = userProfile.preferredRole.ifEmpty { if (isHindi) "मज़दूर, मिस्त्री" else "Labourer, Carpenter" },
                                    fontSize = 15.sp,
                                    color = Color.Gray,
                                    fontWeight = FontWeight.Bold
                                )

                                // Stars Rating Indicator bar
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    repeat(4) {
                                        Icon(Icons.Default.Star, contentDescription = "Rating", tint = AmberHighlight, modifier = Modifier.size(16.dp))
                                    }
                                    Icon(Icons.Default.StarOutline, contentDescription = "Rating", tint = Color.Gray, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isHindi) "(58 रेटिंग)" else "(58 ratings)",
                                        fontSize = 13.sp,
                                        color = Color.Gray,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Authenticated Gvmt ID Badge (Image 5 right side)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = "Aadhaar verified",
                                tint = OrangeClassic,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                "आधार कार्ड",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = OrangePrimary
                            )
                        }
                    }
                }
            }
        }

        // Stats blocks details (मेरी कमाई and कुल काम)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(if (isHindi) "मेरी कमाई" else "My Earnings", fontSize = 15.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("₹ $currentEarnings", fontSize = 24.sp, fontWeight = FontWeight.Black, color = AcceptGreen)
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(if (isHindi) "कुल काम" else "Total Days", fontSize = 15.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(if (isHindi) "$totalDaysWorked दिन" else "$totalDaysWorked Days", fontSize = 24.sp, fontWeight = FontWeight.Black, color = OrangePrimary)
                    }
                }
            }
        }

        // Payout transfer button: "पैसे निकालें" (Wallet Cash drawer button in green)
        item {
            Button(
                onClick = {
                    showWithdrawConfirm = true
                    onSpeak(if (isHindi) "पैसे निकालने की प्रक्रिया शुरू करें।" else "Initiating wallet money transfer.")
                },
                colors = ButtonDefaults.buttonColors(containerColor = AcceptGreen),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Savings, contentDescription = "Withdraw", tint = Color.White, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isHindi) "पैसे निकालें ₹ $currentEarnings" else "Withdraw ₹ $currentEarnings",
                        fontWeight = FontWeight.Black,
                        fontSize = 19.sp,
                        color = Color.White
                    )
                }
            }
        }

        // Past Log of Completed History list: "काम का इतिहास"
        item {
            Text(
                text = if (isHindi) "काम का इतिहास" else "Work History Log",
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                color = DarkCharcoal,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        // Render dynamic completed jobs from Room database first
        items(completedApps) { app ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(ContrastLight),
                            contentAlignment = Alignment.Center
                        ) {
                            val icon = when {
                                app.title.contains("प्लंबर") || app.title.contains("Plumber") -> Icons.Default.WaterDrop
                                app.title.contains("बिजली") || app.title.contains("Wiring") -> Icons.Default.FlashOn
                                app.title.contains("मिस्त्री") || app.title.contains("Repair") -> Icons.Default.Construction
                                else -> Icons.Default.Engineering
                            }
                            Icon(icon, contentDescription = null, tint = OrangePrimary)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(app.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = DarkCharcoal)
                            Text(if (isHindi) "5 दिन (पूरा हुआ)" else "5 Days (Completed)", fontSize = 14.sp, color = Color.Gray)
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val wageRaw = app.salary.filter { it.isDigit() }.toIntOrNull() ?: 700
                        val totalPayStr = "₹ ${wageRaw * 5}"
                        Text(totalPayStr, fontWeight = FontWeight.Black, fontSize = 16.sp, color = DarkCharcoal, modifier = Modifier.padding(end = 12.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(AcceptGreen.copy(alpha = 0.12f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (isHindi) "साफ" else "Settled",
                                color = AcceptGreen,
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }

        // Render standard preloaded seed baseline completed items
        items(
            listOf(
                PastWorkRecord("प्लंबर (Plumbing Leak Repair)", "₹ 1950", "3 दिन", true),
                PastWorkRecord("मिस्त्री (Carpenter Cabinet Repair)", "₹ 3500", "5 दिन", true)
            )
        ) { record ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(ContrastLight),
                            contentAlignment = Alignment.Center
                        ) {
                            val iconChoice = if (record.title.contains("प्लंबर")) Icons.Default.WaterDrop else Icons.Default.Construction
                            Icon(iconChoice, contentDescription = null, tint = OrangePrimary)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            val titleDisplay = if (isHindi) {
                                if (record.title.contains("प्लंबर")) {
                                    "प्लंबर (नल और पाइप मरम्मत)"
                                } else {
                                    "मिस्त्री (किवाड़ और अलमारी मरम्मत)"
                                }
                            } else {
                                record.title
                            }
                            Text(titleDisplay, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = DarkCharcoal)
                            Text(record.days, fontSize = 14.sp, color = Color.Gray)
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(record.earned, fontWeight = FontWeight.Black, fontSize = 16.sp, color = DarkCharcoal, modifier = Modifier.padding(end = 12.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(AcceptGreen.copy(alpha = 0.12f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (isHindi) "साफ" else "Settled",
                                color = AcceptGreen,
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }

    var withdrawStep by remember { mutableStateOf(0) } // 0: UPI input, 1: Handshaking loading, 2: Receipt Successful
    var typedUpiId by remember { mutableStateOf(userProfile.upiId.ifEmpty { "9876543210@paytm" }) }
    var transferProgressPercent by remember { mutableStateOf(0f) }

    if (showWithdrawConfirm) {
        Dialog(onDismissRequest = { 
            showWithdrawConfirm = false 
            withdrawStep = 0
            transferProgressPercent = 0f
        }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.5.dp, AcceptGreen),
                modifier = Modifier.padding(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (withdrawStep == 0) {
                        Icon(Icons.Default.Savings, contentDescription = "Savings", tint = AcceptGreen, modifier = Modifier.size(54.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (isHindi) "बैंक ट्रांसफर सेटलमेंट" else "Direct UPI Bank Settlement",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            color = DarkCharcoal
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (isHindi) "कृपया अपनी UPI आईडी दर्ज करें (जैसे- phonepe/gpay/paytm):" else "Enter your virtual payment address (UPI ID):",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = typedUpiId,
                            onValueChange = { typedUpiId = it },
                            label = { Text(if (isHindi) "UPI आईडी / मोबाइल नंबर" else "UPI ID / Virtual Address*") },
                            placeholder = { Text("username@upi") },
                            leadingIcon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = AcceptGreen) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("upi_input_field"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AcceptGreen,
                                focusedContainerColor = Color(0xFFF1FAF4),
                                unfocusedContainerColor = Color(0xFFF1FAF4)
                            )
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showWithdrawConfirm = false },
                                border = BorderStroke(1.dp, Color.Gray),
                                modifier = Modifier.weight(1f).height(46.dp)
                            ) {
                                Text(if (isHindi) "रद्द करें" else "Cancel", fontWeight = FontWeight.Bold, color = Color.Gray)
                            }
                            Button(
                                onClick = {
                                    if (typedUpiId.trim().length < 5 || !typedUpiId.contains("@")) {
                                        Toast.makeText(context, if (isHindi) "कृपया सही UPI आईडी डालें!" else "Please enter standard UPI ID!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        withdrawStep = 1
                                        onSpeak(if (isHindi) "आपका बैंक अकाउंट सत्यापित किया जा रहा है। कृपया रुकें।" else "Verifying linked bank account.")
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AcceptGreen),
                                modifier = Modifier.weight(1.5f).height(46.dp)
                            ) {
                                Text(if (isHindi) "सत्यापित करें 🔒" else "Verify & Pay 🔒", fontWeight = FontWeight.Bold)
                            }
                        }
                    } else if (withdrawStep == 1) {
                        // Progress / Shake hands
                        LaunchedEffect(Unit) {
                            onSpeak(if (isHindi) "नेशनल पेमेंट कॉर्पोरेशन द्वारा खाता रमेश यादव सत्यापित। पैसे सीधे बैंक में भेज रहे हैं।" else "Account certified for Ramesh Yadav. Dispatching cash ledger.")
                            for (p in 1..100) {
                                kotlinx.coroutines.delay(20)
                                transferProgressPercent = p.toFloat()
                            }
                            viewModel.withdrawEarnings(currentEarnings, typedUpiId)
                            withdrawStep = 2
                            onSpeak(if (isHindi) "बधाई हो, राशि आपके बैंक खाते में सफलतापूर्वक क्रेडिट हो गई है।" else "Transfer successful. Money credited to your bank.")
                        }

                        CircularProgressIndicator(
                            progress = { transferProgressPercent / 100f },
                            color = AcceptGreen,
                            strokeWidth = 6.dp,
                            modifier = Modifier.size(72.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = if (isHindi) "बैंक ट्रांसफर जारी है..." else "NPCI Ledger Transfer...",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = AcceptGreen
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isHindi) "वेतनभोगी: रमेश यादव (${typedUpiId})" else "Holder: Ramesh Yadav (${typedUpiId})",
                            fontSize = 12.sp,
                            color = Color.DarkGray,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "₹ ${currentEarnings} @ STATE BANK OF INDIA",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = DarkCharcoal
                        )
                    } else {
                        // Success receipt
                        Icon(Icons.Default.CheckCircle, contentDescription = "Success", tint = AcceptGreen, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (isHindi) "ट्रांसफर सफल!" else "Withdrawn Successfully!",
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp,
                            color = AcceptGreen
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (isHindi) "पैसा आपके बैंक में क्रेडिट हो चुका है।" else "Credit reference dispatch successful.",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF1FAF4)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(if (isHindi) "राशि (Amount):" else "Amount:", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                    Text("₹ $currentEarnings.00", fontSize = 12.sp, fontWeight = FontWeight.Black, color = AcceptGreen)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(if (isHindi) "UPI आईडी (VPA):" else "UPI ID:", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                    Text(typedUpiId, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DarkCharcoal)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(if (isHindi) "यूटीआर नंबर (UTR ID):" else "NPCI UTR Code:", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                    Text("UTR${(1000000000..9999999999).random()}", fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, color = Color.DarkGray)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { 
                                showWithdrawConfirm = false 
                                withdrawStep = 0
                                transferProgressPercent = 0f
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AcceptGreen),
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(if (isHindi) "रसीद बंद करें (OK)" else "Close Receipt (OK)", fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }
    }
}

data class PastWorkRecord(val title: String, val earned: String, val days: String, val isSettled: Boolean)

// --- TAB SCREEN 3: अपनी प्रोफाइल बनाएं (EDIT/REGISTRATION SCREEN WITH PICKS) ---
@Composable
fun ProfileTabScreen(
    viewModel: MainViewModel,
    isHindi: Boolean,
    onSpeak: (String) -> Unit
) {
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var regName by remember { mutableStateOf("") }
    var selectedCatIndex by remember { mutableStateOf(1) } // Default Mason
    var experienceSlideValue by remember { mutableStateOf(2f) } // Snaps 0-1, 1-3, 3-5+
    var wageSlideValue by remember { mutableStateOf(2f) } // Snaps ₹300, ₹500, ₹700, ₹1000
    var showMicListening by remember { mutableStateOf(false) }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull() ?: ""
            if (spokenText.isNotEmpty()) {
                regName = spokenText
            }
        }
    }

    LaunchedEffect(userProfile) {
        regName = userProfile.name.ifEmpty { "रमेश यादव" }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Microphone inputs
        item {
            Text(
                text = if (isHindi) "नाम: " else "My Name: ",
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                color = DarkCharcoal
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = regName,
                onValueChange = { regName = it },
                trailingIcon = {
                    IconButton(onClick = {
                        onSpeak(if (isHindi) "कृपया अपना नाम बोलें।" else "Please speak your name.")
                        try {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (isHindi) "hi-IN" else "en-US")
                                putExtra(RecognizerIntent.EXTRA_PROMPT, if (isHindi) "अपना शुभ नाम बोलें..." else "Please speak your name...")
                            }
                            speechLauncher.launch(intent)
                        } catch (e: Exception) {
                            showMicListening = true
                        }
                    }) {
                        Icon(Icons.Default.Mic, contentDescription = "Voice Record", tint = OrangePrimary, modifier = Modifier.size(32.dp))
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile_name_input"),
                textStyle = LocalTextStyle.current.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = OrangePrimary,
                    unfocusedBorderColor = Color.Gray,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                )
            )
        }

        // Image 1 Category Selector: "काम का प्रकार*" (Labourer, Mason, Electrician, Plumber)
        item {
            Text(
                text = if (isHindi) "काम का प्रकार*" else "Type of Work*",
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                color = DarkCharcoal
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Layout row representing 4 workers side by side as in image 1
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val rolesList = listOf(
                    WorkerCategoryItem(0, "मजदूर", Icons.Default.Engineering),
                    WorkerCategoryItem(1, "मिस्त्री", Icons.Default.Construction),
                    WorkerCategoryItem(2, "बिजली मिस्त्री", Icons.Default.FlashOn),
                    WorkerCategoryItem(3, "प्लंबर", Icons.Default.WaterDrop)
                )

                rolesList.forEach { category ->
                    val isSelected = selectedCatIndex == category.idx
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                selectedCatIndex = category.idx
                                val readName = if (isHindi) "चुन लिया गया: ${category.hindiLabel}" else "Selected: ${category.hindiLabel}"
                                onSpeak(readName)
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) OrangePrimary.copy(alpha = 0.1f) else Color.White
                        ),
                        border = BorderStroke(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) OrangePrimary else GrayBorder
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = category.icon,
                                    contentDescription = category.hindiLabel,
                                    tint = if (isSelected) OrangePrimary else Color.Gray,
                                    modifier = Modifier.size(36.dp)
                                )
                                if (isSelected) {
                                    // Little green check logo matching plumber icon of image 1
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(AcceptGreen),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = category.hindiLabel,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) OrangeClassic else Color.Gray,
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // Image 1 Slider selection for experience level: "अनुभव"
        item {
            val expLabel = when (experienceSlideValue.toInt()) {
                0 -> "0-1 साल"
                1 -> "1-3 साल"
                else -> "3-5+ साल"
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = if (isHindi) "अनुभव: $expLabel" else "Experience: $expLabel",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = DarkCharcoal
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Slider(
                value = experienceSlideValue,
                onValueChange = {
                    experienceSlideValue = it
                    onSpeak(if (isHindi) "अनुभव: $expLabel" else "Experience: $expLabel")
                },
                valueRange = 0f..2f,
                steps = 1,
                colors = SliderDefaults.colors(
                    thumbColor = OrangePrimary,
                    activeTrackColor = OrangePrimary,
                    inactiveTrackColor = GrayBorder
                )
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("0-1 साल", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                Text("1-3 साल", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                Text("3-5+ साल", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
            }
        }

        // Image 1 Slider selection for wages: "दैनिक मज़दूरी" (₹300, ₹500, ₹700, ₹1000)
        item {
            val wageLabel = when (wageSlideValue.toInt()) {
                0 -> "₹300"
                1 -> "₹500"
                2 -> "₹700"
                else -> "₹1000"
            }

            Text(
                text = if (isHindi) "दैनिक मज़दूरी: $wageLabel / दिन" else "Daily Wage: $wageLabel / day",
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                color = DarkCharcoal
            )
            Spacer(modifier = Modifier.height(4.dp))
            Slider(
                value = wageSlideValue,
                onValueChange = {
                    wageSlideValue = it
                    onSpeak(if (isHindi) "वेतन $wageLabel रूपए प्रतिदिन" else "Wage $wageLabel rupees daily")
                },
                valueRange = 0f..3f,
                steps = 2,
                colors = SliderDefaults.colors(
                    thumbColor = OrangePrimary,
                    activeTrackColor = OrangePrimary,
                    inactiveTrackColor = GrayBorder
                )
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("₹300", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                Text("₹500", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                Text("₹700", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                Text("₹1000", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
            }
        }

        // IMAGE 1 BIG ORANGE BUTTON: "सेव करें" (Save profile)
        item {
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {
                    val preferCategory = when (selectedCatIndex) {
                        0 -> "मजदूर"
                        1 -> "मिस्त्री"
                        2 -> "बिजली मिस्त्री"
                        else -> "प्लंबर"
                    }
                    viewModel.updateProfile(
                        name = regName,
                        phone = userProfile.phone.ifEmpty { "9876543210" },
                        location = userProfile.location.ifEmpty { "मुम्बई (Mumbai)" },
                        role = preferCategory,
                        skills = "मिस्त्री, नल फिटिंग",
                        experience = experienceSlideValue.toInt() * 2,
                        education = "10th Pass",
                        bio = "मेहनती इंसान"
                    )
                    onSpeak(if (isHindi) "आपकी प्रोफाइल सुरक्षित कर ली गई है।" else "Profile bio successfully updated.")
                    Toast.makeText(context, "✅ Profile Saved Successfully!", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp) // Accessible height
            ) {
                Text(
                    text = if (isHindi) "सेव करें" else "Save Details",
                    fontWeight = FontWeight.Black,
                    fontSize = 22.sp,
                    color = Color.White
                )
            }
        }

        // Log out button to experience Login / Register onboarding again dynamically!
        item {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    viewModel.logout()
                    onSpeak(if (isHindi) "लॉगआउट सफल। कृपया दोबारा लॉगिन या पंजीकरण करें।" else "Logged out successfully. Please log in or register again.")
                },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = DeclineRed),
                border = BorderStroke(2.dp, DeclineRed),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ExitToApp, contentDescription = "Logout", tint = DeclineRed)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isHindi) "लॉगआउट करें (Log Out)" else "Log Out Profile",
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        color = DeclineRed
                    )
                }
            }
        }
    }

    if (showMicListening) {
        Dialog(onDismissRequest = { showMicListening = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.SettingsVoice, contentDescription = "Recording", tint = OrangePrimary, modifier = Modifier.size(54.dp))
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("सुन रहा हूँ... (Listening)", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = DarkCharcoal)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("कृपया अपना शुभ नाम साफ़ बोलें", fontSize = 14.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            regName = "रमेश यादव" // Auto simulated voice match success
                            showMicListening = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary)
                    ) {
                        Text("सफल (Finish)", color = Color.White)
                    }
                }
            }
        }
    }
}

data class WorkerCategoryItem(val idx: Int, val hindiLabel: String, val icon: ImageVector)

// --- JOB DETAIL SCREEN 3: काम का विवरण DIALOG WITH PLAY SOUND ASSISTANCE ---
@Composable
fun JobDetailDialog(
    job: MockJob,
    isHindi: Boolean,
    onCancel: () -> Unit,
    onAccept: () -> Unit,
    onSpeak: (String) -> Unit
) {
    Dialog(onDismissRequest = onCancel) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, BorderColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header of detail with Speak option icon in mockup 3
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Dismiss", tint = OrangeClassic)
                    }

                    Text(
                        text = if (isHindi) "काम का विवरण" else "Job Details",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        color = DarkCharcoal
                    )

                    // Speaker Assist clicker button (Visual in image 3)
                    IconButton(onClick = {
                        val speakStr = if (isHindi) {
                            "काम का नाम: ${job.hindiTitle}। वेतन: ${job.hindiSalary}। ठेकेदार: ${job.hindiCompany}। दूरी: ${job.hindiDistance}।"
                        } else {
                            "Job Title: ${job.title}, Salary: ${job.salary}, Company: ${job.company}, Distance: ${job.distance}."
                        }
                        onSpeak(speakStr)
                    }) {
                        Icon(Icons.Default.VolumeUp, contentDescription = "Listen details", tint = OrangePrimary, modifier = Modifier.size(34.dp))
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Big cartoon character icon
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(ContrastLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Engineering, contentDescription = null, tint = OrangePrimary, modifier = Modifier.size(48.dp))
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Detail container frame
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SandyBg),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = if (isHindi) job.hindiTitle else job.title,
                            fontWeight = FontWeight.Black,
                            fontSize = 21.sp,
                            color = DarkCharcoal,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = if (isHindi) job.hindiSalary else job.salary,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 19.sp,
                            color = AcceptGreen,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        )

                        HorizontalDivider(color = BorderColor.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 10.dp))

                        val context = LocalContext.current
                        
                        // Distance & Tenure Highlight
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (isHindi) "📍 दूरी: ${job.hindiDistance}" else "📍 Distance: ${job.distance}",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = DarkCharcoal
                            )
                            Text(
                                text = if (isHindi) "📅 अवधि: ${job.tenureDays} दिन" else "📅 Tenure: ${job.tenureDays} Days",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = DarkCharcoal
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        Text(
                            text = if (isHindi) "👤 ठेकेदार: ${job.hindiCompany}" else "👤 Employer: ${job.company}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkCharcoal
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))

                        // Real Clickable workplace location card mimicking Ola/Uber address picker
                        Card(
                            onClick = {
                                launchGoogleMapsNavigation(context, job.location)
                            },
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.5.dp, OrangePrimary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(OrangePrimary.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LocationOn,
                                        contentDescription = "Navigate",
                                        tint = OrangePrimary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = if (isHindi) "कार्यस्थल का पता (Workplace)" else "Workplace Address",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Gray
                                    )
                                    Text(
                                        text = if (isHindi) job.hindiLocation else job.location,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Black,
                                        color = OrangeClassic
                                    )
                                    Text(
                                        text = if (isHindi) "🗺️ क्लिक करें और नेविगेट करें" else "🗺️ Click to navigate",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        color = AcceptGreen
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Two HUGE Accept & Decline action buttons at bottom of image 3
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Button(
                        onClick = onCancel,
                        colors = ButtonDefaults.buttonColors(containerColor = DeclineRed),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                    ) {
                        Text(
                            text = if (isHindi) "मना करें" else "Decline",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    }

                    Button(
                        onClick = onAccept,
                        colors = ButtonDefaults.buttonColors(containerColor = AcceptGreen),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                    ) {
                        Text(
                            text = if (isHindi) "स्वीकार करें" else "Accept",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

// --- OVERLAY CAREER MITRA GEMINI BOT SHEET ---
@Composable
fun AiCounselorOverlay(
    viewModel: MainViewModel,
    isHindi: Boolean,
    onClose: () -> Unit
) {
    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val isAiThinking by viewModel.isAiThinking.collectAsStateWithLifecycle()
    var inputChatMsg by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    Dialog(onDismissRequest = onClose) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(2.dp, OrangePrimary)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(OrangePrimary)
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SupportAgent, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                "AI Career Mitra (करियर मित्र)",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 18.sp
                            )
                        }
                        IconButton(onClick = onClose) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }
                }

                // Chat logs scroll
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(chatMessages) { chat ->
                        val isUser = chat.sender == "user"
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
                        ) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isUser) OrangePrimary.copy(alpha = 0.15f) else ContrastLight
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = chat.message,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = DarkCharcoal,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }

                    if (isAiThinking) {
                        item {
                            Text(
                                "सोच रहा हूँ... (Mitra is thinking)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = OrangeClassic,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }

                // Inputs panel
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ContrastLight)
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputChatMsg,
                        onValueChange = { inputChatMsg = it },
                        placeholder = { Text(if (isHindi) "मित्र से सवाल पूछें..." else "Ask any query...") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            if (inputChatMsg.trim().isNotEmpty()) {
                                viewModel.sendChatMessage(inputChatMsg)
                                inputChatMsg = ""
                                focusManager.clearFocus()
                            }
                        })
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(
                        onClick = {
                            if (inputChatMsg.trim().isNotEmpty()) {
                                viewModel.sendChatMessage(inputChatMsg)
                                inputChatMsg = ""
                                focusManager.clearFocus()
                            }
                        },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = OrangePrimary)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun SleekSimulatedMapBackground() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        // 1. Light earth slate base (Uber-style sophisticated canvas background)
        drawRect(color = Color(0xFFF1EDE6))

        // 2. High-contrast River (Metropolitan water geographic asset)
        val waterColor = Color(0xFFC9DBE8) // Muted blue-grey water body
        val riverPath = androidx.compose.ui.graphics.Path().apply {
            moveTo(width * 0.85f, 0f)
            cubicTo(
                width * 0.75f, height * 0.22f,
                width * 0.95f, height * 0.48f,
                width * 0.62f, height * 0.74f
            )
            cubicTo(
                width * 0.42f, height * 0.95f,
                width * 0.35f, height * 0.90f,
                width * 0.10f, height
            )
            lineTo(0f, height)
            lineTo(0f, 0f)
            close()
        }
        drawPath(path = riverPath, color = waterColor.copy(alpha = 0.5f))

        // 3. Simulated building blocks (urban layouts with rounded geometries)
        val blockFill = Color(0xFFEADBCE).copy(alpha = 0.45f)
        val blockStroke = Color(0xFFDCCFBE)

        // Block A
        drawRoundRect(
            color = blockFill,
            topLeft = Offset(width * 0.04f, height * 0.05f),
            size = androidx.compose.ui.geometry.Size(width * 0.30f, height * 0.08f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f, 12f)
        )
        drawRoundRect(
            color = blockStroke,
            topLeft = Offset(width * 0.04f, height * 0.05f),
            size = androidx.compose.ui.geometry.Size(width * 0.30f, height * 0.08f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f, 12f),
            style = Stroke(width = 2.5f)
        )

        // Block B
        drawRoundRect(
            color = blockFill,
            topLeft = Offset(width * 0.06f, height * 0.48f),
            size = androidx.compose.ui.geometry.Size(width * 0.24f, height * 0.11f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f, 12f)
        )
        drawRoundRect(
            color = blockStroke,
            topLeft = Offset(width * 0.06f, height * 0.48f),
            size = androidx.compose.ui.geometry.Size(width * 0.24f, height * 0.11f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f, 12f),
            style = Stroke(width = 2.5f)
        )

        // Block C
        drawRoundRect(
            color = blockFill,
            topLeft = Offset(width * 0.58f, height * 0.10f),
            size = androidx.compose.ui.geometry.Size(width * 0.28f, height * 0.07f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f, 12f)
        )
        drawRoundRect(
            color = blockStroke,
            topLeft = Offset(width * 0.58f, height * 0.10f),
            size = androidx.compose.ui.geometry.Size(width * 0.28f, height * 0.07f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f, 12f),
            style = Stroke(width = 2.5f)
        )

        // Block D
        drawRoundRect(
            color = blockFill,
            topLeft = Offset(width * 0.45f, height * 0.81f),
            size = androidx.compose.ui.geometry.Size(width * 0.45f, height * 0.10f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f, 12f)
        )
        drawRoundRect(
            color = blockStroke,
            topLeft = Offset(width * 0.45f, height * 0.81f),
            size = androidx.compose.ui.geometry.Size(width * 0.45f, height * 0.10f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f, 12f),
            style = Stroke(width = 2.5f)
        )

        // 4. White arterial streets structure
        val roadBg = Color(0xFFFFFFFF)
        val roadDiv = Color(0xFFD4CDC0)

        val wPrimary = 26.dp.toPx()
        val wSecondary = 18.dp.toPx()

        // Horizontal highway tracks
        drawLine(color = roadBg, start = Offset(0f, height * 0.18f), end = Offset(width, height * 0.20f), strokeWidth = wPrimary)
        drawLine(color = roadDiv, start = Offset(0f, height * 0.18f), end = Offset(width, height * 0.20f), strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f))

        drawLine(color = roadBg, start = Offset(0f, height * 0.43f), end = Offset(width, height * 0.41f), strokeWidth = wPrimary)
        drawLine(color = roadDiv, start = Offset(0f, height * 0.43f), end = Offset(width, height * 0.41f), strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f))

        drawLine(color = roadBg, start = Offset(0f, height * 0.74f), end = Offset(width, height * 0.76f), strokeWidth = wSecondary)
        drawLine(color = roadDiv, start = Offset(0f, height * 0.74f), end = Offset(width, height * 0.76f), strokeWidth = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f))

        // Vertical connect avenues
        drawLine(color = roadBg, start = Offset(width * 0.22f, 0f), end = Offset(width * 0.25f, height), strokeWidth = wPrimary)
        drawLine(color = roadDiv, start = Offset(width * 0.22f, 0f), end = Offset(width * 0.25f, height), strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f))

        drawLine(color = roadBg, start = Offset(width * 0.50f, 0f), end = Offset(width * 0.50f, height), strokeWidth = wSecondary)
        drawLine(color = roadDiv, start = Offset(width * 0.50f, 0f), end = Offset(width * 0.50f, height), strokeWidth = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))

        drawLine(color = roadBg, start = Offset(width * 0.78f, 0f), end = Offset(width * 0.75f, height), strokeWidth = wSecondary)
        drawLine(color = roadDiv, start = Offset(width * 0.78f, 0f), end = Offset(width * 0.75f, height), strokeWidth = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f))

        // 5. Active Contractor-To-Worker high-visibility matching route (Glowing connecting trace)
        val matchPath = androidx.compose.ui.graphics.Path().apply {
            moveTo(width * 0.24f, height * 0.42f)
            lineTo(width * 0.50f, height * 0.41f)
            lineTo(width * 0.50f, height * 0.75f)
            lineTo(width * 0.76f, height * 0.75f)
        }
        drawPath(
            path = matchPath,
            color = OrangeClassic,
            style = Stroke(width = 5.5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        )
        drawPath(
            path = matchPath,
            color = Color.White,
            style = Stroke(width = 1.8.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))
        )

        // 6. Dual pulsating beacons (Mason pinpoint vs Recruiter home marker)
        val markerWorker = Offset(width * 0.24f, height * 0.42f)
        drawCircle(color = OrangePrimary.copy(alpha = 0.12f), center = markerWorker, radius = 55.dp.toPx())
        drawCircle(color = OrangePrimary.copy(alpha = 0.22f), center = markerWorker, radius = 28.dp.toPx())
        drawCircle(color = Color.White, center = markerWorker, radius = 8.dp.toPx())
        drawCircle(color = OrangePrimary, center = markerWorker, radius = 5.dp.toPx())

        val markerEmployer = Offset(width * 0.76f, height * 0.75f)
        drawCircle(color = AcceptGreen.copy(alpha = 0.12f), center = markerEmployer, radius = 58.dp.toPx())
        drawCircle(color = AcceptGreen.copy(alpha = 0.24f), center = markerEmployer, radius = 30.dp.toPx())
        drawCircle(color = Color.White, center = markerEmployer, radius = 8.dp.toPx())
        drawCircle(color = AcceptGreen, center = markerEmployer, radius = 5.dp.toPx())

        // 7. Render cute tiny high-contrast live cabs (Ola/Uber vehicles moving on grid)
        val vSize = 13.dp.toPx()
        val cabColor = Color(0xFF1E1C21) // High contrast premium black cabs

        // Vehicle A
        drawRoundRect(
            color = cabColor,
            topLeft = Offset(width * 0.24f - vSize/2, height * 0.20f - vSize/3),
            size = androidx.compose.ui.geometry.Size(vSize * 1.3f, vSize * 0.7f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
        )
        drawCircle(color = Color.White, center = Offset(width * 0.24f - vSize/4, height * 0.20f), radius = 1.8f)
        drawCircle(color = Color.White, center = Offset(width * 0.24f + vSize/4, height * 0.20f), radius = 1.8f)

        // Vehicle B
        drawRoundRect(
            color = cabColor,
            topLeft = Offset(width * 0.65f - vSize/2, height * 0.41f - vSize/3),
            size = androidx.compose.ui.geometry.Size(vSize * 1.3f, vSize * 0.7f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
        )
        drawCircle(color = Color.White, center = Offset(width * 0.65f - vSize/4, height * 0.41f), radius = 1.8f)
        drawCircle(color = Color.White, center = Offset(width * 0.65f + vSize/4, height * 0.41f), radius = 1.8f)
    }
}

// --- NEW IMMERSIVE BILINGUAL ONBOARDING & SIGNUP SCREEN ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginSignupScreen(
    viewModel: MainViewModel,
    isHindi: Boolean,
    onLanguageToggle: () -> Unit,
    onSpeak: (String) -> Unit
) {
    val context = LocalContext.current
    var isLoginSelected by remember { mutableStateOf(false) } // Default to Sign Up
    var selectedRegisterRole by remember { mutableStateOf("Worker") } // "Worker", "Contractor", or "Employer"

    // Onboarding Core States
    var loginPhone by remember { mutableStateOf("") }
    var loginOtp by remember { mutableStateOf("") }
    var showOtpField by remember { mutableStateOf(false) }
    
    var regName by remember { mutableStateOf("") }
    var regPhone by remember { mutableStateOf("") }
    var regAadhaar by remember { mutableStateOf("") }
    
    var selectedWorkCategoryIndex by remember { mutableStateOf(1) } // Default to Mason / मिस्त्री
    var showMicListening by remember { mutableStateOf(false) }
    var activeVoiceTarget by remember { mutableStateOf("name") }

    // Worker/Contractor Specific
    var regWorkerExpYears by remember { mutableStateOf("Experienced (3-5 Years)") }
    var regWorkerWageSlider by remember { mutableStateOf(2f) }
    var regWorkerRadius by remember { mutableStateOf("Within 5 km") }
    var regWorkerSkillsBio by remember { mutableStateOf("") }

    // Employer Specific
    var regBusinessName by remember { mutableStateOf("") }
    var regLocation by remember { mutableStateOf("") }
    var offeredWageSlideValue by remember { mutableStateOf(1f) }
    var regEmployerProjectScale by remember { mutableStateOf("Small Repair") }
    var isFetchingLocationByGps by remember { mutableStateOf(false) }
    var showLocationSuggestions by remember { mutableStateOf(false) }
    var isAddressVerifiedByGps by remember { mutableStateOf(false) }

    // Simulated verification values
    var isAadhaarVerified by remember { mutableStateOf(false) }
    var isPhotoCaptured by remember { mutableStateOf(false) }
    var showFingerprintScannerDialog by remember { mutableStateOf(false) }
    var biometricScanProgress by remember { mutableStateOf(0f) }
    var currentScannerLogIndex by remember { mutableStateOf(0) }

    // Camera features simulation
    var showCameraScannerDialog by remember { mutableStateOf(false) }
    var cameraFlashEnabled by remember { mutableStateOf(false) }
    var cameraFocusMode by remember { mutableStateOf("soft_portrait") }
    var isTriggeringCameraFlash by remember { mutableStateOf(false) }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull() ?: ""
            if (spokenText.isNotEmpty()) {
                if (activeVoiceTarget == "name") {
                    regName = spokenText
                } else if (activeVoiceTarget == "business") {
                    regBusinessName = spokenText
                } else if (activeVoiceTarget == "skills") {
                    regWorkerSkillsBio = spokenText
                }
            }
        }
    }

    // Dynamic color tokens mapping for three distinctive user roles
    val themeColor = when(selectedRegisterRole) {
        "Worker" -> OrangePrimary
        "Contractor" -> Color(0xFF673AB7) // Deep Purple
        else -> Color(0xFF0D47A1) // Classic Blue
    }
    val themeAccent = when(selectedRegisterRole) {
        "Worker" -> OrangeClassic
        "Contractor" -> Color(0xFF512DA8)
        else -> Color(0xFF1565C0)
    }
    val themeContainer = when(selectedRegisterRole) {
        "Worker" -> SandyBg
        "Contractor" -> Color(0xFFF3E5F5)
        else -> Color(0xFFF1F5FA)
    }
    val themeTextLabel = when(selectedRegisterRole) {
        "Worker" -> OrangeClassic
        "Contractor" -> Color(0xFF512DA8)
        else -> Color(0xFF0D47A1)
    }

    val locationSuggestionsList = listOf(
        if (isHindi) "📍 मेट्रो पिल्लर ७२, अंधेरी पूर्व, मुंबई" else "📍 Metro Pillar 72, Andheri East, Mumbai",
        if (isHindi) "📍 बजट प्रोजेक्ट साइट, बांद्रा कुर्ला कम्प्लेक्स, मुंबई" else "📍 Builders HQ, Bandra Kurla Complex, Mumbai",
        if (isHindi) "📍 रेलवे स्टेशन मॉल कंस्ट्रक्शन गेट ४, दादर, मुंबई" else "📍 Station Mall Gate 4, Dadar, Mumbai",
        if (isHindi) "📍 एसआरए प्रोजेक्ट टावर बी, गोरेगांव वेस्ट, मुंबई" else "📍 SRA Project Tower B, Goregaon West, Mumbai"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SandyBg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Floating Top Header branding
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    border = BorderStroke(1.5.dp, themeColor),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "ROZGAAR SETU",
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                color = themeAccent,
                                letterSpacing = 2.sp
                            )
                            
                            // High Visibility Language toggle
                            Button(
                                onClick = onLanguageToggle,
                                colors = ButtonDefaults.buttonColors(containerColor = themeColor.copy(alpha = 0.1f)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                border = BorderStroke(1.2.dp, themeColor),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text(
                                    text = if (isHindi) "English 🌐" else "हिन्दी 🌐",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 11.sp,
                                    color = themeAccent
                                )
                            }
                        }
                        
                        Divider(modifier = Modifier.padding(vertical = 8.dp), color = GrayBorder)
                        
                        Text(
                            text = if (isHindi) "🤝 रोजगार सेतु - कामगार, मिस्त्री और ठेकेदार रडार" else "🤝 Rozgaar Setu - Workers, Masons & Contractors",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            color = DarkCharcoal,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // High Contrast Login / Register Pill Toggle
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White)
                        .border(1.2.dp, GrayBorder, RoundedCornerShape(14.dp))
                        .padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (!isLoginSelected) themeColor else Color.Transparent)
                            .clickable { isLoginSelected = false }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isHindi) "नया पंजीयन (Register)" else "Register Account",
                            color = if (!isLoginSelected) Color.White else DarkCharcoal,
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isLoginSelected) themeColor else Color.Transparent)
                            .clickable { isLoginSelected = true }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isHindi) "लॉगिन (Login)" else "Login / Enter",
                            color = if (isLoginSelected) Color.White else DarkCharcoal,
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // Main Core Form Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    border = BorderStroke(1.5.dp, themeColor)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (isLoginSelected) {
                            // --- REAL WORKING LOGIN FORM ---
                            Text(
                                text = if (isHindi) "मोबाईल नंबर से प्रवेश करें" else "Enter Mobile Number to Log In",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = DarkCharcoal
                            )
                            
                            OutlinedTextField(
                                value = loginPhone,
                                onValueChange = { if (it.length <= 10) loginPhone = it },
                                label = { Text(if (isHindi) "मोबाइल नंबर" else "Mobile Number") },
                                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = themeColor) },
                                modifier = Modifier.fillMaxWidth().testTag("login_phone_input"),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = themeColor,
                                    unfocusedBorderColor = Color.Gray,
                                    focusedContainerColor = themeContainer,
                                    unfocusedContainerColor = themeContainer
                                )
                            )

                            if (showOtpField) {
                                OutlinedTextField(
                                    value = loginOtp,
                                    onValueChange = { if (it.length <= 4) loginOtp = it },
                                    label = { Text(if (isHindi) "4-अंकों का ओटीपी कोड दर्ज करें" else "Enter 4-Digit OTP") },
                                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = themeColor) },
                                    modifier = Modifier.fillMaxWidth().testTag("login_otp_input"),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = themeColor,
                                        focusedContainerColor = themeContainer,
                                        unfocusedContainerColor = themeContainer
                                    )
                                )
                            }

                            Button(
                                onClick = {
                                    if (loginPhone.length < 10) {
                                        Toast.makeText(context, if (isHindi) "कृपया सही 10-अंकों का नंबर दर्ज करें!" else "Please enter standard 10-digit phone!", Toast.LENGTH_SHORT).show()
                                    } else if (!showOtpField) {
                                        showOtpField = true
                                        onSpeak(if (isHindi) "ओटीपी भेज दिया गया है" else "OTP Code sent successfully")
                                        Toast.makeText(context, "💬 OTP: 1234", Toast.LENGTH_LONG).show()
                                    } else {
                                        if (loginOtp == "1234") {
                                            // Real Working persistence load or create
                                            viewModel.updateProfile(
                                                name = if (isHindi) "रमेश यादव" else "Ramesh Yadav",
                                                phone = loginPhone,
                                                location = if (isHindi) "अंधेरी ईस्ट, मुम्बई" else "Andheri East, Mumbai",
                                                role = "मिस्त्री",
                                                skills = "ईंट जुड़ाई, प्लास्टर, टाइल फिटिंग",
                                                experience = 3,
                                                education = "10th Pass",
                                                bio = "अपेक्षित दिहाड़ी: ₹700",
                                                userRoleType = "Worker"
                                            )
                                            Toast.makeText(context, "✅ Welcome!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "❌ Invalid OTP (Use 1234)", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = themeColor),
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = if (!showOtpField) (if (isHindi) "ओटीपी कोड प्राप्त करें" else "Get Verification OTP") else (if (isHindi) "सत्यापित करें और प्रवेश करें" else "Verify & Log In"),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 15.sp
                                )
                            }

                        } else {
                            // --- REGISTRATION FORM with Subtheme Customization ---
                            Text(
                                text = if (isHindi) "१. अपना मुख्य रोल चुनिए (Choose Role)" else "1. Choose Your Persona Role Theme",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = themeAccent
                            )

                            // Interactive Subtheme selector with 3 tabs: Worker, Contractor, Employer
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // 1. Worker Tool
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .border(
                                            width = if (selectedRegisterRole == "Worker") 2.5.dp else 1.dp,
                                            color = if (selectedRegisterRole == "Worker") OrangePrimary else Color.LightGray,
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .background(if (selectedRegisterRole == "Worker") OrangePrimary.copy(alpha = 0.08f) else Color.White)
                                        .clickable { 
                                            selectedRegisterRole = "Worker"
                                            onSpeak(if (isHindi) "कामगार रोल चुना गया। दैनिक मजदूरी ढूंढने के लिए।" else "Worker theme activated.")
                                        }
                                        .padding(8.dp)
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                        Icon(Icons.Default.Engineering, contentDescription = null, tint = OrangePrimary, modifier = Modifier.size(22.dp))
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = if (isHindi) "कामगार" else "Worker",
                                            fontWeight = FontWeight.ExtraBold,
                                            color = OrangePrimary,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                // 2. Contractor Tool
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .border(
                                            width = if (selectedRegisterRole == "Contractor") 2.5.dp else 1.dp,
                                            color = if (selectedRegisterRole == "Contractor") Color(0xFF673AB7) else Color.LightGray,
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .background(if (selectedRegisterRole == "Contractor") Color(0xFF673AB7).copy(alpha = 0.08f) else Color.White)
                                        .clickable { 
                                            selectedRegisterRole = "Contractor"
                                            onSpeak(if (isHindi) "ठेकेदार रोल चुना गया। मजदूरों को काम पर रखने और भारी टेंडर के लिए।" else "Contractor portfolio activated.")
                                        }
                                        .padding(8.dp)
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                        Icon(Icons.Default.BuildCircle, contentDescription = null, tint = Color(0xFF673AB7), modifier = Modifier.size(22.dp))
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = if (isHindi) "ठेकेदार" else "Contractor",
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFF673AB7),
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                // 3. Employer Tool
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .border(
                                            width = if (selectedRegisterRole == "Employer") 2.5.dp else 1.dp,
                                            color = if (selectedRegisterRole == "Employer") Color(0xFF0D47A1) else Color.LightGray,
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .background(if (selectedRegisterRole == "Employer") Color(0xFF0D47A1).copy(alpha = 0.08f) else Color.White)
                                        .clickable { 
                                            selectedRegisterRole = "Employer"
                                            onSpeak(if (isHindi) "मालिक रोल चुना गया। तुरंत काम पोस्ट करने के लिए।" else "Employer theme activated.")
                                        }
                                        .padding(8.dp)
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                        Icon(Icons.Default.Business, contentDescription = null, tint = Color(0xFF0D47A1), modifier = Modifier.size(22.dp))
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = if (isHindi) "मालिक" else "Employer",
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFF0D47A1),
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }

                            Divider(color = GrayBorder, modifier = Modifier.padding(vertical = 4.dp))

                            // Smart Selfie Camera Viewfinder System Preview Drawer
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(themeColor.copy(alpha = 0.04f), RoundedCornerShape(12.dp))
                                    .border(1.2.dp, themeColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                    .clickable { showCameraScannerDialog = true }
                                    .padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Portrait,
                                        contentDescription = null,
                                        tint = themeColor,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isHindi) "📸 सेल्फी प्रोफाइल फ़ोटो कैप्चर करें*" else "📸 Secure Face Photo Verification*",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = themeColor
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))

                                if (isPhotoCaptured) {
                                    Box(
                                        modifier = Modifier
                                            .size(80.dp)
                                            .clip(CircleShape)
                                            .border(2.dp, AcceptGreen, CircleShape)
                                            .background(Color.LightGray),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Face, contentDescription = "Face", tint = AcceptGreen, modifier = Modifier.size(46.dp))
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .size(20.dp)
                                                .clip(CircleShape)
                                                .background(AcceptGreen)
                                        ) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (isHindi) "फ़ोटो सत्यापित और लॉक" else "Face biometric photo saved & locked!",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AcceptGreen
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(60.dp)
                                            .clip(CircleShape)
                                            .background(Color.White)
                                            .border(1.2.dp, Color.Gray, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Person, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(36.dp))
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (isHindi) "कैमरा व्यूफाइंडर शुरू करने के लिए छुएं" else "Tap to open smart camera viewfinder",
                                        fontSize = 11.sp,
                                        color = Color.DarkGray
                                    )
                                }
                            }

                            // Shared Name Input
                            OutlinedTextField(
                                value = regName,
                                onValueChange = { regName = it },
                                label = { Text(if (isHindi) "शुभ नाम (Full Name)*" else "Full Name*") },
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = themeColor) },
                                trailingIcon = {
                                    IconButton(onClick = {
                                        activeVoiceTarget = "name"
                                        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                                        }
                                        try { speechLauncher.launch(i) } catch(e: Exception) { showMicListening = true }
                                    }) {
                                        Icon(Icons.Default.Mic, contentDescription = "Voice Input", tint = themeColor)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().testTag("worker_name_input"),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = themeColor,
                                    focusedContainerColor = themeContainer,
                                    unfocusedContainerColor = themeContainer
                                )
                            )

                            // Shared Phone Input
                            Column {
                                OutlinedTextField(
                                    value = regPhone,
                                    onValueChange = { if (it.length <= 10) regPhone = it },
                                    label = { Text(if (isHindi) "१० अंकों का मोबाइल नंबर (Mobile Number)*" else "10-Digit Mobile Number*") },
                                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = themeColor) },
                                    modifier = Modifier.fillMaxWidth().testTag("worker_phone_input"),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = themeColor,
                                        focusedContainerColor = themeContainer,
                                        unfocusedContainerColor = themeContainer
                                    )
                                )

                                if (regPhone.length == 10) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(AcceptGreen.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
                                            .padding(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AcceptGreen, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (isHindi) "सही मोबाईल फॉर्मेट • ओटीपी के लिए तैयार" else "VALID TELECOM FORMAT • Handshake Ready",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AcceptGreen
                                        )
                                    }
                                }
                            }

                            // Government 12-Digit Aadhaar Scanner System Layout
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.2.dp, if (isAadhaarVerified) AcceptGreen else GrayBorder, RoundedCornerShape(12.dp))
                                    .background(if (isAadhaarVerified) Color(0xFFF1FAF4) else Color.White)
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isHindi) "🛡️ सरकारी आधार संख्या सत्यापन (UIDAI)" else "🛡️ UIDAI National Identity Verification*",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = if (isAadhaarVerified) AcceptGreen else themeTextLabel
                                    )
                                    
                                    if (isAadhaarVerified) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(AcceptGreen.copy(alpha = 0.15f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = if (isHindi) "सत्यापित" else "VERIFIED",
                                                color = AcceptGreen,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 9.sp
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = regAadhaar,
                                        onValueChange = { if (it.length <= 12) { regAadhaar = it } },
                                        label = { Text(if (isHindi) "१२ अंकों की आधार संख्या" else "12-Digit Aadhaar") },
                                        placeholder = { Text("1234 5678 9012") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1.5f),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = if (isAadhaarVerified) AcceptGreen else themeColor,
                                            focusedContainerColor = Color.White,
                                            unfocusedContainerColor = Color.White
                                        )
                                    )

                                    Button(
                                        onClick = {
                                            if (regAadhaar.length < 12) {
                                                Toast.makeText(context, if (isHindi) "आधार सत्यापन के लिए १२ अंक आवश्यक हैं!" else "Aadhaar must be exactly 12 digits!", Toast.LENGTH_SHORT).show()
                                            } else {
                                                showFingerprintScannerDialog = true
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = if (isAadhaarVerified) AcceptGreen else themeColor),
                                        modifier = Modifier.weight(1f).height(50.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = if (isAadhaarVerified) "पूर्ण (OK)" else (if (isHindi) "स्कैन करें" else "Scan Biometric"),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }

                                if (isAadhaarVerified) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = AcceptGreen, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (isHindi) "UIDAI रजिस्ट्री: रमेश यादव पिता शंकर लाल यादव (सत्यापित)" else "CENTRAL REGISTERED: RAMESH YADAV S/O SHANKAR DEVI (CONFIRMED)",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AcceptGreen
                                        )
                                    }
                                }
                            }

                            if (selectedRegisterRole == "Worker" || selectedRegisterRole == "Contractor") {
                                // ================= WORKER/CONTRACTOR REGISTRATION =================
                                Text(
                                    text = if (selectedRegisterRole == "Worker") {
                                        if (isHindi) "📝 नया मजदूर पंजीयन पत्र" else "📝 Worker Registration Questionnaire"
                                    } else {
                                        if (isHindi) "📝 नया सुपरवाइज़र / ठेकेदार पंजीयन" else "📝 Contractor Profile Details"
                                    },
                                    fontWeight = FontWeight.Black,
                                    fontSize = 14.sp,
                                    color = themeAccent
                                )

                                // Work Specialty Grid
                                Column {
                                    Text(
                                        text = if (isHindi) "आप किस काम के मिस्त्री / मजदूर हैं?*" else "Choose Work Category*",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = OrangeClassic
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        val cats = listOf(
                                            if (isHindi) "मजदूर" else "Helper",
                                            if (isHindi) "मिस्त्री" else "Mason",
                                            if (isHindi) "बिजली" else "Elec",
                                            if (isHindi) "प्लंबर" else "Plumber"
                                        )
                                        cats.forEachIndexed { idx, label ->
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .border(
                                                        width = if (selectedWorkCategoryIndex == idx) 2.dp else 1.dp,
                                                        color = if (selectedWorkCategoryIndex == idx) OrangePrimary else Color.LightGray,
                                                        shape = RoundedCornerShape(8.dp)
                                                    )
                                                    .background(if (selectedWorkCategoryIndex == idx) OrangePrimary.copy(alpha = 0.1f) else Color.White)
                                                    .clickable { selectedWorkCategoryIndex = idx }
                                                    .padding(vertical = 8.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(label, fontWeight = FontWeight.Black, fontSize = 11.sp, color = DarkCharcoal)
                                            }
                                        }
                                    }
                                }

                                // Experience Levels
                                Column {
                                    Text(
                                        text = if (isHindi) "काम का कुल अनुभव (Experience)*" else "Total Work Experience*",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = OrangeClassic
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        val exps = listOf(
                                            if (isHindi) "1-2 वर्ष" else "1-2 Yrs",
                                            if (isHindi) "3-5 वर्ष" else "3-5 Yrs",
                                            if (isHindi) "5+ वर्ष" else "5+ Yrs"
                                        )
                                        exps.forEach { exp ->
                                            val isSel = regWorkerExpYears.contains(exp.take(3))
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .border(
                                                        width = if (isSel) 2.dp else 1.dp,
                                                        color = if (isSel) OrangePrimary else Color.LightGray,
                                                        shape = RoundedCornerShape(8.dp)
                                                    )
                                                    .background(if (isSel) OrangePrimary.copy(alpha = 0.1f) else Color.White)
                                                    .clickable { regWorkerExpYears = exp }
                                                    .padding(vertical = 8.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(exp, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = DarkCharcoal)
                                            }
                                        }
                                    }
                                }

                                // Wage Slider
                                Column {
                                    val mappedWage = when(regWorkerWageSlider.toInt()) {
                                        0 -> 350
                                        1 -> 500
                                        2 -> 700
                                        3 -> 950
                                        else -> 1200
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(if (isHindi) "अपेक्षित दैनिक दिहाड़ी:" else "Your Daily Wage:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = OrangeClassic)
                                        Text("₹$mappedWage / दिन", fontWeight = FontWeight.Black, fontSize = 13.sp, color = OrangePrimary)
                                    }
                                    Slider(
                                        value = regWorkerWageSlider,
                                        onValueChange = { regWorkerWageSlider = it },
                                        valueRange = 0f..4f,
                                        steps = 3,
                                        colors = SliderDefaults.colors(thumbColor = OrangePrimary, activeTrackColor = OrangePrimary)
                                    )
                                }

                                // Radius distance settings
                                Column {
                                    Text(if (isHindi) "कितनी दूरी तक काम करने जा सकते हैं? (Radius)" else "Work Radius*", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = OrangeClassic)
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        listOf("Within 5 km", "Within 15 km", "Whole City").forEach { dist ->
                                            val isSel = regWorkerRadius == dist
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .border(width = if (isSel) 1.8.dp else 1.dp, color = if (isSel) OrangePrimary else Color.LightGray, shape = RoundedCornerShape(8.dp))
                                                    .background(if (isSel) OrangePrimary.copy(alpha = 0.1f) else Color.White)
                                                    .clickable { regWorkerRadius = dist }
                                                    .padding(vertical = 6.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(if (isHindi) (if (dist == "Within 5 km") "5 किमी दायरा" else if (dist == "Within 15 km") "15 किमी" else "पूरा शहर") else dist, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }

                                // Skills Bio
                                OutlinedTextField(
                                    value = regWorkerSkillsBio,
                                    onValueChange = { regWorkerSkillsBio = it },
                                    label = { Text(if (isHindi) "हुनर / विवरण (Skills Bio)" else "Custom Skills Bio") },
                                    placeholder = { Text(if (isHindi) "उदा. गारा सीमेंट मिक्स करना, प्लास्टर" else "e.g., Brick mortar mixing, putty sanding") },
                                    modifier = Modifier.fillMaxWidth().height(70.dp),
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = OrangePrimary, focusedContainerColor = SandyBg, unfocusedContainerColor = SandyBg)
                                )

                            } else {
                                // ================= EMPLOYER REGISTRATION =================
                                Text(
                                    text = if (isHindi) "📝 नया ठेकेदार / नियोजक पंजीयन पत्र" else "📝 Employer Project Specification Form",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 15.sp,
                                    color = Color(0xFF0D47A1)
                                )

                                // Recruiter Name
                                OutlinedTextField(
                                    value = regName,
                                    onValueChange = { regName = it },
                                    label = { Text(if (isHindi) "ठेकेदार / मालिक का नाम (Your Name)*" else "Owner Name*") },
                                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF0D47A1)) },
                                    modifier = Modifier.fillMaxWidth().testTag("employer_name_input"),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFF0D47A1),
                                        focusedContainerColor = Color(0xFFF1F5FA),
                                        unfocusedContainerColor = Color(0xFFF1F5FA)
                                    )
                                )

                                // Business / Shop Name
                                OutlinedTextField(
                                    value = regBusinessName,
                                    onValueChange = { regBusinessName = it },
                                    label = { Text(if (isHindi) "दुकान / कंपनी का नाम (Business Name)" else "Company / Shop Name") },
                                    leadingIcon = { Icon(Icons.Default.Business, contentDescription = null, tint = Color(0xFF0D47A1)) },
                                    trailingIcon = {
                                        IconButton(onClick = {
                                            activeVoiceTarget = "business"
                                            val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                                            }
                                            try { speechLauncher.launch(i) } catch(e: Exception) { showMicListening = true }
                                        }) {
                                            Icon(Icons.Default.Mic, contentDescription = "Voice Input", tint = Color(0xFF0D47A1))
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().testTag("employer_business_input"),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFF0D47A1),
                                        focusedContainerColor = Color(0xFFF1F5FA),
                                        unfocusedContainerColor = Color(0xFFF1F5FA)
                                    )
                                )

                                // Employer Phone Number
                                OutlinedTextField(
                                    value = regPhone,
                                    onValueChange = { if (it.length <= 10) regPhone = it },
                                    label = { Text(if (isHindi) "मालिक का मोबाइल नंबर (Mobile)*" else "Employer Mobile*") },
                                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = Color(0xFF0D47A1)) },
                                    modifier = Modifier.fillMaxWidth().testTag("employer_phone_input"),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFF0D47A1),
                                        focusedContainerColor = Color(0xFFF1F5FA),
                                        unfocusedContainerColor = Color(0xFFF1F5FA)
                                    )
                                )

                                // Requested Specialty
                                Column {
                                    Text(
                                        text = if (isHindi) "किस प्रकार के मिस्त्री / मजदूर की आवश्यकता है?*" else "Labor Specialty Category Needed*",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = Color(0xFF1565C0)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        val cats = listOf(
                                            if (isHindi) "मजदूर" else "Helper",
                                            if (isHindi) "मिस्त्री" else "Mason",
                                            if (isHindi) "बिजली" else "Elec",
                                            if (isHindi) "प्लंबर" else "Plumber"
                                        )
                                        cats.forEachIndexed { idx, label ->
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .border(
                                                        width = if (selectedWorkCategoryIndex == idx) 2.dp else 1.dp,
                                                        color = if (selectedWorkCategoryIndex == idx) Color(0xFF0D47A1) else Color.LightGray,
                                                        shape = RoundedCornerShape(8.dp)
                                                    )
                                                    .background(if (selectedWorkCategoryIndex == idx) Color(0xFF0D47A1).copy(alpha = 0.1f) else Color.White)
                                                    .clickable { selectedWorkCategoryIndex = idx }
                                                    .padding(vertical = 8.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(label, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = DarkCharcoal)
                                            }
                                        }
                                    }
                                }

                                // Project type/scale
                                Column {
                                    Text(if (isHindi) "परियोजना स्तर / काम का प्रकार*" else "Project Scale / Type*", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF1565C0))
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        listOf("Small Repair", "Home Project", "Large Construction").forEach { scale ->
                                            val isSel = regEmployerProjectScale == scale
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .border(width = if (isSel) 2.dp else 1.dp, color = if (isSel) Color(0xFF0D47A1) else Color.LightGray, shape = RoundedCornerShape(8.dp))
                                                    .background(if (isSel) Color(0xFF0D47A1).copy(alpha = 0.1f) else Color.White)
                                                    .clickable { regEmployerProjectScale = scale }
                                                    .padding(vertical = 6.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(if (isHindi) (if (scale == "Small Repair") "मरम्मत कार्य" else if (scale == "Home Project") "होम प्रोजेक्ट" else "बड़ी साइट") else scale, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }

                                // Offered daily wage
                                Column {
                                    val offers = when (offeredWageSlideValue.toInt()) {
                                        0 -> 350
                                        1 -> 550
                                        2 -> 800
                                        3 -> 1050
                                        else -> 1500
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(if (isHindi) "प्रस्तावित दैनिक दिहाड़ी:" else "Offered Daily Wage:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF1565C0))
                                        Text("₹$offers / दिन", fontWeight = FontWeight.Black, fontSize = 13.sp, color = Color(0xFF0D47A1))
                                    }
                                    Slider(
                                        value = offeredWageSlideValue,
                                        onValueChange = { offeredWageSlideValue = it },
                                        valueRange = 0f..4f,
                                        steps = 3,
                                        colors = SliderDefaults.colors(thumbColor = Color(0xFF0D47A1), activeTrackColor = Color(0xFF0D47A1))
                                    )
                                }

                                // Site Address + REALISTIC AUTOCOMPLETE SUGGESTION LIST like Ola/Uber
                                Column {
                                    Text(
                                        text = if (isHindi) "साइट / काम का पता (Location Address)*" else "Job Site Address*",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = Color(0xFF1565C0)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    OutlinedTextField(
                                        value = regLocation,
                                        onValueChange = { 
                                            regLocation = it
                                            showLocationSuggestions = it.isNotBlank()
                                            isAddressVerifiedByGps = false
                                        },
                                        placeholder = { Text(if (isHindi) "गंतव्य पता लिखें या जीपीएस बटन दबाएं" else "Type site address or tap GPS") },
                                        leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFF0D47A1)) },
                                        modifier = Modifier.fillMaxWidth().testTag("employer_site_address"),
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFF0D47A1),
                                            focusedContainerColor = Color(0xFFF1F5FA),
                                            unfocusedContainerColor = Color(0xFFF1F5FA)
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))

                                    // Auto-detect GPS button (Ola/Uber functional style)
                                    Button(
                                        onClick = {
                                            isFetchingLocationByGps = true
                                            onSpeak(if (isHindi) "जीपीएस सैटेलाइट द्वारा आपके काम का पता दर्ज किया जा रहा है..." else "Querying GPS constellation satellite for active construction grid coordinate...")
                                            // Simulate quick loading and auto-set
                                            regLocation = if (isHindi) "मेट्रो पिल्लर ७२, अंधेरी ईस्ट, मुम्बई" else "Metro Pillar 72, Andheri East, Mumbai"
                                            isAddressVerifiedByGps = true
                                            showLocationSuggestions = true
                                            Toast.makeText(context, "📍 GPS Coordinates Handshaked!", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = AcceptGreen.copy(alpha = 0.08f)),
                                        border = BorderStroke(1.2.dp, AcceptGreen),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth().height(40.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (isFetchingLocationByGps && regLocation.isEmpty()) {
                                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = AcceptGreen)
                                            } else {
                                                Icon(Icons.Default.MyLocation, contentDescription = null, tint = AcceptGreen, modifier = Modifier.size(16.dp))
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (isHindi) "📍 तत्काल GPS सैटेलाइट द्वारा खोजें" else "📍 Instantly Capture Site Coordinates",
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 11.sp,
                                                color = AcceptGreen
                                            )
                                        }
                                    }

                                    // GPS Location Autocomplete Suggestions - Ola/Uber real working feel!
                                    if (showLocationSuggestions) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF9F9FB)),
                                            border = BorderStroke(1.dp, Color.LightGray),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(4.dp)) {
                                                Text(
                                                    text = if (isHindi) "मल्टीप्ल मैचिंग साइट पते (OLA/UBER MATCHES):" else "Matching Coordinates (OLA/UBER MAP MATCH):",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = Color.Gray,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                                locationSuggestionsList.forEach { sugg ->
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable {
                                                                regLocation = sugg.replace("📍 ", "")
                                                                showLocationSuggestions = false
                                                                isAddressVerifiedByGps = true
                                                                onSpeak(if (isHindi) "पता चुना गया" else "Address coordinate locked")
                                                            }
                                                            .padding(vertical = 10.dp, horizontal = 12.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFFE65100), modifier = Modifier.size(16.dp))
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(sugg, fontSize = 12.sp, color = DarkCharcoal, fontWeight = FontWeight.Bold)
                                                    }
                                                    Divider(color = Color.LightGray.copy(alpha = 0.5f))
                                                }
                                            }
                                        }
                                    }

                                    if (isAddressVerifiedByGps) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(AcceptGreen.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                                                .padding(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AcceptGreen, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (isHindi) "सक्रिय जीपीएस लोकेशन लॉक और सत्यापित!" else "Active GPS Coordinates Verified & Satellites Synced!",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Black,
                                                color = AcceptGreen
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Consent Terms & Trigger Registration Save directly onto core Room Database
                            Button(
                                onClick = {
                                    if (regName.trim().isEmpty()) {
                                        Toast.makeText(context, if (isHindi) "कृपया अपना शुभ नाम लिखें!" else "Please enter your name!", Toast.LENGTH_SHORT).show()
                                    } else if (regPhone.length < 10) {
                                        Toast.makeText(context, if (isHindi) "कृपया सही 10-अंकों का नंबर दर्ज करें!" else "Please enter correct 10 digits number!", Toast.LENGTH_SHORT).show()
                                    } else if (!isPhotoCaptured) {
                                        Toast.makeText(context, if (isHindi) "सेल्फी प्रोफाइल फोटो वेरिफिकेशन आवश्यक है!" else "Profile selfie photo verification is mandatory!", Toast.LENGTH_SHORT).show()
                                    } else if (regAadhaar.length < 12 || !isAadhaarVerified) {
                                        Toast.makeText(context, if (isHindi) "आधार बायोमेट्रिक सत्यापन आवश्यक है!" else "Government Aadhaar verification is mandatory!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        val preferCategory = when (selectedWorkCategoryIndex) {
                                            0 -> "मजदूर"
                                            1 -> "मिस्त्री"
                                            2 -> "बिजली मिस्त्री"
                                            else -> "प्लंबर"
                                        }

                                        if (selectedRegisterRole == "Worker" || selectedRegisterRole == "Contractor") {
                                            val mappedExpectedWage = when (regWorkerWageSlider.toInt()) {
                                                0 -> 350
                                                1 -> 500
                                                2 -> 700
                                                3 -> 950
                                                else -> 1200
                                            }
                                            viewModel.updateProfile(
                                                name = regName,
                                                phone = regPhone,
                                                location = regWorkerRadius,
                                                role = preferCategory,
                                                skills = regWorkerSkillsBio.ifBlank { "सामान्य कार्य, $preferCategory काम" },
                                                experience = when(regWorkerExpYears) {
                                                    "1-2 वर्ष", "1-2 Yrs" -> 1
                                                    "3-5 वर्ष", "3-5 Yrs" -> 4
                                                    else -> 6
                                                },
                                                education = regWorkerExpYears,
                                                bio = "अपेक्षित दिहाड़ी: ₹$mappedExpectedWage/दिन, दायरा: $regWorkerRadius",
                                                userRoleType = selectedRegisterRole,
                                                businessName = "",
                                                offeredWage = mappedExpectedWage,
                                                aadhaarNumber = regAadhaar,
                                                isAadhaarVerified = isAadhaarVerified,
                                                profilePicUrl = "locked_facial_verified"
                                            )
                                            Toast.makeText(context, if (isHindi) "खाता पंजीयन सफल!" else "Account registered!", Toast.LENGTH_SHORT).show()
                                            onSpeak(if (isHindi) "खाता पूर्ण हुआ! रोजगार सेतु में आपका स्वागत है, $regName।" else "Account registered successfully! Welcome to Rozgaar Setu, $regName.")
                                        } else {
                                            val bName = regBusinessName.ifBlank { if (isHindi) "सामान्य कंस्ट्रक्शन साइट" else "General Worksite" }
                                            val loc = regLocation.ifBlank { if (isHindi) "अँधेरी ईस्ट, मुम्बई" else "Andheri East, Mumbai" }
                                            val offers = when (offeredWageSlideValue.toInt()) {
                                                0 -> 350
                                                1 -> 550
                                                2 -> 800
                                                3 -> 1050
                                                else -> 1500
                                            }

                                            viewModel.updateProfile(
                                                name = regName,
                                                phone = regPhone,
                                                location = loc,
                                                role = preferCategory, 
                                                skills = "Employer / Builder ($regEmployerProjectScale)",
                                                experience = 0,
                                                education = "Degree",
                                                bio = "परियोजना: $bName, स्तर: $regEmployerProjectScale",
                                                userRoleType = "Employer",
                                                businessName = bName,
                                                offeredWage = offers,
                                                aadhaarNumber = regAadhaar,
                                                isAadhaarVerified = isAadhaarVerified,
                                                profilePicUrl = "locked_facial_verified"
                                            )
                                            Toast.makeText(context, if (isHindi) "मालिक खाता तैयार हुआ!" else "Recruiter Profile Created!", Toast.LENGTH_SHORT).show()
                                            onSpeak(if (isHindi) "मालिक खाता पूर्ण हुआ! आपका स्वागत है $regName।" else "Employer profile registered successfully! Welcome $regName.")
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = themeColor),
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = if (selectedRegisterRole == "Worker") 
                                        (if (isHindi) "सहमत हैं और नया मजदूर खाता बनाएं 👷" else "Create Worker Account 👷")
                                        else if (selectedRegisterRole == "Contractor")
                                        (if (isHindi) "सहमत हैं और नया ठेकेदार खाता बनाएं 🛠️" else "Create Contractor Account 🛠️")
                                        else (if (isHindi) "सहमत हैं और नया मालिक खाता बनाएं 🏢" else "Create Recruiter Account 🏢"),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }

            // Quick bypass card for guest demo
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.5.dp, GrayBorder),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (isHindi) "⚡ त्वरित बायपास (Direct Skip Testing):" else "⚡ Direct Bypass Gates:",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = OrangeClassic
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Guest Worker entrance (Drives worker layout)
                            OutlinedButton(
                                onClick = {
                                    viewModel.updateProfile(
                                        name = "रमेश यादव (Ramesh Yadav)",
                                        phone = "9876543210",
                                        location = "मुम्बई (Mumbai)",
                                        role = "मिस्त्री",
                                        skills = "कारीगर, मिस्त्री",
                                        experience = 3,
                                        education = "10th Pass",
                                        bio = "साधारण मेहमान यूजर",
                                        userRoleType = "Worker",
                                        aadhaarNumber = "112233445566",
                                        isAadhaarVerified = true,
                                        profilePicUrl = "verified_bypass"
                                    )
                                    onSpeak(if (isHindi) "कामगार बाईपास सक्रिय" else "Guest Worker login selected")
                                },
                                border = BorderStroke(1.5.dp, OrangePrimary),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = OrangeClassic),
                                modifier = Modifier.weight(1f).height(46.dp),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Engineering, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isHindi) "मजदूर प्रवेश" else "Worker Skip",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            // Guest Recruiter entrance (Drives Employer layout)
                            OutlinedButton(
                                onClick = {
                                    viewModel.updateProfile(
                                        name = "अशोक मेहता (Ashok Mehta)",
                                        phone = "9812345678",
                                        location = "Andheri East, Mumbai",
                                        role = "मिस्त्री",
                                        skills = "Contractor",
                                        experience = 0,
                                        education = "B.Tech",
                                        bio = "मेहता बिल्डिंग बिल्डर्स",
                                        userRoleType = "Employer",
                                        businessName = "मेहता बिल्डर्स (Mehta Builders)",
                                        offeredWage = 800,
                                        aadhaarNumber = "665544332211",
                                        isAadhaarVerified = true,
                                        profilePicUrl = "verified_bypass"
                                    )
                                    onSpeak(if (isHindi) "मालिक गेटवे चालू हुआ" else "Guest Recruiter skipping complete")
                                },
                                border = BorderStroke(1.5.dp, Color(0xFF0D47A1)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF0D47A1)),
                                modifier = Modifier.weight(1f).height(46.dp),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Business, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isHindi) "मालिक प्रवेश" else "Recruiter Skip",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showMicListening) {
        Dialog(onDismissRequest = { showMicListening = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Mic, contentDescription = "Recording", tint = OrangePrimary, modifier = Modifier.size(54.dp))
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("सुन रहा हूँ... (Listening)", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = DarkCharcoal)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (activeVoiceTarget == "name") {
                            if (isHindi) "मालिक / आपका नाम बोलें..." else "Please speak owner/your name clearly..."
                        } else {
                            if (isHindi) "दुकान / कंपनी का नाम बोलें..." else "Please speak business name clearly..."
                        },
                        fontSize = 14.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            if (activeVoiceTarget == "name") {
                                regName = "रमेश यादव (Ramesh Yadav)"
                            } else {
                                regBusinessName = "यादव इलेक्ट्रॉनिक्स (Yadav Electronics)"
                            }
                            showMicListening = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = themeColor)
                    ) {
                        Text("सफल (Finish)", color = Color.White)
                    }
                }
            }
        }
    }
}

// --- OLD PREVIOUS DEPRECATED ONBOARDING SCREEN ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginSignupScreenOldDeprecated(
    viewModel: MainViewModel,
    isHindi: Boolean,
    onLanguageToggle: () -> Unit,
    onSpeak: (String) -> Unit
) {
    val context = LocalContext.current
    var isLoginSelected by remember { mutableStateOf(false) } // Default to Sign Up for new installs
    
    // Role selection: "Worker" (मजदूर) vs "Employer" (काम देने वाला/ठेकेदार)
    var selectedRegisterRole by remember { mutableStateOf("Worker") } // "Worker" or "Employer"

    // Login inputs
    var loginPhone by remember { mutableStateOf("") }
    var loginOtp by remember { mutableStateOf("") }
    var showOtpField by remember { mutableStateOf(false) }

    // Signup/Register inputs (Shared)
    var regName by remember { mutableStateOf("") }
    var regPhone by remember { mutableStateOf("") }
    var selectedWorkCategoryIndex by remember { mutableStateOf(1) } // Default to Mason / मिस्त्री
    var showMicListening by remember { mutableStateOf(false) }
    var activeVoiceTarget by remember { mutableStateOf("name") } // "name" or "business"

    // Worker Specific Inputs (Custom themed)
    var regWorkerExpYears by remember { mutableStateOf("Experienced (3-5 Years)") } // Experience Level
    var regWorkerWageSlider by remember { mutableStateOf(2f) } // Expected Wage 300 to 1200
    var regWorkerRadius by remember { mutableStateOf("Within 5 km") } // Travel Range
    var regWorkerSkillsBio by remember { mutableStateOf("") } // Custom bio built or preset

    // Employer Specific Inputs (Custom themed & site matching)
    var regBusinessName by remember { mutableStateOf("") }
    var regLocation by remember { mutableStateOf("") }
    var offeredWageSlideValue by remember { mutableStateOf(1f) } // 0..4
    var regEmployerProjectScale by remember { mutableStateOf("Small Repair") } // Project Level/Scale
    var isFetchingLocationByGps by remember { mutableStateOf(false) }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull() ?: ""
            if (spokenText.isNotEmpty()) {
                if (activeVoiceTarget == "name") {
                    regName = spokenText
                } else {
                    regBusinessName = spokenText
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SandyBg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {

        // 2. Main Login/Register Content in front of map
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Top Branding Card with high quality typography
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CleanWhite.copy(alpha = 0.95f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    border = BorderStroke(1.5.dp, OrangePrimary),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "ROZGAAR SETU",
                                fontWeight = FontWeight.Black,
                                style = MaterialTheme.typography.titleMedium,
                                color = OrangeClassic,
                                letterSpacing = 2.sp
                            )
                            
                            // High Visibility Language toggle
                            Button(
                                onClick = onLanguageToggle,
                                colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary.copy(alpha = 0.1f)),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                border = BorderStroke(1.dp, OrangePrimary),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text(
                                    text = if (isHindi) "English" else "हिन्दी",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = OrangeClassic
                                )
                            }
                        }
                        
                        Divider(modifier = Modifier.padding(vertical = 10.dp), color = GrayBorder)
                        
                        Text(
                            text = if (isHindi) "🤝 रोजगार सेतु - कामगार और मालिक रडार" else "🤝 Rozgaar Setu - Workers & Recruiter Connect",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            color = DarkCharcoal,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = if (isHindi) "ओला-इकोनॉमी स्टाइल लाइव लोकेशन मैचिंग ऐप" else "Ola/Uber style live location matching engine",
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp,
                            color = MutedSlate,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }

            // Tabs for switching between LOGIN vs REGISTER
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(ContrastLight)
                        .padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (!isLoginSelected) OrangePrimary else Color.Transparent)
                            .clickable { isLoginSelected = false }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isHindi) "नया खाता बनाएं (Register)" else "Register Profile",
                            color = if (!isLoginSelected) Color.White else DarkCharcoal,
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isLoginSelected) OrangePrimary else Color.Transparent)
                            .clickable { isLoginSelected = true }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isHindi) "लॉगिन करें (Login)" else "Login / Enter",
                            color = if (isLoginSelected) Color.White else DarkCharcoal,
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp
                        )
                    }
                }
            }

            // Card Body (Forms matching chosen tab)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CleanWhite.copy(alpha = 0.98f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    border = BorderStroke(1.dp, GrayBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        if (isLoginSelected) {
                            // --- LOGIN SECTION ---
                            Text(
                                text = if (isHindi) "अपने मोबाइल नंबर से प्रवेश करें" else "Enter Mobile Number to Log In",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = DarkCharcoal
                            )

                            OutlinedTextField(
                                value = loginPhone,
                                onValueChange = { if (it.length <= 10) loginPhone = it },
                                placeholder = { Text("9876543210") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = OrangePrimary) },
                                modifier = Modifier.fillMaxWidth().testTag("login_phone_input"),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = OrangePrimary,
                                    unfocusedBorderColor = Color.Gray,
                                    focusedContainerColor = SandyBg,
                                    unfocusedContainerColor = SandyBg
                                )
                            )

                            if (showOtpField) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (isHindi) "4-अंकों का ओटीपी कोड डालें (OTP: 1234)" else "Enter 4-Digit OTP Code (OTP: 1234)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = DarkCharcoal
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = loginOtp,
                                    onValueChange = { if (it.length <= 4) loginOtp = it },
                                    placeholder = { Text("1234") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = OrangePrimary) },
                                    modifier = Modifier.fillMaxWidth().testTag("login_otp_input"),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = OrangePrimary,
                                        unfocusedBorderColor = Color.Gray,
                                        focusedContainerColor = SandyBg,
                                        unfocusedContainerColor = SandyBg
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Button(
                                onClick = {
                                    if (loginPhone.length < 10) {
                                        Toast.makeText(context, if (isHindi) "कृपया सही 10-अंकों का नंबर दर्ज करें!" else "Please enter a valid 10-digit number!", Toast.LENGTH_SHORT).show()
                                        onSpeak(if (isHindi) "कृपया सही फ़ोन नंबर दर्ज करें।" else "Please enter your valid phone number.")
                                    } else if (!showOtpField) {
                                        showOtpField = true
                                        onSpeak(if (isHindi) "ओटीपी भेजा गया। आपके फ़ोन पर प्राप्त हुआ ४ अंकों का कोड दर्ज करें।" else "OTP sent. Please enter the 4-digit code sent to your phone.")
                                        Toast.makeText(context, if (isHindi) "📍 ओटीपी भेज दिया गया है!" else "📍 OTP Sent Successfully!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        if (loginOtp.length < 4) {
                                            Toast.makeText(context, if (isHindi) "कृपया 4-अंकों का ओटीपी दर्ज करें!" else "Please enter a valid 4-digit OTP!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            // Successful simulated login!
                                            viewModel.updateProfile(
                                                name = "रमेश यादव",
                                                phone = loginPhone,
                                                location = "मुम्बई (Mumbai)",
                                                role = "मिस्त्री",
                                                skills = "ईंट जुड़ाई, प्लास्टर",
                                                experience = 3,
                                                education = "10th Pass",
                                                bio = "परिश्रमी निर्माण कामगार",
                                                userRoleType = "Worker"
                                            )
                                            Toast.makeText(context, if (isHindi) "लॉगिन सफल!" else "Login Successful!", Toast.LENGTH_SHORT).show()
                                            onSpeak(if (isHindi) "आपका लॉगिन सफल रहा। काम ढूंढना शुरू करें।" else "Login successful. Let's find some work.")
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary),
                                modifier = Modifier.fillMaxWidth().height(54.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = if (!showOtpField) {
                                        if (isHindi) "ओटीपी भेजें (Get OTP)" else "Get OTP Code"
                                    } else {
                                        if (isHindi) "प्रवेश करें (Verify & Login)" else "Verify & Login"
                                    },
                                    fontWeight = FontWeight.Black,
                                    fontSize = 17.sp
                                )
                            }
                        } else {
                            // --- NEW MULTI-PERSONA REGISTER FORM ---
                            
                            // Role selector sub-tab with highly intuitive icons
                            Text(
                                text = if (isHindi) "आप क्या करना चाहते हैं? (Select Your Role)*" else "Select Business Role*",
                                fontWeight = FontWeight.Black,
                                fontSize = 15.sp,
                                color = DarkCharcoal
                            )
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Seeker Option / मज़दूर
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(
                                            2.dp,
                                            if (selectedRegisterRole == "Worker") OrangePrimary else Color.LightGray,
                                            RoundedCornerShape(12.dp)
                                        )
                                        .background(if (selectedRegisterRole == "Worker") OrangePrimary.copy(alpha = 0.08f) else Color.White)
                                        .clickable {
                                            selectedRegisterRole = "Worker"
                                            onSpeak(if (isHindi) "कामगार यानी मजदूर श्रेणी चुनी गई" else "Worker role selected")
                                        }
                                        .padding(10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            Icons.Default.Engineering,
                                            contentDescription = null,
                                            tint = if (selectedRegisterRole == "Worker") OrangePrimary else Color.Gray,
                                            modifier = Modifier.size(36.dp)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = if (isHindi) "👷 काम ढूंढें\n(मजदूर / कामगार)" else "👷 Find Work\n(Laborer / Worker)",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (selectedRegisterRole == "Worker") OrangeClassic else Color.Gray,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }

                                // Employer Option / काम देने वाला
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(
                                            2.dp,
                                            if (selectedRegisterRole == "Employer") OrangePrimary else Color.LightGray,
                                            RoundedCornerShape(12.dp)
                                        )
                                        .background(if (selectedRegisterRole == "Employer") OrangePrimary.copy(alpha = 0.08f) else Color.White)
                                        .clickable {
                                            selectedRegisterRole = "Employer"
                                            onSpeak(if (isHindi) "काम देने वाले मालिक ठेकेदार की श्रेणी चुनी गई" else "Employer builder role selected")
                                        }
                                        .padding(10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            Icons.Default.Business,
                                            contentDescription = null,
                                            tint = if (selectedRegisterRole == "Employer") OrangePrimary else Color.Gray,
                                            modifier = Modifier.size(36.dp)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = if (isHindi) "🏢 काम दें\n(ठेकेदार / मालिक)" else "🏢 Hire Workers\n(Employer)",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (selectedRegisterRole == "Employer") OrangeClassic else Color.Gray,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }

                            Divider(color = GrayBorder, modifier = Modifier.padding(vertical = 4.dp))

                            if (selectedRegisterRole == "Worker") {
                                // ================= WORKER FORM =================
                                // Customized details specifically suitable to Worker theme (wages slider, experience level, travel range, and quick preset bio chips)
                                Text(
                                    text = if (isHindi) "👷 कामगार विवरण भरें (Worker Profile Details)" else "👷 Setup Your Worker Persona",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp,
                                    color = OrangeClassic
                                )

                                Column {
                                    Text(
                                        text = if (isHindi) "आपका शुभ नाम (Full Name)*" else "Full Name*",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 14.sp,
                                        color = DarkCharcoal
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    OutlinedTextField(
                                        value = regName,
                                        onValueChange = { regName = it },
                                        placeholder = { Text(if (isHindi) "जैसे: रमेश यादव" else "e.g., Ramesh Yadav") },
                                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = OrangePrimary) },
                                        trailingIcon = {
                                            IconButton(onClick = {
                                                activeVoiceTarget = "name"
                                                onSpeak(if (isHindi) "कृपया अपना शुभ नाम बोलें।" else "Please speak your name clearly.")
                                                try {
                                                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (isHindi) "hi-IN" else "en-US")
                                                        putExtra(RecognizerIntent.EXTRA_PROMPT, if (isHindi) "अपना शुभ नाम बोलें..." else "Please speak your name...")
                                                    }
                                                    speechLauncher.launch(intent)
                                                } catch (e: Exception) {
                                                    showMicListening = true
                                                }
                                            }) {
                                                Icon(Icons.Default.Mic, contentDescription = "Voice input", tint = OrangePrimary)
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().testTag("register_name_input"),
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = OrangePrimary,
                                            unfocusedBorderColor = Color.Gray,
                                            focusedContainerColor = SandyBg,
                                            unfocusedContainerColor = SandyBg
                                        )
                                    )
                                }

                                Column {
                                    Text(
                                        text = if (isHindi) "मज़दूर का मोबाइल नंबर" else "Mobile Number",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 14.sp,
                                        color = DarkCharcoal
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    OutlinedTextField(
                                        value = regPhone,
                                        onValueChange = { if (it.length <= 10) regPhone = it },
                                        placeholder = { Text("9876543210") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = OrangePrimary) },
                                        modifier = Modifier.fillMaxWidth().testTag("register_phone_input"),
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = OrangePrimary,
                                            unfocusedBorderColor = Color.Gray,
                                            focusedContainerColor = SandyBg,
                                            unfocusedContainerColor = SandyBg
                                        )
                                    )
                                }

                                // Choose Job Type / Preferred Category Choice
                                Column {
                                    Text(
                                        text = if (isHindi) "आप किस काम के मिस्त्री / मजदूर हैं?*" else "Your Work Specialty*",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 14.sp,
                                        color = DarkCharcoal
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    
                                    val roles = listOf(
                                        WorkerCategoryItem(0, "मजदूर", Icons.Default.Engineering),
                                        WorkerCategoryItem(1, "मिस्त्री", Icons.Default.Construction),
                                        WorkerCategoryItem(2, "बिजली वाला", Icons.Default.FlashOn),
                                        WorkerCategoryItem(3, "प्लंबर", Icons.Default.WaterDrop)
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        roles.forEach { role ->
                                            val isSelected = selectedWorkCategoryIndex == role.idx
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .border(
                                                        width = if (isSelected) 2.2.dp else 1.dp,
                                                        color = if (isSelected) OrangePrimary else Color.LightGray,
                                                        shape = RoundedCornerShape(8.dp)
                                                    )
                                                    .background(if (isSelected) OrangePrimary.copy(alpha = 0.08f) else Color.White)
                                                    .clickable {
                                                        selectedWorkCategoryIndex = role.idx
                                                        val pBio = when (role.idx) {
                                                            0 -> if (isHindi) "मसाला मिश्रण, मिट्टी ढोना, भारी काम" else "Masala mixing, cement lifting, heavy load"
                                                            1 -> if (isHindi) "ईंट जुड़ाई, प्लास्टर, टाइल्स" else "Brick laying, plaster structure, tiles"
                                                            2 -> if (isHindi) "हाउस वायरिंग, फैन रिपेयर, बोर्ड फिटिंग" else "House wiring, fan repair, light fitting"
                                                            else -> if (isHindi) "नल लीकेज सुधार, पाइप पाइपिंग, मोटर फिटिंग" else "Tap repairing, pipe fittings, motor assembly"
                                                        }
                                                        regWorkerSkillsBio = pBio
                                                        onSpeak(if (isHindi) "चयनित काम: ${role.hindiLabel}" else "Selected work: ${role.hindiLabel}")
                                                    }
                                                    .padding(6.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Icon(
                                                        imageVector = role.icon,
                                                        contentDescription = null,
                                                        tint = if (isSelected) OrangePrimary else Color.Gray,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = role.hindiLabel,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isSelected) OrangeClassic else Color.Gray,
                                                        maxLines = 1
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // EXPERIENCE LEVEL CHIPS Choice
                                Column {
                                    Text(
                                        text = if (isHindi) "काम का कुल अनुभव (Work Experience)*" else "Work Experience Level*",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 14.sp,
                                        color = DarkCharcoal
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        val expOptions = listOf(
                                            if (isHindi) "1-2 वर्ष (Beginner)" else "1-2 Years (Beginner)",
                                            if (isHindi) "3-5 वर्ष (Experienced)" else "3-5 Years (Experienced)",
                                            if (isHindi) "5+ वर्ष (Master / उस्ताद)" else "5+ Years (Master)"
                                        )
                                        expOptions.forEach { opt ->
                                            val isSelected = regWorkerExpYears == opt
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .border(
                                                        width = if (isSelected) 1.5.dp else 1.dp,
                                                        color = if (isSelected) OrangePrimary else Color.LightGray,
                                                        shape = RoundedCornerShape(8.dp)
                                                    )
                                                    .background(if (isSelected) OrangePrimary.copy(alpha = 0.05f) else Color.White)
                                                    .clickable {
                                                        regWorkerExpYears = opt
                                                        onSpeak(opt)
                                                    }
                                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = opt,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSelected) OrangeClassic else Color.DarkGray,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                    }
                                }

                                // EXPECTED MINIMUM WAGE SLIDER
                                Column {
                                    val mappedWage = when (regWorkerWageSlider.toInt()) {
                                        0 -> 350
                                        1 -> 500
                                        2 -> 700
                                        3 -> 950
                                        else -> 1200
                                    }
                                    Text(
                                        text = if (isHindi) "अपेक्षित न्यूनतम दैनिक दिहाड़ी: ₹$mappedWage / दिन*" else "Expected Daily Wage: ₹$mappedWage / day*",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 14.sp,
                                        color = DarkCharcoal
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Slider(
                                        value = regWorkerWageSlider,
                                        onValueChange = {
                                            regWorkerWageSlider = it
                                        },
                                        valueRange = 0f..4f,
                                        steps = 3,
                                        colors = SliderDefaults.colors(
                                            thumbColor = OrangePrimary,
                                            activeTrackColor = OrangePrimary,
                                            inactiveTrackColor = GrayBorder
                                        )
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("₹350", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                        Text("₹500", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                        Text("₹700", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                        Text("₹950", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                        Text("₹1200", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                    }
                                }

                                // PREFERRED TRAVEL RADIUS
                                Column {
                                    Text(
                                        text = if (isHindi) "आप कितनी दूरी तक काम करने जा सकते हैं?*" else "Preferred Work Radius*",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 14.sp,
                                        color = DarkCharcoal
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        val radiuses = listOf(
                                            if (isHindi) "5 किमी (Within 5 km)" else "Within 5 km",
                                            if (isHindi) "15 किमी (Within 15 km)" else "Within 15 km",
                                            if (isHindi) "पूरा शहर (Whole City)" else "Whole City"
                                        )
                                        radiuses.forEach { rad ->
                                            val isSelected = regWorkerRadius == rad
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .border(
                                                        width = if (isSelected) 1.5.dp else 1.dp,
                                                        color = if (isSelected) OrangePrimary else Color.LightGray,
                                                        shape = RoundedCornerShape(8.dp)
                                                    )
                                                    .background(if (isSelected) OrangePrimary.copy(alpha = 0.05f) else Color.White)
                                                    .clickable {
                                                        regWorkerRadius = rad
                                                        onSpeak(rad)
                                                    }
                                                    .padding(vertical = 8.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = rad,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSelected) OrangeClassic else Color.DarkGray,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                    }
                                }

                                // QUICK BIO Preset Appender or Input
                                Column {
                                    Text(
                                        text = if (isHindi) "हुनर / काम का संक्षिप्त विवरण (Your Skills Bio)" else "Special Skills Bio Summary",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 14.sp,
                                        color = DarkCharcoal
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    OutlinedTextField(
                                        value = regWorkerSkillsBio,
                                        onValueChange = { regWorkerSkillsBio = it },
                                        placeholder = { Text(if (isHindi) "जैसे: प्लास्टर और आरसीसी ढलाई का शानदार अनुभव" else "e.g., Expert plaster laying and brick masonry") },
                                        leadingIcon = { Icon(Icons.Default.Verified, contentDescription = null, tint = OrangePrimary) },
                                        modifier = Modifier.fillMaxWidth().height(80.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = OrangePrimary,
                                            unfocusedBorderColor = Color.Gray,
                                            focusedContainerColor = SandyBg,
                                            unfocusedContainerColor = SandyBg
                                        )
                                    )
                                    
                                    // Preset Tag Chips
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(text = if (isHindi) "⚡ तुरंत जोड़ने के लिए छुएं (Presets):" else "⚡ Quick presets (Tap to append):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MutedSlate)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    
                                    val presets = when (selectedWorkCategoryIndex) {
                                        0 -> listOf("भारी सामान उठाना", "सीमेंट मसाला मिलाना", "मिट्टी खुदाई")
                                        1 -> listOf("ईंट जोड़ाई कारीगरी", "सटीक प्लास्टर फिनिशिंग", "आरसीसी कालम ढलाई")
                                        2 -> listOf("कंसिल्ड हाउस वायरिंग", "पैनल बोर्ड सुधार", "शार्ट सर्किट फाल्ट दुरुस्ती")
                                        else -> listOf("पानी मोटर फिटिंग", "नल लीकेज बंद करना", "जीआई / पीवीसी पाइप फिटिंग")
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        presets.forEach { preset ->
                                            Box(
                                                modifier = Modifier
                                                    .background(Color.White, RoundedCornerShape(16.dp))
                                                    .border(1.dp, OrangePrimary.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                                                    .clickable {
                                                        regWorkerSkillsBio = if (regWorkerSkillsBio.isBlank()) preset else "$regWorkerSkillsBio, $preset"
                                                        onSpeak(preset)
                                                    }
                                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                                            ) {
                                                Text(text = preset, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = OrangeClassic)
                                            }
                                        }
                                    }
                                }

                            } else {
                                // ================= EMPLOYER FORM =================
                                // Custom suited to Recruiter / Job Site Theme (Business logo, project size scale, address detection)
                                Text(
                                    text = if (isHindi) "🏢 मालिक / ठेकेदार विवरण भरें (Recruiter Profile Details)" else "🏢 Setup Recruiter Theme Persona",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp,
                                    color = OrangeClassic
                                )

                                Column {
                                    Text(
                                        text = if (isHindi) "मालिक / ठेकेदार का शुभ नाम*" else "Owner / Recruiter Name*",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 14.sp,
                                        color = DarkCharcoal
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    OutlinedTextField(
                                        value = regName,
                                        onValueChange = { regName = it },
                                        placeholder = { Text(if (isHindi) "जैसे: संजय मेहता" else "e.g., Sanjay Mehta") },
                                        leadingIcon = { Icon(Icons.Default.AccountBox, contentDescription = null, tint = OrangePrimary) },
                                        trailingIcon = {
                                            IconButton(onClick = {
                                                activeVoiceTarget = "name"
                                                onSpeak(if (isHindi) "कृपया अपना शुभ नाम बोलें।" else "Please speak your name clearly.")
                                                try {
                                                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (isHindi) "hi-IN" else "en-US")
                                                        putExtra(RecognizerIntent.EXTRA_PROMPT, if (isHindi) "अपना शुभ नाम बोलें..." else "Please speak your name...")
                                                    }
                                                    speechLauncher.launch(intent)
                                                } catch (e: Exception) {
                                                    showMicListening = true
                                                }
                                            }) {
                                                Icon(Icons.Default.Mic, contentDescription = "Voice input", tint = OrangePrimary)
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().testTag("employer_name_input"),
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = OrangePrimary,
                                            unfocusedBorderColor = Color.Gray,
                                            focusedContainerColor = SandyBg,
                                            unfocusedContainerColor = SandyBg
                                        )
                                    )
                                }

                                Column {
                                    Text(
                                        text = if (isHindi) "दुकान / कंपनी का नाम*" else "Business / Company Name*",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 14.sp,
                                        color = DarkCharcoal
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    OutlinedTextField(
                                        value = regBusinessName,
                                        onValueChange = { regBusinessName = it },
                                        placeholder = { Text(if (isHindi) "जैसे: मेहता हार्डवेयर / कंस्ट्रक्शन" else "e.g., Mehta Hardware / Builder") },
                                        leadingIcon = { Icon(Icons.Default.Storefront, contentDescription = null, tint = OrangePrimary) },
                                        trailingIcon = {
                                            IconButton(onClick = {
                                                activeVoiceTarget = "business"
                                                onSpeak(if (isHindi) "कृपया दुकान या संस्था का नाम बोलें।" else "Please speak business name clearly.")
                                                try {
                                                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (isHindi) "hi-IN" else "en-US")
                                                        putExtra(RecognizerIntent.EXTRA_PROMPT, if (isHindi) "दुकान का नाम बोलें..." else "Please speak business name...")
                                                    }
                                                    speechLauncher.launch(intent)
                                                } catch (e: Exception) {
                                                    showMicListening = true
                                                }
                                            }) {
                                                Icon(Icons.Default.Mic, contentDescription = "Voice input", tint = OrangePrimary)
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().testTag("employer_business_input"),
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = OrangePrimary,
                                            unfocusedBorderColor = Color.Gray,
                                            focusedContainerColor = SandyBg,
                                            unfocusedContainerColor = SandyBg
                                        )
                                    )
                                }

                                Column {
                                    Text(
                                        text = if (isHindi) "मालिक का मोबाइल नंबर*" else "Employer Mobile Number*",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 14.sp,
                                        color = DarkCharcoal
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    OutlinedTextField(
                                        value = regPhone,
                                        onValueChange = { if (it.length <= 10) regPhone = it },
                                        placeholder = { Text("9812345678") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = OrangePrimary) },
                                        modifier = Modifier.fillMaxWidth().testTag("employer_phone_input"),
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = OrangePrimary,
                                            unfocusedBorderColor = Color.Gray,
                                            focusedContainerColor = SandyBg,
                                            unfocusedContainerColor = SandyBg
                                        )
                                    )
                                }

                                // Required worker category grid
                                Column {
                                    Text(
                                        text = if (isHindi) "किस श्रेणी के मज़दूर की आवश्यकता है?*" else "Worker Category Needed*",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 14.sp,
                                        color = DarkCharcoal
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    
                                    val roles = listOf(
                                        WorkerCategoryItem(0, "मजदूर", Icons.Default.Engineering),
                                        WorkerCategoryItem(1, "मिस्त्री", Icons.Default.Construction),
                                        WorkerCategoryItem(2, "बिजली वाला", Icons.Default.FlashOn),
                                        WorkerCategoryItem(3, "प्लंबर", Icons.Default.WaterDrop)
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        roles.forEach { role ->
                                            val isSelected = selectedWorkCategoryIndex == role.idx
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .border(
                                                        width = if (isSelected) 2.2.dp else 1.dp,
                                                        color = if (isSelected) OrangePrimary else Color.LightGray,
                                                        shape = RoundedCornerShape(8.dp)
                                                    )
                                                    .background(if (isSelected) OrangePrimary.copy(alpha = 0.08f) else Color.White)
                                                    .clickable {
                                                        selectedWorkCategoryIndex = role.idx
                                                        val roleRead = if (isHindi) "चुनें: ${role.hindiLabel}" else "Requested category: ${role.hindiLabel}"
                                                        onSpeak(roleRead)
                                                    }
                                                    .padding(6.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Icon(
                                                        imageVector = role.icon,
                                                        contentDescription = null,
                                                        tint = if (isSelected) OrangePrimary else Color.Gray,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = role.hindiLabel,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isSelected) OrangeClassic else Color.Gray,
                                                        maxLines = 1
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // Project Scale/Scope Needed
                                Column {
                                    Text(
                                        text = if (isHindi) "काम का स्तर / प्रकार*" else "Project Scale / Type*",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 14.sp,
                                        color = DarkCharcoal
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        val scales = listOf(
                                            if (isHindi) "मरम्मत (Repair)" else "Repair Work",
                                            if (isHindi) "घर प्रोजेक्ट (House)" else "Home Project",
                                            if (isHindi) "बड़ा निर्माण (Large)" else "Large Site"
                                        )
                                        scales.forEach { scl ->
                                            val isSelected = regEmployerProjectScale == scl
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .border(
                                                        width = if (isSelected) 1.5.dp else 1.dp,
                                                        color = if (isSelected) OrangePrimary else Color.LightGray,
                                                        shape = RoundedCornerShape(8.dp)
                                                    )
                                                    .background(if (isSelected) OrangePrimary.copy(alpha = 0.05f) else Color.White)
                                                    .clickable {
                                                        regEmployerProjectScale = scl
                                                        onSpeak(scl)
                                                    }
                                                    .padding(vertical = 8.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = scl,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSelected) OrangeClassic else Color.DarkGray,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                    }
                                }

                                // Offered Wage Slider
                                Column {
                                    val wageLabel = when (offeredWageSlideValue.toInt()) {
                                        0 -> "₹350"
                                        1 -> "₹550"
                                        2 -> "₹800"
                                        3 -> "₹1050"
                                        else -> "₹1500"
                                    }
                                    Text(
                                        text = if (isHindi) "प्रस्तावित दैनिक मजदूरी दर: $wageLabel / दिन*" else "Offered Daily Wage: $wageLabel / day*",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 14.sp,
                                        color = DarkCharcoal
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Slider(
                                        value = offeredWageSlideValue,
                                        onValueChange = {
                                            offeredWageSlideValue = it
                                            onSpeak(if (isHindi) "मजदूरी दर $wageLabel रूपए प्रतिदिन" else "Wage rate $wageLabel rupees daily")
                                        },
                                        valueRange = 0f..4f,
                                        steps = 3,
                                        colors = SliderDefaults.colors(
                                            thumbColor = OrangePrimary,
                                            activeTrackColor = OrangePrimary,
                                            inactiveTrackColor = GrayBorder
                                        )
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("₹350", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                        Text("₹550", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                        Text("₹800", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                        Text("₹1050", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                        Text("₹1500", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                    }
                                }

                                // Work address Location + GPS Simulator button
                                Column {
                                    Text(
                                        text = if (isHindi) "साइट / काम का पता (Location Address)*" else "Job Site Address*",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 14.sp,
                                        color = DarkCharcoal
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    OutlinedTextField(
                                        value = regLocation,
                                        onValueChange = { regLocation = it },
                                        placeholder = { Text(if (isHindi) "जैसे: अँधेरी ईस्ट, मुम्बई" else "e.g., Andheri East, Mumbai") },
                                        leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = OrangePrimary) },
                                        modifier = Modifier.fillMaxWidth().testTag("employer_site_address"),
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = OrangePrimary,
                                            unfocusedBorderColor = Color.Gray,
                                            focusedContainerColor = SandyBg,
                                            unfocusedContainerColor = SandyBg
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    
                                    // Custom GPS Auto-detect Simulation button (Very Ola/Uber style)
                                    Button(
                                        onClick = {
                                            isFetchingLocationByGps = true
                                            onSpeak(if (isHindi) "जीपीएस द्वारा आपके काम का पता लोकेट किया जा रहा है..." else "Detecting worksite address coordinates via active GPS satellite...")
                                            // Simulate fetch
                                            regLocation = if (isHindi) "सेक्टर ३, मेट्रो पिल्लर ४२, अँधेरी ईस्ट, मुम्बई" else "Sector 3, Metro Pillar 42, Andheri East, Mumbai"
                                            Toast.makeText(context, "📍 GPS Location Auto-Captured!", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = AcceptGreen.copy(alpha = 0.08f)),
                                        border = BorderStroke(1.2.dp, AcceptGreen),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth().height(42.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.MyLocation, contentDescription = null, tint = AcceptGreen, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (isHindi) "📍 जीपीएस द्वारा पता दर्ज करें (Find GPS Address)" else "📍 Capture Site via GPS Satellites",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                color = AcceptGreen
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Register Action Button
                            Button(
                                onClick = {
                                    if (regName.trim().isEmpty()) {
                                        val mMsg = if (selectedRegisterRole == "Worker") {
                                            if (isHindi) "कृपया अपना शुभ नाम लिखें!" else "Please enter your name!"
                                        } else {
                                            if (isHindi) "कृपया मालिक का नाम लिखें!" else "Please enter employer name!"
                                        }
                                        Toast.makeText(context, mMsg, Toast.LENGTH_SHORT).show()
                                    } else if (regPhone.length < 10) {
                                        Toast.makeText(context, if (isHindi) "कृपया सही 10-अंकों का नंबर दर्ज करें!" else "Please enter correct 10 digits number!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        val preferCategory = when (selectedWorkCategoryIndex) {
                                            0 -> "मजदूर"
                                            1 -> "मिस्त्री"
                                            2 -> "बिजली मिस्त्री"
                                            else -> "प्लंबर"
                                        }

                                        if (selectedRegisterRole == "Worker") {
                                            // Save profile worker register
                                            val mappedExpectedWage = when (regWorkerWageSlider.toInt()) {
                                                0 -> 350
                                                1 -> 500
                                                2 -> 700
                                                3 -> 950
                                                else -> 1200
                                            }
                                            viewModel.updateProfile(
                                                name = regName,
                                                phone = regPhone,
                                                location = regWorkerRadius,
                                                role = preferCategory,
                                                skills = regWorkerSkillsBio.ifBlank { "सामान्य कार्य, $preferCategory काम" },
                                                experience = when(regWorkerExpYears) {
                                                    "1-2 वर्ष (Beginner)", "1-2 Years (Beginner)" -> 1
                                                    "3-5 वर्ष (Experienced)", "3-5 Years (Experienced)" -> 4
                                                    else -> 6
                                                },
                                                education = regWorkerExpYears, // Map experience status as education level description for quick overview
                                                bio = "अपेक्षित दिहाड़ी: ₹$mappedExpectedWage/दिन, दायरा: $regWorkerRadius",
                                                userRoleType = "Worker",
                                                businessName = "",
                                                offeredWage = mappedExpectedWage
                                            )
                                            Toast.makeText(context, if (isHindi) "मजदूर रजिस्ट्रेशन सफल!" else "Worker Profile Created!", Toast.LENGTH_SHORT).show()
                                            onSpeak(if (isHindi) "कामगार खाता पूर्ण हुआ! स्वागत है $regName।" else "Worker profile created successfully. Welcome $regName.")
                                        } else {
                                            // Save profile employer register
                                            val bName = regBusinessName.ifBlank { if (isHindi) "सामान्य कंस्ट्रक्शन साइट" else "General Worksite" }
                                            val loc = regLocation.ifBlank { if (isHindi) "अँधेरी ईस्ट, मुम्बई" else "Andheri East, Mumbai" }
                                            val offers = when (offeredWageSlideValue.toInt()) {
                                                0 -> 350
                                                1 -> 550
                                                2 -> 800
                                                3 -> 1050
                                                else -> 1500
                                            }

                                            viewModel.updateProfile(
                                                name = regName,
                                                phone = regPhone,
                                                location = loc,
                                                role = preferCategory, 
                                                skills = "Employer / Builder ($regEmployerProjectScale)",
                                                experience = 0,
                                                education = "Degree",
                                                bio = "परियोजना: $bName, स्तर: $regEmployerProjectScale",
                                                userRoleType = "Employer",
                                                businessName = bName,
                                                offeredWage = offers
                                            )
                                            Toast.makeText(context, if (isHindi) "मालिक रजिस्ट्रेशन सफल!" else "Employer Profile Created!", Toast.LENGTH_SHORT).show()
                                            onSpeak(if (isHindi) "मालिक खाता पूर्ण हुआ! आपका स्वागत है $regName।" else "Employer profile registered successfully! Welcome $regName.")
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary),
                                modifier = Modifier.fillMaxWidth().height(54.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = if (isHindi) "सहमत हैं और पंजीकरण करें" else "Agree & Register Profile",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 17.sp
                                )
                            }
                        }
                    }
                }
            }

            // Quick bypass card for guest demo (Tailored to let visitors instantly check BOTH roles)
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CleanWhite.copy(alpha = 0.95f)),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.5.dp, GrayBorder),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (isHindi) "⚡ बिना नंबर सीधे टेस्ट करें (Fast Demo):" else "⚡ Instant Test Bypasses:",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = OrangeClassic
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Guest Worker entrance
                            OutlinedButton(
                                onClick = {
                                    viewModel.updateProfile(
                                        name = "रमेश यादव (Ramesh Yadav)",
                                        phone = "9876543210",
                                        location = "मुम्बई (Mumbai)",
                                        role = "मिस्त्री",
                                        skills = "कारीगर, मिस्त्री",
                                        experience = 3,
                                        education = "10th Pass",
                                        bio = "साधारण मेहमान यूजर",
                                        userRoleType = "Worker"
                                    )
                                    onSpeak(if (isHindi) "मजदूर गेस्ट लॉगिन तैयार" else "Guest Seeker login selected")
                                },
                                border = BorderStroke(1.5.dp, OrangePrimary),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = OrangeClassic),
                                modifier = Modifier.weight(1f).height(46.dp),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Engineering, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isHindi) "मजदूर प्रवेश" else "Worker Skip",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            // Guest Recruiter entrance
                            OutlinedButton(
                                onClick = {
                                    viewModel.updateProfile(
                                        name = "अशोक मेहता (Ashok Mehta)",
                                        phone = "9812345678",
                                        location = "Andheri East, Mumbai",
                                        role = "मिस्त्री",
                                        skills = "Contractor",
                                        experience = 0,
                                        education = "B.Tech",
                                        bio = "मेहता बिल्डिंग बिल्डर्स",
                                        userRoleType = "Employer",
                                        businessName = "मेहता बिल्डर्स (Mehta Builders)",
                                        offeredWage = 800
                                    )
                                    onSpeak(if (isHindi) "मालिक गेटवे चालू हुआ" else "Guest Employer portal loaded")
                                },
                                border = BorderStroke(1.5.dp, AcceptGreen),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = AcceptGreen),
                                modifier = Modifier.weight(1f).height(46.dp),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Business, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isHindi) "मालिक प्रवेश" else "Employer Skip",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showMicListening) {
        Dialog(onDismissRequest = { showMicListening = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Mic, contentDescription = "Recording", tint = OrangePrimary, modifier = Modifier.size(54.dp))
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("सुन रहा हूँ... (Listening)", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = DarkCharcoal)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (activeVoiceTarget == "name") {
                            if (isHindi) "मालिक / आपका नाम बोलें..." else "Please speak owner/your name clearly..."
                        } else {
                            if (isHindi) "दुकान / कंपनी का नाम बोलें..." else "Please speak business name clearly..."
                        },
                        fontSize = 14.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            if (activeVoiceTarget == "name") {
                                regName = "रमेश यादव (Ramesh Yadav)"
                            } else {
                                regBusinessName = "यादव इलेक्ट्रॉनिक्स (Yadav Electronics)"
                            }
                            showMicListening = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary)
                    ) {
                        Text("सफल (Finish)", color = Color.White)
                    }
                }
            }
        }
    }
}

// --- NEW IMMERSIVE BILINGUAL ONBOARDING & SIGNUP SCREEN ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OldLoginSignupScreen(
    viewModel: MainViewModel,
    isHindi: Boolean,
    onLanguageToggle: () -> Unit,
    onSpeak: (String) -> Unit
) {
    val context = LocalContext.current
    var isLoginSelected by remember { mutableStateOf(false) }
    var loginPhone by remember { mutableStateOf("") }
    var loginOtp by remember { mutableStateOf("") }
    var showOtpField by remember { mutableStateOf(false) }
    var regName by remember { mutableStateOf("") }
    var regPhone by remember { mutableStateOf("") }
    var selectedWorkCategoryIndex by remember { mutableStateOf(1) }
    var showMicListening by remember { mutableStateOf(false) }
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SandyBg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Top Branding Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CleanWhite),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    border = BorderStroke(1.dp, GrayBorder),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(OrangePrimary.copy(alpha = 0.05f), Color.Transparent)
                                )
                            )
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Sound assistance button
                            IconButton(
                                onClick = {
                                    val introSpeech = if (isHindi) {
                                        "रोजगार सेतु ऐप में आपका स्वागत है। नया काम और दैनिक मजदूरी देखने के लिए अपना नया पंजीकरण करें या लॉगिन करें।"
                                    } else {
                                        "Welcome to Rozgaar Setu. Please register or login to view jobs and track your earnings."
                                    }
                                    onSpeak(introSpeech)
                                },
                                modifier = Modifier
                                    .size(46.dp)
                                    .background(OrangePrimary.copy(alpha = 0.1f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VolumeUp,
                                    contentDescription = "Speak intro",
                                    tint = OrangePrimary,
                                    modifier = Modifier.size(26.dp)
                                )
                            }

                            // Language toggle button
                            TextButton(
                                onClick = onLanguageToggle,
                                colors = ButtonDefaults.textButtonColors(contentColor = OrangePrimary)
                            ) {
                                Text(
                                    text = if (isHindi) "English" else "हिन्दी",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 16.sp,
                                    modifier = Modifier
                                        .background(OrangePrimary.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                                        .padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Logo icon
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(OrangePrimary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Engineering,
                                contentDescription = "Rozgaar Logo",
                                tint = OrangePrimary,
                                modifier = Modifier.size(44.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = if (isHindi) "रोजगार सेतु" else "Rozgaar Setu",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            color = OrangeClassic
                        )

                        Text(
                            text = if (isHindi) "काम ढूंढें • कमाई करें • आगे बढ़ें" else "Find Local Work • Quick Daily Wages",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MutedSlate,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Tabs to select Login or Register
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(ContrastLight)
                        .padding(4.dp)
                ) {
                    // Login tab button
                    Button(
                        onClick = {
                            isLoginSelected = true
                            onSpeak(if (isHindi) "लॉगिन पृष्ठ" else "Login display")
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isLoginSelected) CleanWhite else Color.Transparent,
                            contentColor = if (isLoginSelected) OrangePrimary else MutedSlate
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).height(48.dp),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = if (isLoginSelected) 2.dp else 0.dp
                        )
                    ) {
                        Text(
                            text = if (isHindi) "लॉगिन करें" else "Login",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    // Register tab button
                    Button(
                        onClick = {
                            isLoginSelected = false
                            onSpeak(if (isHindi) "नया पंजीकरण पृष्ठ" else "New Registration display")
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (!isLoginSelected) CleanWhite else Color.Transparent,
                            contentColor = if (!isLoginSelected) OrangePrimary else MutedSlate
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).height(48.dp),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = if (!isLoginSelected) 2.dp else 0.dp
                        )
                    ) {
                        Text(
                            text = if (isHindi) "पंजीकरण (New)" else "Sign Up",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }

            // Input Form Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CleanWhite),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, GrayBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (isLoginSelected) {
                            // LOGIN FORM
                            Column {
                                Text(
                                    text = if (isHindi) "मोबाइल नंबर" else "Mobile Number",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 16.sp,
                                    color = DarkCharcoal
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = loginPhone,
                                    onValueChange = { if (it.length <= 10) loginPhone = it },
                                    placeholder = { Text("9876543210") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = OrangePrimary) },
                                    modifier = Modifier.fillMaxWidth().testTag("login_phone_input"),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = OrangePrimary,
                                        unfocusedBorderColor = Color.Gray,
                                        focusedContainerColor = SandyBg,
                                        unfocusedContainerColor = SandyBg
                                    )
                                )
                            }

                            if (showOtpField) {
                                Column {
                                    Text(
                                        text = if (isHindi) "ओटीपी कोड दर्ज करें (4 अंक)" else "Enter OTP Code (4 Digits)",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 16.sp,
                                        color = DarkCharcoal
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    OutlinedTextField(
                                        value = loginOtp,
                                        onValueChange = { if (it.length <= 4) loginOtp = it },
                                        placeholder = { Text("1234") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = OrangePrimary) },
                                        modifier = Modifier.fillMaxWidth().testTag("login_otp_input"),
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = OrangePrimary,
                                            unfocusedBorderColor = Color.Gray,
                                            focusedContainerColor = SandyBg,
                                            unfocusedContainerColor = SandyBg
                                        )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Button(
                                onClick = {
                                    if (loginPhone.length < 10) {
                                        Toast.makeText(context, if (isHindi) "कृपया सही 10-अंकों का नंबर दर्ज करें!" else "Please enter a valid 10-digit number!", Toast.LENGTH_SHORT).show()
                                        onSpeak(if (isHindi) "कृपया सही फ़ोन नंबर दर्ज करें।" else "Please enter your valid phone number.")
                                    } else if (!showOtpField) {
                                        showOtpField = true
                                        onSpeak(if (isHindi) "ओटीपी भेजा गया। आपके फ़ोन पर प्राप्त हुआ ४ अंकों का कोड दर्ज करें।" else "OTP sent. Please enter the 4-digit code sent to your phone.")
                                        Toast.makeText(context, if (isHindi) "📍 ओटीपी भेज दिया गया है!" else "📍 OTP Sent Successfully!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        if (loginOtp.length < 4) {
                                            Toast.makeText(context, if (isHindi) "कृपया 4-अंकों का ओटीपी दर्ज करें!" else "Please enter a valid 4-digit OTP!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            // Successful simulated login!
                                            viewModel.updateProfile(
                                                name = "रमेश यादव",
                                                phone = loginPhone,
                                                location = "मुम्बई (Mumbai)",
                                                role = "मिस्त्री",
                                                skills = "ईंट जुड़ाई, प्लास्टर",
                                                experience = 3,
                                                education = "10th Pass",
                                                bio = "परिश्रमी निर्माण कामगार"
                                            )
                                            Toast.makeText(context, if (isHindi) "लॉगिन सफल!" else "Login Successful!", Toast.LENGTH_SHORT).show()
                                            onSpeak(if (isHindi) "आपका लॉगिन सफल रहा। काम ढूंढना शुरू करें।" else "Login successful. Let's find some work.")
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary),
                                modifier = Modifier.fillMaxWidth().height(54.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = if (!showOtpField) {
                                        if (isHindi) "ओटीपी भेजें (Get OTP)" else "Get OTP Code"
                                    } else {
                                        if (isHindi) "प्रवेश करें (Verify & Login)" else "Verify & Login"
                                    },
                                    fontWeight = FontWeight.Black,
                                    fontSize = 18.sp
                                )
                            }

                        } else {
                            // SIGNUP / REGISTER FORM
                            Column {
                                Text(
                                    text = if (isHindi) "आपका शुभ नाम" else "Full Name",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 16.sp,
                                    color = DarkCharcoal
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = regName,
                                    onValueChange = { regName = it },
                                    placeholder = { Text(if (isHindi) "जैसे: रमेश यादव" else "e.g., Ramesh Yadav") },
                                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = OrangePrimary) },
                                    trailingIcon = {
                                        IconButton(onClick = {
                                            onSpeak(if (isHindi) "कृपया अपना नाम साफ़ बोलें।" else "Please speak your name clearly.")
                                            try {
                                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (isHindi) "hi-IN" else "en-US")
                                                    putExtra(RecognizerIntent.EXTRA_PROMPT, if (isHindi) "अपना शुभ नाम बोलें..." else "Please speak your name...")
                                                }
                                                speechLauncher.launch(intent)
                                            } catch (e: Exception) {
                                                showMicListening = true
                                            }
                                        }) {
                                            Icon(Icons.Default.Mic, contentDescription = "Voice input", tint = OrangePrimary)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().testTag("register_name_input"),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = OrangePrimary,
                                        unfocusedBorderColor = Color.Gray,
                                        focusedContainerColor = SandyBg,
                                        unfocusedContainerColor = SandyBg
                                    )
                                )
                            }

                            Column {
                                Text(
                                    text = if (isHindi) "मोबाइल नंबर" else "Mobile Number",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 16.sp,
                                    color = DarkCharcoal
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = regPhone,
                                    onValueChange = { if (it.length <= 10) regPhone = it },
                                    placeholder = { Text("9876543210") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = OrangePrimary) },
                                    modifier = Modifier.fillMaxWidth().testTag("register_phone_input"),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = OrangePrimary,
                                        unfocusedBorderColor = Color.Gray,
                                        focusedContainerColor = SandyBg,
                                        unfocusedContainerColor = SandyBg
                                    )
                                )
                            }

                            // Choose Job Type / Preferred Category Choice
                            Column {
                                Text(
                                    text = if (isHindi) "काम का प्रकार (Type of Work)*" else "Preferred Work*",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 16.sp,
                                    color = DarkCharcoal
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                
                                val roles = listOf(
                                    WorkerCategoryItem(0, "मजदूर", Icons.Default.Engineering),
                                    WorkerCategoryItem(1, "मिस्त्री", Icons.Default.Construction),
                                    WorkerCategoryItem(2, "बिजली मिस्त्री", Icons.Default.FlashOn),
                                    WorkerCategoryItem(3, "प्लंबर", Icons.Default.WaterDrop)
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    roles.forEach { role ->
                                        val isSelected = selectedWorkCategoryIndex == role.idx
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .border(
                                                    width = if (isSelected) 2.dp else 1.dp,
                                                    color = if (isSelected) OrangePrimary else Color.LightGray,
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                .background(if (isSelected) OrangePrimary.copy(alpha = 0.08f) else Color.White)
                                                .clickable {
                                                    selectedWorkCategoryIndex = role.idx
                                                    val roleRead = if (isHindi) "काम: ${role.hindiLabel}" else "Work category: ${role.hindiLabel}"
                                                    onSpeak(roleRead)
                                                }
                                                .padding(6.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Icon(
                                                    imageVector = role.icon,
                                                    contentDescription = null,
                                                    tint = if (isSelected) OrangePrimary else Color.Gray,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = role.hindiLabel,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSelected) OrangeClassic else Color.Gray,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Button(
                                onClick = {
                                    if (regName.trim().isEmpty()) {
                                        Toast.makeText(context, if (isHindi) "कृपया अपना नाम लिखें!" else "Please enter your name!", Toast.LENGTH_SHORT).show()
                                    } else if (regPhone.length < 10) {
                                        Toast.makeText(context, if (isHindi) "कृपया सही 10-अंकों का मोबाइल नंबर दर्ज करें!" else "Please enter your 10-digit mobile number!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        val preferCategory = when (selectedWorkCategoryIndex) {
                                            0 -> "मजदूर"
                                            1 -> "मिस्त्री"
                                            2 -> "बिजली मिस्त्री"
                                            else -> "प्लंबर"
                                        }
                                        // Save profile register!
                                        viewModel.updateProfile(
                                            name = regName,
                                            phone = regPhone,
                                            location = "मुम्बई (Mumbai)",
                                            role = preferCategory,
                                            skills = if (isHindi) "सामान्य कार्य, $preferCategory काम" else "General work, $preferCategory skills",
                                            experience = 1,
                                            education = "10th Pass",
                                            bio = "पूर्णतः काम के लिए उपलब्ध हूँ"
                                        )
                                        Toast.makeText(context, if (isHindi) "रजिस्ट्रेशन सफल!" else "Registration Successful!", Toast.LENGTH_SHORT).show()
                                        onSpeak(if (isHindi) "पंजीकरण सफल! आपका स्वागत है $regName।" else "Registration successful. Welcome $regName.")
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary),
                                modifier = Modifier.fillMaxWidth().height(54.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = if (isHindi) "खाता बनाएं (Register)" else "Create Account",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 18.sp
                                )
                            }
                        }
                    }
                }
            }

            // Quick bypass card for guest demo
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ContrastLight),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, GrayBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (isHindi) "बिना लॉगिन तुरंत उपयोग के लिए:" else "For immediate guest test checkout:",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MutedSlate
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                viewModel.updateProfile(
                                    name = "रमेश यादव",
                                    phone = "9876543210",
                                    location = "मुम्बई (Mumbai)",
                                    role = "मिस्त्री",
                                    skills = "कारीगर, मिस्त्री",
                                    experience = 3,
                                    education = "10th Pass",
                                    bio = "साधारण मेहमान यूजर"
                                )
                                onSpeak(if (isHindi) "गेस्ट लॉगिन चालू हुआ" else "Guest entrance ready")
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = OrangeClassic),
                            border = BorderStroke(1.5.dp, OrangePrimary),
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isHindi) "बिना लॉगिन सीधे आगे बढ़ें" else "Skip & Enter as Guest",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showMicListening) {
        Dialog(onDismissRequest = { showMicListening = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Mic, contentDescription = "Recording", tint = OrangePrimary, modifier = Modifier.size(54.dp))
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("सुन रहा हूँ... (Listening)", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = DarkCharcoal)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("कृपया अपना शुभ नाम साफ़ बोलें", fontSize = 14.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            regName = "रमेश यादव" // Auto simulated voice match success
                            showMicListening = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary)
                    ) {
                        Text("सफल (Finish)", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun IncomingJobRequestScreen(
    job: MockJob,
    isHindi: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onAnotherPersonAccepted: () -> Unit,
    onSpeak: (String) -> Unit
) {
    var secondsLeft by remember { mutableStateOf(15) }

    LaunchedEffect(job) {
        val message = if (isHindi) {
            "सावधान! नया काम आ गया है! २००० रूपये दिहाड़ी! तुरंत स्वीकार करें!"
        } else {
            "Siren alert! High-paying nearby job request. Emergency contractor signal broadcasted. Accept immediately."
        }
        onSpeak(message)
    }

    LaunchedEffect(secondsLeft) {
        if (secondsLeft > 0) {
            kotlinx.coroutines.delay(1000L)
            secondsLeft--
        } else {
            onAnotherPersonAccepted()
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_scale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_alpha"
    )

    // Intense rapid crimson strobe color animation to give a prominent warning alarm feel
    val sirenGlowColor by infiniteTransition.animateColor(
        initialValue = Color(0xFF1A0000),
        targetValue = Color(0xFF660000),
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "siren_color"
    )

    // Equalizer bouncing animations (4 channels)
    val eqHeight1 by infiniteTransition.animateFloat(initialValue = 10f, targetValue = 40f, animationSpec = infiniteRepeatable(tween(300, easing = LinearOutSlowInEasing), RepeatMode.Reverse), label = "eq1")
    val eqHeight2 by infiniteTransition.animateFloat(initialValue = 50f, targetValue = 15f, animationSpec = infiniteRepeatable(tween(250, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "eq2")
    val eqHeight3 by infiniteTransition.animateFloat(initialValue = 20f, targetValue = 60f, animationSpec = infiniteRepeatable(tween(350, easing = LinearEasing), RepeatMode.Reverse), label = "eq3")
    val eqHeight4 by infiniteTransition.animateFloat(initialValue = 40f, targetValue = 20f, animationSpec = infiniteRepeatable(tween(200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "eq4")

    Dialog(
        onDismissRequest = {}, // Cannot dismiss by clicking outside or back to keep full-screen overlay active
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(sirenGlowColor)
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // High Impact Warning Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Red.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        .border(2.dp, Color.Red, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Campaign,
                        contentDescription = "Alarm",
                        tint = Color.Red,
                        modifier = Modifier.size(36.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isHindi) "🚨 आपातकालीन नौकरी अलर्ट! 🚨" else "🚨 HIGH-URGENCY ALARM DETECTED 🚨",
                            color = Color.Red,
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = if (isHindi) "डबल पेमेंट गारंटी • तुरंत स्वीकार्य" else "Double wage guarantee • Radar Signal Active",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }

                // Pulsating Radar Ring Representing Nearby Client Search
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .drawBehind {
                            drawCircle(
                                color = Color.Red.copy(alpha = pulseAlpha),
                                radius = (size.minDimension / 2f) * pulseScale,
                                style = Stroke(width = 6.dp.toPx())
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(Color.Red),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }

                // Visual Bouncing Audio Waves (Equalizer) showing high acoustic alert
                Row(
                    modifier = Modifier
                        .height(60.dp)
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Box(modifier = Modifier.width(6.dp).height(eqHeight1.dp).background(Color.Red, RoundedCornerShape(3.dp)))
                    Box(modifier = Modifier.width(6.dp).height(eqHeight2.dp).background(Color.Yellow, RoundedCornerShape(3.dp)))
                    Box(modifier = Modifier.width(6.dp).height(eqHeight3.dp).background(Color.Red, RoundedCornerShape(3.dp)))
                    Box(modifier = Modifier.width(6.dp).height(eqHeight4.dp).background(Color.Yellow, RoundedCornerShape(3.dp)))
                    Box(modifier = Modifier.width(6.dp).height(eqHeight2.dp).background(Color.Red, RoundedCornerShape(3.dp)))
                    Box(modifier = Modifier.width(6.dp).height(eqHeight1.dp).background(Color.Yellow, RoundedCornerShape(3.dp)))
                }

                Text(
                    text = if (isHindi) "⚡ रडार संकेत: नया तत्काल काम! ⚡" else "⚡ BROADCAST SIGNAL CHANNELS ACTIVE ⚡",
                    color = Color.Yellow,
                    fontWeight = FontWeight.Black,
                    fontSize = 17.sp,
                    letterSpacing = 1.sp,
                    textAlign = TextAlign.Center
                )

                // Timer badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(DeclineRed.copy(alpha = 0.2f))
                        .border(1.5.dp, DeclineRed, RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Schedule, contentDescription = null, tint = Color.Red, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isHindi) "कोई और स्वीकार कर लेगा: ${secondsLeft}s" else "Accept before another: ${secondsLeft}s",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // High Contrast Job Details Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(3.dp, OrangePrimary)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (isHindi) job.hindiTitle else job.title,
                            fontWeight = FontWeight.Black,
                            fontSize = 22.sp,
                            color = DarkCharcoal,
                            textAlign = TextAlign.Center,
                            lineHeight = 28.sp
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))

                        // Contractor / Employer Name
                        Text(
                            text = if (isHindi) "👤 ग्राहक: ${job.hindiCompany}" else "👤 Client: ${job.company}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MutedSlate
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        HorizontalDivider(color = GrayBorder)

                        Spacer(modifier = Modifier.height(12.dp))

                        // Wage Display (Giant Highlight)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(AcceptGreen.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                                .padding(12.dp)
                        ) {
                            Icon(Icons.Default.CurrencyRupee, contentDescription = null, tint = AcceptGreen, modifier = Modifier.size(24.dp))
                            Text(
                                text = if (isHindi) "${job.hindiSalary}" else "${job.salary}",
                                fontWeight = FontWeight.Black,
                                fontSize = 24.sp,
                                color = AcceptGreen
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Proximity Highlight
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Location Badge
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.DirectionsWalk, contentDescription = null, tint = OrangePrimary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isHindi) "दूरी: ${job.hindiDistance}" else "Distance: ${job.distance}",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 14.sp,
                                    color = DarkCharcoal
                                )
                            }

                            // Day Count
                            Text(
                                text = if (isHindi) "⏱️ अवधि: ${job.tenureDays} दिन" else "⏱️ Tenure: ${job.tenureDays} Days",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = DarkCharcoal
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Workplace Address
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SandyBg, RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = if (isHindi) "📍 पता: ${job.hindiLocation}" else "📍 Address: ${job.location}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MutedSlate,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Giant Accept & Decline buttons matching Uber / Ola UI
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Decline
                    Button(
                        onClick = onDecline,
                        colors = ButtonDefaults.buttonColors(containerColor = DeclineRed),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isHindi) "मना करें" else "Decline",
                                fontWeight = FontWeight.Black,
                                fontSize = 17.sp,
                                color = Color.White
                            )
                        }
                    }

                    // Accept
                    Button(
                        onClick = onAccept,
                        colors = ButtonDefaults.buttonColors(containerColor = AcceptGreen),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1.3f)
                            .height(64.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isHindi) "स्वीकार करें" else "Accept Job",
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

// Global real turn-by-turn navigation helper linked to the user's GPS maps (like Ola/Uber)
fun launchGoogleMapsNavigation(context: android.content.Context, address: String) {
    try {
        val gmmIntentUri = Uri.parse("google.navigation:q=${Uri.encode(address)}")
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
            setPackage("com.google.android.apps.maps")
        }
        context.startActivity(mapIntent)
    } catch (e: Exception) {
        try {
            val fallbackUri = Uri.parse("geo:0,0?q=${Uri.encode(address)}")
            val fallbackIntent = Intent(Intent.ACTION_VIEW, fallbackUri)
            context.startActivity(fallbackIntent)
        } catch (e2: Exception) {
            Toast.makeText(context, "लोकेशन नेविगेट करने के लिए कोई ऐप नहीं मिला", Toast.LENGTH_LONG).show()
        }
    }
}

// ==========================================
// --- WORKER MODEL & SIMULATION SYSTEM ---
// ==========================================
data class NearbyWorker(
    val id: String,
    val name: String,
    val nameHindi: String,
    val role: String,
    val roleHindi: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val rating: Double,
    val distanceMeters: Int,
    val durationMinutes: Int,
    val dailyRate: Int,
    val phone: String,
    val locationName: String,
    val locationNameHindi: String,
    val mapPercentX: Float,
    val mapPercentY: Float
)

// ==========================================
// --- TAB SCREEN: EMPLOYER RADAR (OLA/UBER MATCHING) ---
// ==========================================
@Composable
fun EmployerRadarTabScreen(
    viewModel: MainViewModel,
    isHindi: Boolean,
    onSpeak: (String) -> Unit
) {
    val context = LocalContext.current
    var selectedCategory by remember { mutableStateOf("मिस्त्री") }
    var selectedWorkerId by remember { mutableStateOf("w1") }
    var voicePromptActive by remember { mutableStateOf(false) }
    var ringWorkerName by remember { mutableStateOf("") }
    var isRinging by remember { mutableStateOf(false) }
    var showSuccessHirePopup by remember { mutableStateOf(false) }
    var hiredWorkerName by remember { mutableStateOf("") }

    // Simulating rich available workers
    val workers = remember {
        listOf(
            NearbyWorker(
                id = "w1",
                name = "Ramesh Yadav",
                nameHindi = "रमेश यादव",
                role = "मिस्त्री",
                roleHindi = "राजमिस्त्री (Mason)",
                icon = Icons.Default.Construction,
                rating = 4.9,
                distanceMeters = 220,
                durationMinutes = 4,
                dailyRate = 600,
                phone = "9876543210",
                locationName = "Goregaon East, Mumbai",
                locationNameHindi = "गोरेगांव पूर्व, मुंबई",
                mapPercentX = 0.35f,
                mapPercentY = 0.42f
            ),
            NearbyWorker(
                id = "w2",
                name = "Dinesh Kumar",
                nameHindi = "दिनेश कुमार",
                role = "मजदूर",
                roleHindi = "मजदूर (Helper / Beldar)",
                icon = Icons.Default.Engineering,
                rating = 4.8,
                distanceMeters = 380,
                durationMinutes = 7,
                dailyRate = 450,
                phone = "9546738291",
                locationName = "Dindoshi, Mumbai",
                locationNameHindi = "दिंडोशी, मुंबई",
                mapPercentX = 0.65f,
                mapPercentY = 0.28f
            ),
            NearbyWorker(
                id = "w3",
                name = "Suneta Devi",
                nameHindi = "सुनीता देवी",
                role = "मजदूर",
                roleHindi = "मजदूर (Helper / Beldar)",
                icon = Icons.Default.Engineering,
                rating = 4.7,
                distanceMeters = 550,
                durationMinutes = 9,
                dailyRate = 450,
                phone = "9746352819",
                locationName = "Sanjay Gandhi National Park Area",
                locationNameHindi = "संजय गांधी नेशनल पार्क क्षेत्र",
                mapPercentX = 0.52f,
                mapPercentY = 0.61f
            ),
            NearbyWorker(
                id = "w4",
                name = "Pappu Sharma",
                nameHindi = "पप्पू शर्मा",
                role = "बिजली मिस्त्री",
                roleHindi = "बिजली मिस्त्री (Electrician)",
                icon = Icons.Default.FlashOn,
                rating = 5.0,
                distanceMeters = 720,
                durationMinutes = 12,
                dailyRate = 700,
                phone = "9634521789",
                locationName = "Malad East Bus Depot",
                locationNameHindi = "मालाड पूर्व बस डिपो",
                mapPercentX = 0.22f,
                mapPercentY = 0.72f
            ),
            NearbyWorker(
                id = "w5",
                name = "Suresh Prasad",
                nameHindi = "सुरेश प्रसाद",
                role = "प्लंबर",
                roleHindi = "प्लंबर (Plumber)",
                icon = Icons.Default.WaterDrop,
                rating = 4.5,
                distanceMeters = 900,
                durationMinutes = 15,
                dailyRate = 650,
                phone = "9012345678",
                locationName = "Kurar Village Metro Gate",
                locationNameHindi = "कुरार विलेज मेट्रो गेट",
                mapPercentX = 0.48f,
                mapPercentY = 0.15f
            )
        )
    }

    // Filtered matches
    val filteredWorkers = workers.filter { it.role == selectedCategory }
    val activeSelection = filteredWorkers.firstOrNull { it.id == selectedWorkerId } ?: filteredWorkers.firstOrNull()

    // Breathing pulse for Ola/Uber radar wave look
    val infiniteTransition = rememberInfiniteTransition()
    val radarPulse1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )
    val radarPulse2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
            initialStartOffset = StartOffset(1250)
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SandyBg)
    ) {
        // --- 1. CATEGORY CHIPS OVERLAY ROW ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CleanWhite)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val categorLabels = listOf(
                Pair("मिस्त्री", "मिस्त्री (Mason)"),
                Pair("मजदूर", "मजदूर (Labor)"),
                Pair("बिजली मिस्त्री", "बिजली (Electrician)"),
                Pair("प्लंबर", "प्लंबर (Plumber)")
            )

            categorLabels.forEach { (roleCode, label) ->
                val isSelected = selectedCategory == roleCode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) OrangePrimary else SandyBg)
                        .border(1.dp, if (isSelected) OrangeClassic else Color.LightGray, RoundedCornerShape(8.dp))
                        .clickable {
                            selectedCategory = roleCode
                            // Auto select first of new category
                            val matched = workers.filter { it.role == roleCode }
                            if (matched.isNotEmpty()) {
                                selectedWorkerId = matched.first().id
                            }
                            val speakText = if (isHindi) {
                                "आसपास के $roleCode रडार खोज रहे हैं।"
                            } else {
                                "Finding nearby $roleCode"
                            }
                            onSpeak(speakText)
                        }
                        .padding(vertical = 8.dp, horizontal = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isHindi) label.substringBefore(" (") else label.substringAfter("(").substringBefore(")"),
                        color = if (isSelected) Color.White else DarkCharcoal,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        maxLines = 1,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // --- 2. OLA/UBER STYLE RADAR INTERACTIVE MAP ---
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable {
                        onSpeak(
                            if (isHindi) "कृपया उपलब्ध कामगार के पिन को छूकर विवरण जांचें"
                            else "Tap on a worker's pin to inspect their nearby telemetry rating"
                        )
                    }
            ) {
                // Ground Map Slate
                drawRect(color = Color(0xFFF1EDE6))
                
                val width = size.width
                val height = size.height

                // Draw Local Street Grids (Ola Uber Theme Style)
                val roadColor = Color(0xFFE3DEC1)
                drawLine(color = roadColor, start = Offset(0f, height * 0.25f), end = Offset(width, height * 0.25f), strokeWidth = 22.dp.toPx())
                drawLine(color = roadColor, start = Offset(0f, height * 0.6f), end = Offset(width, height * 0.65f), strokeWidth = 30.dp.toPx())
                drawLine(color = roadColor, start = Offset(width * 0.3f, 0f), end = Offset(width * 0.35f, height), strokeWidth = 26.dp.toPx())
                drawLine(color = roadColor, start = Offset(width * 0.75f, 0f), end = Offset(width * 0.7f, height), strokeWidth = 20.dp.toPx())

                // Draw Real-time Animated Radar Concentric Circles originating from Employer Center Hub
                val centerHubX = width * 0.5f
                val centerHubY = height * 0.5f

                // Pulse 1
                drawCircle(
                    color = OrangePrimary.copy(alpha = 0.15f * (1f - radarPulse1)),
                    center = Offset(centerHubX, centerHubY),
                    radius = radarPulse1 * 180.dp.toPx()
                )
                // Pulse 2
                drawCircle(
                    color = OrangePrimary.copy(alpha = 0.15f * (1f - radarPulse2)),
                    center = Offset(centerHubX, centerHubY),
                    radius = radarPulse2 * 180.dp.toPx()
                )

                // Employer Center Pin Dot
                drawCircle(color = OrangePrimary.copy(alpha = 0.2f), center = Offset(centerHubX, centerHubY), radius = 30.dp.toPx())
                drawCircle(color = OrangeClassic, center = Offset(centerHubX, centerHubY), radius = 10.dp.toPx())
                drawCircle(color = Color.White, center = Offset(centerHubX, centerHubY), radius = 4.dp.toPx())

                // Draw dash paths from Employer to currently active selected worker
                activeSelection?.let { wk ->
                    val destX = width * wk.mapPercentX
                    val destY = height * wk.mapPercentY
                    
                    drawLine(
                        color = Color(0xFF2E7D32),
                        start = Offset(centerHubX, centerHubY),
                        end = Offset(destX, destY),
                        strokeWidth = 3.dp.toPx(),
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
                    )
                }
            }

            // High Visibility Speak guidance help bubble
            Card(
                colors = CardDefaults.cardColors(containerColor = OrangePrimary),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .padding(12.dp)
                    .align(Alignment.TopEnd)
                    .clickable {
                        onSpeak(
                            if (isHindi) "यह एक रीयल-टाइम रडार मैप है। जो कामगार आपके सबसे करीब है, उसका मार्ग दिखाया गया है।"
                            else "This is an active live GPS radar. The closest candidate matches are glowing in real-time."
                        )
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.VolumeUp, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = if (isHindi) "विवरण सुनें (Listen Help)" else "How it works", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            // Render Worker Floating Radar Pins dynamically on top of the Canvas
            filteredWorkers.forEach { wk ->
                val isSelected = wk.id == selectedWorkerId
                
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(
                            x = (wk.mapPercentX * 0.9f).dp * 260f / 100f,
                            y = (wk.mapPercentY * 0.9f).dp * 400f / 100f
                        )
                        .clickable {
                            selectedWorkerId = wk.id
                            val statusInfo = if (isHindi) {
                                "${wk.nameHindi} केवल ${wk.distanceMeters} मीटर दूर है। रेटिंग ${wk.rating} स्टार।"
                            } else {
                                "${wk.name} is ${wk.distanceMeters} meters away with ${wk.rating} stars."
                            }
                            onSpeak(statusInfo)
                        }
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.wrapContentSize()
                    ) {
                        // Glowing pin avatar
                        Box(
                            modifier = Modifier
                                .size(if (isSelected) 40.dp else 32.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) Color(0xFF2E7D32) else OrangePrimary)
                                .border(2.dp, Color.White, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = wk.icon,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(if (isSelected) 22.dp else 16.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(2.dp))
                        
                        // Small label
                        Card(
                            colors = CardDefaults.cardColors(containerColor = if (isSelected) Color(0xFF2E7D32) else DarkCharcoal),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = if (isHindi) wk.nameHindi else wk.name,
                                color = Color.White,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }

        // --- 3. EXPANDABLE BOTTOM WORKER DETAIL PANELS (OLA/UBER CALL-TO-ACTIONS) ---
        activeSelection?.let { wk ->
            Card(
                colors = CardDefaults.cardColors(containerColor = CleanWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                border = BorderStroke(1.dp, GrayBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp)
                ) {
                    // Header title & distance tags
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (isHindi) wk.nameHindi else wk.name,
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                color = DarkCharcoal
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.LocationOn, contentDescription = null, tint = OrangePrimary, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = if (isHindi) "${wk.locationNameHindi} (${wk.distanceMeters} मीटर दूर)" else "${wk.locationName} (${wk.distanceMeters}m away)",
                                    fontSize = 12.sp,
                                    color = MutedSlate,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        
                        // Rating Chip
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFE8F5E9))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Star, contentDescription = "Rating", tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = wk.rating.toString(), fontWeight = FontWeight.Black, color = Color(0xFF2E7D32), fontSize = 13.sp)
                        }
                    }

                    Divider(modifier = Modifier.padding(vertical = 10.dp), color = GrayBorder)

                    // Job category details & daily offered wage metrics
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Engineering, tint = OrangePrimary, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isHindi) "काम: ${wk.roleHindi}" else "Role: ${wk.role}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = DarkCharcoal
                            )
                        }

                        Text(
                            text = if (isHindi) "₹${wk.dailyRate}/दिन" else "₹${wk.dailyRate}/Day",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp,
                            color = OrangeClassic
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // OLA/UBER style direct booking & call panels
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Action 1: Call instantly (कॉल करें)
                        Button(
                            onClick = {
                                ringWorkerName = if (isHindi) wk.nameHindi else wk.name
                                isRinging = true
                                onSpeak(
                                    if (isHindi) "कामगार ${wk.nameHindi} को कॉल लग रही है। कृपया प्रतीक्षा करें..."
                                    else "Initiating secure telecom dial to ${wk.name}. Kindly wait."
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Phone, contentDescription = "Call", tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = if (isHindi) "कॉल करें" else "Call Now", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }

                        // Action 2: Direct Hire (काम पर रखें)
                        Button(
                            onClick = {
                                hiredWorkerName = if (isHindi) wk.nameHindi else wk.name
                                showSuccessHirePopup = true
                                val responseText = if (isHindi) {
                                    "${wk.nameHindi} को काम की पेशकश दी गयी। वे ${wk.durationMinutes} मिनट में आ रहे हैं।"
                                } else {
                                    "Hired ${wk.name}! They will arrive in ${wk.durationMinutes} minutes."
                                }
                                onSpeak(responseText)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1.2f)
                                .height(46.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Check, contentDescription = "Hire", tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isHindi) "काम पर रखें (Hire)" else "Hire Instantly",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 12.sp,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // --- DIALOG: LIVE TELECOMMUNICATION RINGING OVERLAY ---
    if (isRinging) {
        Dialog(onDismissRequest = { isRinging = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0D47A1)),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.5.dp, Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = "Calling",
                        tint = Color.White,
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f))
                            .padding(12.dp)
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = if (isHindi) "सुरक्षित टेलीकॉम कॉल चालू..." else "Connecting Secure Call...",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = ringWorkerName,
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (isHindi) "वॉयस मास्क सुविधा द्वारा आपका वास्तविक मोबाइल नंबर सुरक्षित है।" else "Your actual mobile number is fully masked for privacy.",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { isRinging = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Text(text = if (isHindi) "कॉल काटें (Cut Call)" else "Disconnect", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // --- DIALOG: SUCCESSFUL HIRE POPUP ---
    if (showSuccessHirePopup) {
        Dialog(onDismissRequest = { showSuccessHirePopup = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CleanWhite),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(2.dp, Color(0xFF2E7D32)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE8F5E9)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Success", tint = Color(0xFF2E7D32), modifier = Modifier.size(32.dp))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (isHindi) "काम की पुष्टि सफल!" else "Booking Confirmed!",
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp,
                        color = Color(0xFF2E7D32)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isHindi) "$hiredWorkerName ने आपका काम स्वीकार कर लिया है और साइड की ओर प्रस्थान कर रहे हैं!" else "$hiredWorkerName accepted your offer and is now heading to your worksite location!",
                        fontSize = 14.sp,
                        color = DarkCharcoal,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isHindi) "कृपया 'My Jobs' में लाइव रूट ट्रैक करें।" else "Please monitor their active GPS path under settings/My Jobs.",
                        fontSize = 12.sp,
                        color = MutedSlate,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Button(
                        onClick = { showSuccessHirePopup = false },
                        colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = if (isHindi) "ठीक है (OK)" else "Got it", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ==========================================
// --- TAB SCREEN: EMPLOYER JOBS (TRACK ACTIVE) ---
// ==========================================
@Composable
fun EmployerJobsTabScreen(
    viewModel: MainViewModel,
    isHindi: Boolean,
    onSpeak: (String) -> Unit
) {
    val context = LocalContext.current
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    var showPostJobDialog by remember { mutableStateOf(false) }
    var showPostSuccessDialog by remember { mutableStateOf(false) }

    // Dialog state bindings
    var category by remember { mutableStateOf("मिस्त्री") }
    var businessName by remember { mutableStateOf(userProfile.businessName) }
    var offeredWage by remember { mutableStateOf(if (userProfile.offeredWage > 0) userProfile.offeredWage else 600) }
    var customTitle by remember { mutableStateOf("") }
    var customTitleHindi by remember { mutableStateOf("") }
    var customLocation by remember { mutableStateOf(userProfile.location.ifEmpty { "Goregaon East, Mumbai" }) }
    var customLocationHindi by remember { mutableStateOf(if (isHindi) "गोरेगांव पूर्व, मुंबई" else "Goregaon East, Mumbai") }
    var tenureDays by remember { mutableStateOf(3) }
    var customDescription by remember { mutableStateOf("") }
    var customDescriptionHindi by remember { mutableStateOf("") }
    var customRequirements by remember { mutableStateOf("") }
    var customRequirementsHindi by remember { mutableStateOf("") }

    // Generate responsive default job template details instantly depending on selected category to load fast
    LaunchedEffect(category) {
        when (category) {
            "मजदूर" -> {
                customTitle = "Material Helper & Shifting"
                customTitleHindi = "मजदूर (सामग्री उठाना व लोडिंग)"
                customDescription = "Need helper beldar for loading construction bricks, material shifting, cement mixing at worksite."
                customDescriptionHindi = "साइट पर ईंटें उठाने, सीमेंट मिलाने, खुदाई और लोडिंग-अनलोडिंग के लिए सहायक बेलदार मजदूर की जरुरत है।"
                customRequirements = "Physically strong, must have own safety boots and helmet."
                customRequirementsHindi = "शारीरिक रूप से स्वस्थ, सुरक्षा जूते व हेलमेट होना चाहिए।"
            }
            "बिजली मिस्त्री" -> {
                customTitle = "House Wiring Electrician"
                customTitleHindi = "बिजली मिस्त्री (हाउस वायरिंग व फिटिंग)"
                customDescription = "Need skilled electrician for wall pipe cutting, switchboard installation, and master building wiring."
                customDescriptionHindi = "घर की नयी दीवार पाइपिंग, सॉकेट फिटिंग, मुख्य बोर्ड वायरिंग और लाइट कनेक्शन के लिए अनुभवी बिजली फिटिंग मिस्त्री।"
                customRequirements = "Must have wiring tools and tester. Min 2 years experience."
                customRequirementsHindi = "वायरिंग औज़ार और टेस्टर होना अनिवार्य है। कम से कम २ वर्ष का अनुभव।"
            }
            "प्लंबर" -> {
                customTitle = "Pipeline Plumbing Fitter"
                customTitleHindi = "प्लंबर (पाइपलाइन व नल फिटिंग)"
                customDescription = "Urgent plumbing work for bathroom sanitary fitting, water tank connections, and pipeline leakage fix."
                customDescriptionHindi = "बाथरूम सेनेटरी फिटिंग, ताज़ा पानी की टंकी कनेक्शन और लीकेज पाइप वेल्डिंग सुधार के लिए योग्य प्लंबर।"
                customRequirements = "Must understand PPR/PVC pipe threading and jointing."
                customRequirementsHindi = "PPR/PVC पाइप चूड़ी काटने और जोड़ने का काम पता होना चाहिए।"
            }
            else -> { // "मिस्त्री"
                customTitle = "Mason Bricklaying & Plastering"
                customTitleHindi = "राजमिस्त्री (दीवार निर्माण व प्लास्टर)"
                customDescription = "Need expert building mason (Rajmistry) for bricklaying, cement plastering, and floor tile installation."
                customDescriptionHindi = "मकान निर्माण, ईंट की जुड़ाई, सीमेंट प्लास्टर सुधार और फर्श टाइल्स लगाने के लिए अनुभवी मुख्य मिस्त्री की आवश्यकता है।"
                customRequirements = "Precision wall alignment. Speed & alignment tool required."
                customRequirementsHindi = "दीवार की सही सीधी जुड़ाई। अपना साहुल (plumb bob) साथ लाएं।"
            }
        }
    }

    var progressStep by remember { mutableStateOf(2) } // Simulating "On the way"
    var mapMovementOffset by remember { mutableStateOf(0.0f) }

    // Simulating real telemetry movement steps inside active trackers
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(6000)
            if (mapMovementOffset < 1.0f) {
                mapMovementOffset += 0.15f
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SandyBg)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // High Visibility Post Job Action Button Card Item
        item {
            Card(
                onClick = {
                    showPostJobDialog = true
                    onSpeak(if (isHindi) "नया काम पोस्ट करने का फॉर्म खुल गया है।" else "New work registration form opened.")
                },
                colors = CardDefaults.cardColors(containerColor = CleanWhite),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(2.dp, OrangePrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("post_job_card_trigger")
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isHindi) "📝 नया काम पोस्ट करें" else "📝 Post New Work",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp,
                            color = OrangeClassic
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isHindi) "नजदीकी कामगारों के फ़ोन पर हाई-अलार्म बजाकर बुलाएं" else "Alert nearest workers instantly with high-volume siren!",
                            fontSize = 12.sp,
                            color = MutedSlate,
                            lineHeight = 16.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(OrangePrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Post",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        // Tracker Summary Card (Ola/Uber inspired status progress)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CleanWhite),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, GrayBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Construction,
                                contentDescription = null,
                                tint = OrangePrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isHindi) "सक्रिय कार्य (Active Work Location)" else "Active Bookings",
                                fontWeight = FontWeight.Black,
                                fontSize = 15.sp,
                                color = DarkCharcoal
                            )
                        }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = OrangePrimary.copy(alpha = 0.1f)),
                            border = BorderStroke(1.dp, OrangePrimary)
                        ) {
                            Text(
                                text = if (isHindi) "चल रहा है (LIVE)" else "LIVE TRACKING",
                                color = OrangeClassic,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Simulated Map Route tracking panel
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(8.dp))
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawRect(color = Color(0xFFECE5D8))
                            val w = size.width
                            val h = size.height

                            // Draw organic road segment
                            drawLine(color = Color.White, start = Offset(0f, h * 0.5f), end = Offset(w, h * 0.5f), strokeWidth = 16.dp.toPx())
                            
                            // Employer house pin (Destination)
                            drawCircle(color = OrangePrimary, center = Offset(w * 0.8f, h * 0.5f), radius = 10.dp.toPx())
                            drawCircle(color = Color.White, center = Offset(w * 0.8f, h * 0.5f), radius = 3.dp.toPx())

                            // Moving Worker Pin
                            val currentWorkerX = w * (0.15f + mapMovementOffset * 0.65f)
                            drawCircle(color = Color(0xFF1B5E20).copy(alpha = 0.2f), center = Offset(currentWorkerX, h * 0.5f), radius = 20.dp.toPx())
                            drawCircle(color = Color(0xFF2E7D32), center = Offset(currentWorkerX, h * 0.5f), radius = 8.dp.toPx())
                        }

                        // Floating ETA Tag
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1B5E20)),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier
                                .padding(8.dp)
                                .align(Alignment.BottomStart)
                        ) {
                            Text(
                                text = if (isHindi) "रमेश: ${if (mapMovementOffset >= 0.9f) "पहुंच गए" else "3 मिनट दूर"}"
                                       else "Ramesh: ${if (mapMovementOffset >= 0.9f) "Arrived" else "3 min away"}",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Step Tracker Timeline
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val steps = listOf(
                            Pair("स्वीकृत", "Accepted"),
                            Pair("रास्ते में", "On Way"),
                            Pair("साइट पर", "Arrived"),
                            Pair("कार्य चालू", "Started")
                        )

                        steps.forEachIndexed { index, (hindi, eng) ->
                            val isActive = progressStep >= index
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(if (isActive) Color(0xFF2E7D32) else Color.LightGray),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isActive) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (isHindi) hindi else eng,
                                    fontSize = 10.sp,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isActive) Color(0xFF2E7D32) else Color.Gray
                                )
                            }
                        }
                    }
                }
            }
        }

        // Job details details info card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CleanWhite),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, GrayBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isHindi) "साइट सम्बन्धी जानकारियाँ" else "Worksite & Crew Profile",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = DarkCharcoal
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = if (isHindi) "साइट का काम:" else "Work Category:", fontSize = 13.sp, color = MutedSlate)
                        Text(text = if (isHindi) "राजमिस्त्री (Mason Building)" else "Mason Craft", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = DarkCharcoal)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = if (isHindi) "दिहाड़ी रेट:" else "Offered Wage:", fontSize = 13.sp, color = MutedSlate)
                        Text(text = "₹600 / दिन", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = OrangeClassic)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = if (isHindi) "काले इलेक्ट्रिकल:" else "Business:", fontSize = 13.sp, color = MutedSlate)
                        Text(text = "यादव कंस्ट्रक्शन", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = DarkCharcoal)
                    }
                }
            }
        }
    }

    // --- FORM DIALOG: ADD/POST NEW CUSTOM WORK SIGNAL CHANNEL ---
    if (showPostJobDialog) {
        Dialog(onDismissRequest = { showPostJobDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CleanWhite),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.5.dp, OrangePrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(vertical = 10.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isHindi) "📋 नया काम दर्ज करें" else "📋 Post Job Details",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp,
                                color = OrangeClassic
                            )
                            IconButton(onClick = { showPostJobDialog = false }) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                            }
                        }
                        Divider(color = GrayBorder)
                    }

                    // 1. Category selector
                    item {
                        Text(
                            text = if (isHindi) "काम किस श्रेणी का है? (Category):" else "Select Work Category:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MutedSlate
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val categories = listOf("मजदूर", "मिस्त्री", "बिजली मिस्त्री", "प्लंबर")
                            categories.forEach { cat ->
                                val isSel = category == cat
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSel) OrangePrimary else ContrastLight)
                                        .border(1.dp, if (isSel) OrangeClassic else Color.LightGray, RoundedCornerShape(8.dp))
                                        .clickable { category = cat }
                                        .padding(vertical = 8.dp, horizontal = 2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (isHindi) cat else when(cat) {
                                            "मजदूर" -> "Labor"
                                            "मिस्त्री" -> "Mason"
                                            "बिजली मिस्त्री" -> "Electric"
                                            "प्लंबर" -> "Plumbing"
                                            else -> cat
                                        },
                                        color = if (isSel) Color.White else DarkCharcoal,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    // 2. Business Name
                    item {
                        Text(
                            text = if (isHindi) "कंपनी / मालिक का नाम (Company/Owner):" else "Company / Proprietor Name:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MutedSlate
                        )
                        OutlinedTextField(
                            value = businessName,
                            onValueChange = { businessName = it },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = OrangePrimary,
                                unfocusedContainerColor = SandyBg,
                                focusedContainerColor = SandyBg
                            )
                        )
                    }

                    // 3. Wage Slider
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (isHindi) "प्रस्तावित दैनिक दिहाड़ी:" else "Offered Daily Wage:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MutedSlate
                            )
                            Text(
                                text = "₹$offeredWage / दिन",
                                fontWeight = FontWeight.Black,
                                color = OrangeClassic,
                                fontSize = 14.sp
                            )
                        }
                        Slider(
                            value = offeredWage.toFloat(),
                            onValueChange = { offeredWage = it.toInt() },
                            valueRange = 300f..1200f,
                            steps = 18,
                            colors = SliderDefaults.colors(
                                thumbColor = OrangePrimary,
                                activeTrackColor = OrangeClassic
                            )
                        )
                    }

                    // 4. Job Title
                    item {
                        Text(
                            text = if (isHindi) "काम का मुख्य नाम (Job Title):" else "Job Title (Custom):",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MutedSlate
                        )
                        OutlinedTextField(
                            value = customTitleHindi,
                            onValueChange = { customTitleHindi = it; customTitle = it },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = OrangePrimary,
                                unfocusedContainerColor = SandyBg,
                                focusedContainerColor = SandyBg
                            )
                        )
                    }

                    // 5. Worksite Location
                    item {
                        Text(
                            text = if (isHindi) "काम का क्षेत्र / पता (Location):" else "Worksite Location:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MutedSlate
                        )
                        OutlinedTextField(
                            value = customLocationHindi,
                            onValueChange = { customLocationHindi = it; customLocation = it },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = OrangePrimary,
                                unfocusedContainerColor = SandyBg,
                                focusedContainerColor = SandyBg
                            )
                        )
                    }

                    // 6. Work Duration
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (isHindi) "काम की कुल अवधि:" else "Work Tenure:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MutedSlate
                            )
                            Text(
                                text = if (isHindi) "$tenureDays दिन" else "$tenureDays Days",
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF2E7D32),
                                fontSize = 13.sp
                            )
                        }
                        Slider(
                            value = tenureDays.toFloat(),
                            onValueChange = { tenureDays = it.toInt() },
                            valueRange = 1f..15f,
                            steps = 14,
                            colors = SliderDefaults.colors(
                                thumbColor = OrangePrimary,
                                activeTrackColor = Color(0xFF2E7D32)
                            )
                        )
                    }

                    // 7. Work Details Description
                    item {
                        Text(
                            text = if (isHindi) "काम के बारे में विवरण (Description):" else "A brief description of work:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MutedSlate
                        )
                        OutlinedTextField(
                            value = customDescriptionHindi,
                            onValueChange = { customDescriptionHindi = it; customDescription = it },
                            modifier = Modifier.fillMaxWidth().height(70.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = OrangePrimary,
                                unfocusedContainerColor = SandyBg,
                                focusedContainerColor = SandyBg
                            )
                        )
                    }

                    // 8. Requirements for workers
                    item {
                        Text(
                            text = if (isHindi) "योग्यता व औज़ार की आवश्यकता (Requirements):" else "Required Tools or Criteria:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MutedSlate
                        )
                        OutlinedTextField(
                            value = customRequirementsHindi,
                            onValueChange = { customRequirementsHindi = it; customRequirements = it },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = OrangePrimary,
                                unfocusedContainerColor = SandyBg,
                                focusedContainerColor = SandyBg
                            )
                        )
                    }

                    // Call To Action Submit
                    item {
                        Button(
                            onClick = {
                                if (customTitleHindi.isBlank()) {
                                    Toast.makeText(context, if (isHindi) "कृपया शीर्षक भरें" else "Please enter job title", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                viewModel.addNewCustomJob(
                                    title = customTitle.ifEmpty { "General Masonry" },
                                    hindiTitle = customTitleHindi,
                                    company = businessName.ifEmpty { "Yadav Builders" },
                                    hindiCompany = if (isHindi) "यादव बिल्डर्स" else (businessName.ifEmpty { "Yadav Builders" }),
                                    salary = "₹$offeredWage / दिन",
                                    hindiSalary = "₹$offeredWage / दिन",
                                    location = customLocation,
                                    hindiLocation = customLocationHindi,
                                    distance = "450m",
                                    hindiDistance = "450 मी",
                                    tenure = tenureDays,
                                    category = category,
                                    description = customDescription,
                                    hindiDescription = customDescriptionHindi,
                                    requirements = customRequirements,
                                    hindiRequirements = customRequirementsHindi
                                )
                                showPostJobDialog = false
                                showPostSuccessDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("submit_posted_job_button")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Campaign, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isHindi) "पोस्ट करें और अलार्म बजाएं (Publish Work) 🚀" else "Publish Work & Call Near Crew 🚀",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // --- DIALOG: SUCCESSFUL BROADCAST ALERT RADAR SIMULATOR ---
    if (showPostSuccessDialog) {
        Dialog(onDismissRequest = { showPostSuccessDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1C21)),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(2.dp, Color.Yellow),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Campaign,
                        contentDescription = "Success",
                        tint = Color.Yellow,
                        modifier = Modifier
                            .size(56.dp)
                            .background(Color.Yellow.copy(alpha = 0.15f), CircleShape)
                            .padding(12.dp)
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = if (isHindi) "📡 रडार प्रसारण सक्रिय!" else "📡 RADAR BROADCAST ACTIVE!",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        color = Color.Yellow
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isHindi) 
                            "५०० मीटर के दायरे में सभी उपलब्ध $category भाइयों के फ़ोन पर पूर्ण-स्क्रीन सायरन अलार्म गूंज रहा है!" 
                            else "Full-screen high-volume siren alarms are alert-pinging all nearest $category specialists within 500m right now!",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                    
                    Spacer(modifier = Modifier.height(18.dp))
                    
                    // Pulsating simulated radar circle
                    Box(
                        modifier = Modifier.size(80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val pulseTransition = rememberInfiniteTransition(label = "broadcast")
                        val pulseSize by pulseTransition.animateFloat(
                            initialValue = 0.4f,
                            targetValue = 1.0f,
                            animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
                            label = "pulseSize"
                        )
                        val pulseAlpha by pulseTransition.animateFloat(
                            initialValue = 0.8f,
                            targetValue = 0.0f,
                            animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
                            label = "pulseAlpha"
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .drawBehind {
                                    drawCircle(
                                        color = Color.Yellow.copy(alpha = pulseAlpha),
                                        radius = (size.minDimension / 2) * pulseSize,
                                        style = Stroke(width = 3.dp.toPx())
                                    )
                                }
                        )
                        Icon(Icons.Default.WifiTethering, contentDescription = null, tint = Color.Yellow, modifier = Modifier.size(32.dp))
                    }
                    
                    Spacer(modifier = Modifier.height(18.dp))
                    
                    Text(
                        text = if (isHindi) "मजदूरों द्वारा काम स्वीकार करने की प्रतीक्षा है..." else "Awaiting task acceptance from candidate pool...",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Button(
                        onClick = { showPostSuccessDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().height(42.dp)
                    ) {
                        Text(text = if (isHindi) "ठीक है (Okay)" else "Done", color = Color(0xFF1E1C21), fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

// ==========================================
// --- TAB SCREEN: EMPLOYER PROFILE (WAGE CHANGER) ---
// ==========================================
@Composable
fun EmployerProfileTabScreen(
    viewModel: MainViewModel,
    isHindi: Boolean,
    onSpeak: (String) -> Unit
) {
    val context = LocalContext.current
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    var editBusinessName by remember { mutableStateOf(userProfile.businessName) }
    var editOfferedWage by remember { mutableStateOf(if (userProfile.offeredWage > 0) userProfile.offeredWage else 500) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SandyBg)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Main Employer Business Identity Board
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CleanWhite),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, GrayBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(OrangePrimary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Business, contentDescription = null, tint = OrangeClassic, modifier = Modifier.size(36.dp))
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = if (userProfile.businessName.isNotEmpty()) userProfile.businessName else "यादव इलेक्ट्रॉनिक्स",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp,
                        color = DarkCharcoal,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = if (isHindi) "🤝 रोजग़ार प्रदाता (Employer Profile)" else "🤝 Work Provider ID Profile",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = OrangeClassic
                    )

                    Divider(modifier = Modifier.padding(vertical = 12.dp), color = GrayBorder)

                    // Phone and Local coordinates display
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(text = if (isHindi) "फ़ोन:" else "Mobile:", fontSize = 11.sp, color = MutedSlate)
                            Text(text = userProfile.phone.ifEmpty { "9876543210" }, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = DarkCharcoal)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(text = if (isHindi) "रजिस्ट्रेशन तिथि:" else "Registered:", fontSize = 11.sp, color = MutedSlate)
                            Text(text = "03 जून, 2026", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = DarkCharcoal)
                        }
                    }
                }
            }
        }

        // Action parameters edit layout for employers
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CleanWhite),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, GrayBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isHindi) "अपनी काम की शर्तें बदलें" else "Set Hiring Rules & Wage rates",
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        color = DarkCharcoal
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Change Business/Proprietor Name input
                    Text(
                        text = if (isHindi) "कंपनी / दुकान का नाम:" else "Business (Shop) Name:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MutedSlate
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = editBusinessName,
                        onValueChange = { editBusinessName = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = OrangePrimary,
                            unfocusedContainerColor = SandyBg,
                            focusedContainerColor = SandyBg
                        )
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Wage Slider rate settings (Classic Ola/Uber customized feature)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (isHindi) "प्रस्तावित न्यूनतम दिहाड़ी:" else "Offered Daily Wage Rate:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MutedSlate
                        )
                        Text(
                            text = "₹$editOfferedWage / दिन",
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp,
                            color = OrangeClassic
                        )
                    }
                    Slider(
                        value = editOfferedWage.toFloat(),
                        onValueChange = { editOfferedWage = it.toInt() },
                        valueRange = 300f..1200f,
                        steps = 18,
                        colors = SliderDefaults.colors(
                            thumbColor = OrangePrimary,
                            activeTrackColor = OrangeClassic
                        )
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            viewModel.updateProfile(
                                name = userProfile.name,
                                phone = userProfile.phone,
                                location = userProfile.location,
                                role = userProfile.preferredRole,
                                skills = userProfile.skills,
                                experience = userProfile.experienceYears,
                                education = userProfile.educationLevel,
                                bio = userProfile.resumeBio,
                                userRoleType = userProfile.userRoleType,
                                businessName = editBusinessName,
                                offeredWage = editOfferedWage
                            )
                            Toast.makeText(context, if (isHindi) "शर्तें अपडेट हो गयी हैं!" else "Hiring details saved!", Toast.LENGTH_SHORT).show()
                            onSpeak(if (isHindi) "आपकी नयी काम की दिहाड़ी दर ₹$editOfferedWage सुरक्षित कर दी है।" else "Offering rate updated.")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Text(text = if (isHindi) "अपडेट करें (Save Details)" else "Save Hiring Settings", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Logout panel options
        item {
            Button(
                onClick = {
                    viewModel.logout()
                    onSpeak(if (isHindi) "सफलतापूर्वक लॉगआउट हुए।" else "Logged out successfully.")
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(text = if (isHindi) "खाता लॉग आउट करें (Log Out App)" else "Log Out and Reset Profile", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

