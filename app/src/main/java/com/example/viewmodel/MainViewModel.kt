package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.api.GeminiClient
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class MockJob(
    val id: String,
    val title: String,
    val hindiTitle: String,
    val company: String,
    val hindiCompany: String,
    val salary: String,
    val hindiSalary: String,
    val location: String,
    val hindiLocation: String,
    val distance: String,
    val hindiDistance: String,
    val tenureDays: Int,
    val category: String, // "मजदूर", "मिस्त्री", "बिजली मिस्त्री", "प्लंबर"
    val description: String,
    val hindiDescription: String,
    val requirements: String,
    val hindiRequirements: String,
    val skillsNeeded: List<String>,
    val shift: String = "Day Shift",
    val type: String = "Full-Time",
    val experienceNeeded: String = "Freshers Welcome"
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = DatabaseProvider.getDatabase(application)
    private val profileDao = database.userProfileDao()
    private val applicationDao = database.jobApplicationDao()
    private val messageDao = database.aiChatMessageDao()

    // --- State Management ---

    // 1. Navigation Flow: Home (0), Explore (1), AI Chat (2), Profile (3)
    private val _currentTab = MutableStateFlow(0)
    val currentTab: StateFlow<Int> = _currentTab.asStateFlow()

    fun selectTab(tab: Int) {
        _currentTab.value = tab
    }

    // 2. User Profile Setup (Room Source)
    val userProfile: StateFlow<UserProfile> = profileDao.getProfileFlow()
        .map { it ?: UserProfile() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserProfile()
        )

    fun updateProfile(
        name: String,
        phone: String,
        location: String,
        role: String,
        skills: String,
        experience: Int,
        education: String,
        bio: String,
        userRoleType: String = "Worker",
        businessName: String = "",
        offeredWage: Int = 500,
        aadhaarNumber: String = "",
        isAadhaarVerified: Boolean = false,
        profilePicUrl: String = "",
        upiId: String = "",
        withdrawnAmount: Int = 0,
        userLatitude: Double = 19.1154,
        userLongitude: Double = 72.8681
    ) {
        viewModelScope.launch {
            val updated = UserProfile(
                id = 1,
                name = name,
                phone = phone,
                location = location,
                preferredRole = role,
                skills = skills,
                experienceYears = experience,
                educationLevel = education,
                resumeBio = bio,
                userRoleType = userRoleType,
                businessName = businessName,
                offeredWage = offeredWage,
                aadhaarNumber = aadhaarNumber,
                isAadhaarVerified = isAadhaarVerified,
                profilePicUrl = profilePicUrl,
                upiId = upiId,
                withdrawnAmount = withdrawnAmount,
                userLatitude = userLatitude,
                userLongitude = userLongitude
            )
            profileDao.insertOrUpdateProfile(updated)
        }
    }

    fun logout() {
        viewModelScope.launch {
            val emptyProfile = UserProfile(
                id = 1,
                name = "",
                phone = "",
                location = "",
                preferredRole = "",
                skills = "",
                experienceYears = 0,
                educationLevel = "",
                resumeBio = "",
                userRoleType = "Worker",
                businessName = "",
                offeredWage = 500,
                aadhaarNumber = "",
                isAadhaarVerified = false,
                profilePicUrl = "",
                upiId = "",
                withdrawnAmount = 0
            )
            profileDao.insertOrUpdateProfile(emptyProfile)
        }
    }

    fun withdrawEarnings(amount: Int, upiId: String) {
        viewModelScope.launch {
            val current = profileDao.getProfileDirect() ?: UserProfile()
            val updated = current.copy(
                withdrawnAmount = current.withdrawnAmount + amount,
                upiId = upiId
            )
            profileDao.insertOrUpdateProfile(updated)
        }
    }

    fun updateAadhaarDetails(number: String, verified: Boolean) {
        viewModelScope.launch {
            val current = profileDao.getProfileDirect() ?: UserProfile()
            val updated = current.copy(
                aadhaarNumber = number,
                isAadhaarVerified = verified
            )
            profileDao.insertOrUpdateProfile(updated)
        }
    }

    fun updateProfilePhoto(url: String) {
        viewModelScope.launch {
            val current = profileDao.getProfileDirect() ?: UserProfile()
            val updated = current.copy(profilePicUrl = url)
            profileDao.insertOrUpdateProfile(updated)
        }
    }

    fun updateUserLocation(lat: Double, lng: Double, address: String) {
        viewModelScope.launch {
            val current = profileDao.getProfileDirect() ?: UserProfile()
            val updated = current.copy(
                userLatitude = lat,
                userLongitude = lng,
                location = address
            )
            profileDao.insertOrUpdateProfile(updated)
        }
    }

    // 3. Applications Applied (Room Source)
    val appliedJobs: StateFlow<List<JobApplication>> = applicationDao.getAllApplicationsFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val walletEarnings: StateFlow<Int> = combine(appliedJobs, userProfile) { apps, profile ->
        val completedEarnings = apps.filter { it.status == "Completed" }.sumOf { app ->
            val wage = app.salary.filter { it.isDigit() }.toIntOrNull() ?: 700
            val matchedJob = mockJobs.find { it.id == app.jobId }
            val days = matchedJob?.tenureDays ?: 5
            wage * days
        }
        val currentE = 12400 + completedEarnings - profile.withdrawnAmount
        if (currentE < 0) 0 else currentE
    }.stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = 12400)

    val walletDays: StateFlow<Int> = appliedJobs.map { apps ->
        val completedDays = apps.filter { it.status == "Completed" }.sumOf { app ->
            val matchedJob = mockJobs.find { it.id == app.jobId }
            matchedJob?.tenureDays ?: 5
        }
        18 + completedDays
    }.stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = 18)

    fun completeJob(jobId: String) {
        viewModelScope.launch {
            applicationDao.updateStatus(jobId, "Completed")
        }
    }

    fun applyToJob(job: MockJob) {
        viewModelScope.launch {
            if (!applicationDao.isAppliedDirect(job.id)) {
                val newApp = JobApplication(
                    jobId = job.id,
                    title = job.title,
                    company = job.company,
                    salary = job.salary,
                    location = job.location,
                    status = "Ongoing",
                    timestamp = System.currentTimeMillis()
                )
                applicationDao.insertApplication(newApp)
            }
        }
    }

    // 4. Job Explore & Search Filters
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategoryFilter = MutableStateFlow("All")
    val selectedCategoryFilter: StateFlow<String> = _selectedCategoryFilter.asStateFlow()

    private val _selectedLocationFilter = MutableStateFlow("All")
    val selectedLocationFilter: StateFlow<String> = _selectedLocationFilter.asStateFlow()

    fun updateSearchFilters(query: String, category: String, location: String) {
        _searchQuery.value = query
        _selectedCategoryFilter.value = category
        _selectedLocationFilter.value = location
    }

    // --- AI Chat Career Counselor Flow ---
    val chatMessages: StateFlow<List<AiChatMessage>> = messageDao.getAllMessagesFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _isAiThinking = MutableStateFlow(false)
    val isAiThinking: StateFlow<Boolean> = _isAiThinking.asStateFlow()

    fun sendChatMessage(messageText: String) {
        if (messageText.trim().isEmpty()) return

        viewModelScope.launch {
            // Save User Message
            val userMsg = AiChatMessage(sender = "user", message = messageText)
            messageDao.insertMessage(userMsg)

            // Trigger AI thoughts
            _isAiThinking.value = true

            // Generate profile context details
            val profile = userProfile.value
            val profileContextText = """
                Name: ${profile.name.ifEmpty { "Anonymous" }}
                Phone: ${profile.phone.ifEmpty { "Not Provided" }}
                City/Location: ${profile.location.ifEmpty { "Anywhere in India" }}
                Preferred Job: ${profile.preferredRole}
                Education: ${profile.educationLevel}
                Experience: ${profile.experienceYears} years
                Skills: ${profile.skills}
                Bio Summary: ${profile.resumeBio}
            """.trimIndent()

            // Fetch chat log history
            val messagesList = messageDao.getAllMessagesFlow().firstOrNull() ?: emptyList()
            // Map to plain list of Pairs
            val historyPairs = messagesList.map { it.sender to it.message }

            // Get Gemini counsel
            val aiResponse = GeminiClient.generateCounselorResponse(historyPairs + ("user" to messageText), profileContextText)

            // Save AI response
            _isAiThinking.value = false
            val aiMsg = AiChatMessage(sender = "ai", message = aiResponse)
            messageDao.insertMessage(aiMsg)
        }
    }

    fun clearChat() {
        viewModelScope.launch {
            messageDao.clearChatHistory()
            insertWelcomeMessage()
        }
    }

    private fun insertWelcomeMessage() {
        viewModelScope.launch {
            val count = messageDao.getAllMessagesFlow().firstOrNull()?.size ?: 0
            if (count == 0) {
                val intro = "Namaste! Main aapka **Career Mitra (करियर मित्र)** hoon. " +
                        "Main aapki job applications ke liye guidelines dene, mock interviews karne, " +
                        "aur local skills behtar karne me madad karunga. Aapke resume ko dhyan me rakhkar sawal poochein! 😊"
                messageDao.insertMessage(AiChatMessage(sender = "ai", message = intro))
            }
        }
    }

    init {
        // Automatically seed introductory message
        insertWelcomeMessage()
    }

    // --- Mock Job Postings Static Database ---
    val jobCategories = listOf("All", "मजदूर", "मिस्त्री", "बिजली मिस्त्री", "प्लंबर")
    val jobLocations = listOf("All", "Mumbai")

    // --- Dynamic Mock Job Postings Database ---
    private val initialJobs = listOf(
        MockJob(
            id = "job-001",
            title = "Carpenter (Door & Cabinet Repair)",
            hindiTitle = "बढ़ई चाहिए (किवाड़ और अलमारी मरम्मत)",
            company = "Ramesh Yadav (Homeowner)",
            hindiCompany = "रमेश यादव (मकान मालिक)",
            salary = "₹ 700 / Day",
            hindiSalary = "₹ 700 / दिन",
            location = "Andheri East, Mumbai",
            hindiLocation = "अंधेरी ईस्ट, मुम्बई",
            distance = "2 km away",
            hindiDistance = "2 किमी. दूर",
            tenureDays = 5,
            category = "मिस्त्री",
            description = "Carpenter needed to repair wooden doors, windows, and kitchen cabinets. Owner will provide raw wood material. Bring your own tools.",
            hindiDescription = "मकान में लकड़ी के किवाड़, खिड़की और किचन की अलमारी की मरम्मत का काम है। मालिक लकड़ी का सामान देगा, औजार आपके खुद के होने चाहिए।",
            requirements = "2+ years of carpenter experience. Bring own toolbox.",
            hindiRequirements = "कम से कम २ साल बढ़ई का काम किया हो। खुद का औजार डिब्बा (Toolbox) लाना जरूरी है।",
            skillsNeeded = listOf("Woodwork", "Tool Handling", "Cabinet Repair")
        ),
        MockJob(
            id = "job-002",
            title = "Plumber (Bathroom Leak Repair)",
            hindiTitle = "प्लंबर चाहिए (नल और पाइप मरम्मत)",
            company = "Dinesh Kumar (Society Manager)",
            hindiCompany = "दिनेश कुमार (सोसाइटी मैनेजर)",
            salary = "₹ 600 / Day",
            hindiSalary = "₹ 600 / दिन",
            location = "Jogeshwari, Mumbai",
            hindiLocation = "जोगेश्वरी, मुम्बई",
            distance = "1.5 km away",
            hindiDistance = "1.5 किमी. दूर",
            tenureDays = 3,
            category = "प्लंबर",
            description = "Fix bathroom pipe leaks and install new water taps in a residential society building. Plumbing tape and sealant provided.",
            hindiDescription = "आवासीय सोसाइटी में बाथरूम के पाइप लीक ठीक करना और नए नल फिट करना। धागा और टेप सोसाइटी द्वारा दिया जाएगा।",
            requirements = "Knowledge of PVC and G.I. pipelines. Immediate joining.",
            hindiRequirements = "पीवीसी और पाइप फिटिंग की जानकारी होनी चाहिए। तुरंत काम शुरू करना है।",
            skillsNeeded = listOf("Pipe Fitting", "Leak Fixing", "Taps Repair")
        ),
        MockJob(
            id = "job-003",
            title = "Construction Helper (Wall Sanding)",
            hindiTitle = "मजदूर चाहिए (दीवार घिसाई और सीमेंट मिलाना)",
            company = "Mohan Sharma (Contractor)",
            hindiCompany = "मोहन शर्मा (ठेकेदार)",
            salary = "₹ 500 / Day",
            hindiSalary = "₹ 500 / दिन",
            location = "Vikhroli, Mumbai",
            hindiLocation = "विक्रोली, मुम्बई",
            distance = "3 km away",
            hindiDistance = "3 किमी. दूर",
            tenureDays = 15,
            category = "मजदूर",
            description = "General helper labor to grind walls, mix cement, lift sand bags, and assist painting teams.",
            hindiDescription = "दीवारों की घिसाई करना, सीमेंट और गारा मिलाना, भारी सामान उठाना और पेंटिंग टीम की सहायता करना मुख्य काम है।",
            requirements = "Good physical health. Hardworking individual.",
            hindiRequirements = "शारीरिक रूप से स्वस्थ और मजबूत होना चाहिए। मेहनती व्यक्ति की जरूरत है।",
            skillsNeeded = listOf("Heavy Lifting", "Cement Mixing", "Physical Fitness")
        ),
        MockJob(
            id = "job-004",
            title = "Electrician (House Wiring Fitting)",
            hindiTitle = "बिजली मिस्त्री (हाउस wiring और स्विच फिटिंग)",
            company = "Sunil Gupta (Elec Contractor)",
            hindiCompany = "सुनील गुप्ता (इलेक्ट्रिकल ठेकेदार)",
            salary = "₹ 800 / Day",
            hindiSalary = "₹ 800 / दिन",
            location = "Ghatkopar, Mumbai",
            hindiLocation = "घाटकोपर, मुम्बई",
            distance = "1.2 km away",
            hindiDistance = "1.2 किमी. दूर",
            tenureDays = 2,
            category = "बिजली मिस्त्री",
            description = "Electrician needed to fit internal conduits, wire connections, board installation, switches, and main switchboard.",
            hindiDescription = "नए फ्लैट के अंदर बिजली की पाइप फिटिंग, वायरिंग डालना, बोर्ड कनेक्शन और मुख्य स्विच बोर्ड लगाने का काम है।",
            requirements = "Experience in domestic switchboard wiring. Safety gloves necessary.",
            hindiRequirements = "घरेलू स्विचबोर्ड और मीटर वायरिंग का काम आना चाहिए। सुरक्षित दास्ताने (Safety gloves) होने चाहिए।",
            skillsNeeded = listOf("Wiring Fitments", "Board Connections", "Safety Gloves")
        )
    )

    private val _jobsList = MutableStateFlow<List<MockJob>>(initialJobs)
    val mockJobs: List<MockJob> get() = _jobsList.value

    // Real-Time Incoming Job Notification Events (like Uber/Ola order arrival)
    private val _incomingJobRequest = MutableSharedFlow<MockJob>(extraBufferCapacity = 1)
    val incomingJobRequest: SharedFlow<MockJob> = _incomingJobRequest.asSharedFlow()

    // Simulation simulation index to post varied templates
    private var simulatedCounter = 0

    fun postSimulatedNearbyJob() {
        val simulatedTemplates = listOf(
            MockJob(
                id = "sim-job-${System.currentTimeMillis()}",
                title = "Wall Painter (High-Finish Putty)",
                hindiTitle = "पुट्टी और पेंटर चाहिए (दीवार सजावट)",
                company = "Rajesh Mehta (Flat Owner)",
                hindiCompany = "राजेश मेहता (मकान मालिक)",
                salary = "₹ 750 / Day",
                hindiSalary = "₹ 750 / दिन",
                location = "Versova, Mumbai",
                hindiLocation = "वरसोवा, मुम्बई",
                distance = "0.4 km away",
                hindiDistance = "0.4 किमी. दूर (बहुत नजदीक)",
                tenureDays = 4,
                category = "मजदूर",
                description = "Wall paint and putty sanding needed for a living room. All paint cans, brush and safety stool provided.",
                hindiDescription = "लिविंग रूम के लिए दीवार पेंट और पुट्टी घिसाई की आवश्यकता है। पेंट के डिब्बे, ब्रश और सेफ्टी स्टूल दिए जाएंगे।",
                requirements = "At least 1 year painting experience.",
                hindiRequirements = "कम से कम १ साल दीवार पेंटिंग का अनुभव होना चाहिए।",
                skillsNeeded = listOf("Painting", "Wall Putty", "Finishing")
            ),
            MockJob(
                id = "sim-job-${System.currentTimeMillis() + 1}",
                title = "Electrician (Inverter Fitting)",
                hindiTitle = "बिजली मिस्त्री (इन्वर्टर और बैटरी कनेक्शन)",
                company = "Ankita Joshi (Homeowner)",
                hindiCompany = "अंकिता जोशी (मकान मालिक)",
                salary = "₹ 900 / Day",
                hindiSalary = "₹ 900 / दिन",
                location = "Yari Road, Mumbai",
                hindiLocation = "यारी रोड, मुम्बई",
                distance = "0.8 km away",
                hindiDistance = "0.8 किमी. दूर (बहुत नजदीक)",
                tenureDays = 1,
                category = "बिजली मिस्त्री",
                description = "Setup domestic heavy duty inverter line wiring and secure backup battery terminal cabling safely.",
                hindiDescription = "घर के इन्वर्टर और बैटरी का कनेक्शन करना और सुरक्षित ढंग से हैवी पावर लाइन वायरिंग सेट करना।",
                requirements = "Must have insulating shoes and line testers.",
                hindiRequirements = "इन्सुलेटेड जूते और लाइन टेस्टर खुद के पास होने चाहिए।",
                skillsNeeded = listOf("Inverter Fitting", "Power cabling", "Safety Testing")
            ),
            MockJob(
                id = "sim-job-${System.currentTimeMillis() + 2}",
                title = "Slab Helper / Concrete Mixer",
                hindiTitle = "मसाला मिक्सिंग व स्लैब भराई मजदूर",
                company = "Karan Johar (Site Incharge)",
                hindiCompany = "करण जौहर (साइट इंचार्ज)",
                salary = "₹ 550 / Day",
                hindiSalary = "₹ 550 / दिन",
                location = "Lokhandwala, Mumbai",
                hindiLocation = "लोखंडवाला, मुम्बई",
                distance = "0.3 km away",
                hindiDistance = "0.3 किमी. दूर (बहुत नजदीक)",
                tenureDays = 10,
                category = "मजदूर",
                description = "Concrete mixing, brick carriage, and assisting local masons in roof tile layering works.",
                hindiDescription = "कंक्रीट मसाला मिलाना, ईंट पहुँचाना और छत ढलाई का काम करने में राजमिस्त्री की सहायता करना।",
                requirements = "Physically fit for active material porting.",
                hindiRequirements = "सामान ढोने के लिए शारीरिक रूप से बिलकुल तंदुरुस्त होना चाहिए।",
                skillsNeeded = listOf("Cement Mixing", "Material Carrying", "Active Labor")
            ),
            MockJob(
                id = "sim-job-${System.currentTimeMillis() + 3}",
                title = "Emergency Tap & Line Plumber",
                hindiTitle = "हैंडपंप व वाशबेसिन नल मरम्मत मिस्त्री",
                company = "Society Block C",
                hindiCompany = "सोसाइटी ब्लॉक सी",
                salary = "₹ 800 / Day",
                hindiSalary = "₹ 800 / दिन",
                location = "Andheri West, Mumbai",
                hindiLocation = "अंधेरी वेस्ट, मुम्बई",
                distance = "0.2 km away",
                hindiDistance = "0.2 किमी. दूर (बहुत नजदीक)",
                tenureDays = 2,
                category = "प्लंबर",
                description = "Replace internal copper washers and brass coupling handles in continuous leaking overhead pipeline.",
                hindiDescription = "ओवरहेड वाटर पाइपलाइन में ब्रास कपलिंग हैंडल बदलना और तांबे के वाशर फिट करके लीक बंद करना।",
                requirements = "Must carry own pipe wrenches.",
                hindiRequirements = "अपना खुद का पाइप रेंच अवश्य लाना होगा।",
                skillsNeeded = listOf("Leakage repair", "Wrench grip", "Washer replacement")
            )
        )

        val nextJob = simulatedTemplates[simulatedCounter % simulatedTemplates.size]
        simulatedCounter++

        viewModelScope.launch {
            // Prepend new job to the top of the jobs list
            _jobsList.value = listOf(nextJob) + _jobsList.value
            // Emit the incoming job event for real-time full-screen overlay popup
            _incomingJobRequest.emit(nextJob)
        }
    }

    fun addNewCustomJob(
        title: String,
        hindiTitle: String,
        company: String,
        hindiCompany: String,
        salary: String,
        hindiSalary: String,
        location: String,
        hindiLocation: String,
        distance: String,
        hindiDistance: String,
        tenure: Int,
        category: String,
        description: String,
        hindiDescription: String,
        requirements: String,
        hindiRequirements: String
    ) {
        val newJob = MockJob(
            id = "custom-${System.currentTimeMillis()}",
            title = title,
            hindiTitle = hindiTitle,
            company = company,
            hindiCompany = hindiCompany,
            salary = salary,
            hindiSalary = hindiSalary,
            location = location,
            hindiLocation = hindiLocation,
            distance = distance,
            hindiDistance = hindiDistance,
            tenureDays = tenure,
            category = category,
            description = description,
            hindiDescription = hindiDescription,
            requirements = requirements,
            hindiRequirements = hindiRequirements,
            skillsNeeded = listOf("General Skills")
        )

        viewModelScope.launch {
            _jobsList.value = listOf(newJob) + _jobsList.value
            _incomingJobRequest.emit(newJob)
        }
    }

    // Filtered list of Mock Jobs
    val filteredJobs: StateFlow<List<MockJob>> = combine(
        _searchQuery,
        _selectedCategoryFilter,
        _selectedLocationFilter,
        _jobsList
    ) { query, category, location, jobs ->
        jobs.filter {
            val queryMatch = it.title.contains(query, ignoreCase = true) || 
                             it.hindiTitle.contains(query, ignoreCase = true) ||
                             it.company.contains(query, ignoreCase = true) ||
                             it.hindiCompany.contains(query, ignoreCase = true) ||
                             it.description.contains(query, ignoreCase = true) ||
                             it.hindiDescription.contains(query, ignoreCase = true)
            
            val categoryMatch = category == "All" || it.category == category
            val locationMatch = location == "All" || it.location.contains(location, ignoreCase = true) || it.hindiLocation.contains(location, ignoreCase = true)

            queryMatch && categoryMatch && locationMatch
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
}
