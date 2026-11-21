package com.example.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError

class PharmacyViewModel(application: Application) : AndroidViewModel(application) {
    private val database = PharmacyDatabase.getDatabase(application)
    private val repository = PharmacyRepository(database.pharmacyDao())

    private val sharedPrefs = application.getSharedPreferences("PharmacyPrefs", Context.MODE_PRIVATE)

    // Logo image stored as base64 string
    private val _uploadedLogoBase64 = MutableStateFlow<String?>(sharedPrefs.getString("uploaded_logo_base64", null))
    val uploadedLogoBase64: StateFlow<String?> = _uploadedLogoBase64.asStateFlow()

    fun updateUploadedLogo(base64: String?) {
        _uploadedLogoBase64.value = base64
        if (base64 != null) {
            sharedPrefs.edit().putString("uploaded_logo_base64", base64).apply()
        } else {
            sharedPrefs.edit().remove("uploaded_logo_base64").apply()
        }
    }

    fun uploadLogoFromUri(uri: Uri) {
        viewModelScope.launch {
            try {
                val inputStream = getApplication<Application>().contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes()
                inputStream?.close()
                if (bytes != null) {
                    val base64String = Base64.encodeToString(bytes, Base64.DEFAULT)
                    updateUploadedLogo(base64String)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun removeLogo() {
        updateUploadedLogo(null)
    }

    // Language Toggle: true for বাংলা (Bengali), false for ENG (English)
    private val _isBengali = MutableStateFlow(true)
    val isBengali: StateFlow<Boolean> = _isBengali.asStateFlow()

    fun toggleLanguage() {
        _isBengali.value = !_isBengali.value
    }

    fun setLanguage(bengali: Boolean) {
        _isBengali.value = bengali
    }

    // Theme Toggle State: true for Black Mode (Dark), false for Light Mode
    private val _isDarkMode = MutableStateFlow(true)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    fun toggleDarkMode() {
        _isDarkMode.value = !_isDarkMode.value
    }

    fun setDarkMode(darkMode: Boolean) {
        _isDarkMode.value = darkMode
    }

    // Dynamic Multi-User / Multi-Pharmacy profiles
    data class UserProfile(
        val username: String,
        val pharmacyName: String,
        val address: String,
        val phone: String,
        val ownerName: String,
        val pass: String,
        val role: String = "merchant", // "merchant" / "admin", "staff", "customer", "super_admin"
        val openTime: String = "09:00 AM",
        val closeTime: String = "11:00 PM",
        val ward: String = "",
        val neighborhood: String = "",
        val latitude: Double = 0.0,
        val longitude: Double = 0.0,
        val securityCode: String = "",
        val linkedAdminUsername: String = "",
        val portalTrack: String = "" // "", "merchant", "staff"
    ) {
        val isLocationSet: Boolean
            get() = address.isNotBlank() || (latitude != 0.0 && longitude != 0.0)
    }

    fun isMerchant(user: UserProfile = _currentUser.value): Boolean {
        return user.role == "merchant" || user.role == "admin"
    }

    data class PermissionRequest(
        val id: String = "",
        val staffUsername: String = "",
        val staffPhone: String = "",
        val adminUsername: String = "",
        val pharmacyName: String = "",
        val ownerName: String = "",
        val securityCode: String = "",
        val status: String = "Pending", // "Pending", "Approved", "Rejected"
        val timestamp: Long = System.currentTimeMillis()
    )

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val profileMapType = Types.newParameterizedType(
        Map::class.java,
        String::class.java,
        UserProfile::class.java
    )
    private val profileMapAdapter = moshi.adapter<Map<String, UserProfile>>(profileMapType)

    private fun defaultProfiles(): Map<String, UserProfile> {
        return mapOf(
            "customer" to UserProfile(
                username = "customer",
                pharmacyName = "",
                address = "",
                phone = "+৮৮০ ১৮০০-১১২২৩৩",
                ownerName = "গ্রাহক ইউজার",
                pass = "1234",
                role = "customer",
                securityCode = "#ROC-1001",
                latitude = 0.0,
                longitude = 0.0,
                portalTrack = ""
            ),
            "staff" to UserProfile(
                username = "staff",
                pharmacyName = "",
                address = "",
                phone = "+৮৮০ ১৯০০-৪৪৫৫৬৬",
                ownerName = "স্টাফ সদস্য",
                pass = "1234",
                role = "staff",
                securityCode = "#ROS-2002",
                linkedAdminUsername = "",
                latitude = 0.0,
                longitude = 0.0,
                portalTrack = "staff"
            ),
            "merchant" to UserProfile(
                username = "merchant",
                pharmacyName = "নিকট ফার্মেসী",
                address = "জিন্দাবাজার, সিলেট",
                phone = "+৮৮০ ১৭০০-০০০০০০",
                ownerName = "ফার্মেসী মার্চেন্ট",
                pass = "1234",
                role = "admin",
                securityCode = "#ROPH-3003",
                latitude = 24.8967,
                longitude = 91.8687,
                portalTrack = "merchant"
            )
        )
    }

    private fun loadProfiles(): Map<String, UserProfile> {
        val result = defaultProfiles().toMutableMap()
        val json = sharedPrefs.getString("saved_user_profiles", null)
        if (json != null) {
            try {
                val loaded = profileMapAdapter.fromJson(json) ?: emptyMap()
                val assignedCodes = mutableSetOf<String>()
                result.values.forEach { assignedCodes.add(it.securityCode) }
                loaded.forEach { (uname, prof) ->
                    var p = prof
                    if (p.address.contains("মিরপুর ২") || p.address.contains("Mirpur 2")) {
                        p = p.copy(address = "")
                    }
                    if (p.role == "customer" && p.latitude == 24.9015 && p.longitude == 91.8683 && p.address.isBlank()) {
                        p = p.copy(latitude = 0.0, longitude = 0.0)
                    }
                    if (p.role == "staff" && p.linkedAdminUsername == "merchant" && p.pharmacyName == "নিকট ফার্মেসী") {
                        p = p.copy(linkedAdminUsername = "", pharmacyName = "", address = "")
                    }
                    if (p.portalTrack.isBlank()) {
                        p = when {
                            p.role == "admin" || p.role == "merchant" -> p.copy(portalTrack = "merchant")
                            p.role == "staff" -> p.copy(portalTrack = "staff")
                            else -> p
                        }
                    }
                    var code = p.securityCode.trim().uppercase()
                    if (code.isBlank() || code == "#ROPH001" || assignedCodes.contains(code)) {
                        val prefix = when (p.role) {
                            "staff" -> "#ROS-"
                            "customer" -> "#ROC-"
                            else -> "#ROPH-"
                        }
                        var newCode: String
                        do {
                            newCode = "$prefix${(1000..9999).random()}"
                        } while (assignedCodes.contains(newCode) || newCode == "#ROPH001")
                        code = newCode
                        p = p.copy(securityCode = code)
                    }
                    assignedCodes.add(code)
                    result[uname] = p
                }
                return result
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return result
    }

    private fun saveProfiles(profiles: Map<String, UserProfile>) {
        try {
            val json = profileMapAdapter.toJson(profiles)
            sharedPrefs.edit().putString("saved_user_profiles", json).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private val _userProfiles = MutableStateFlow<Map<String, UserProfile>>(loadProfiles())
    val userProfiles: StateFlow<Map<String, UserProfile>> = _userProfiles.asStateFlow()

    private val _permissionRequests = MutableStateFlow<List<PermissionRequest>>(emptyList())
    val permissionRequests: StateFlow<List<PermissionRequest>> = _permissionRequests.asStateFlow()

    private fun getSavedCurrentUsername(): String? {
        return sharedPrefs.getString("logged_in_username", null)
    }

    private fun saveCurrentUsername(username: String?) {
        if (username != null) {
            sharedPrefs.edit().putString("logged_in_username", username).apply()
        } else {
            sharedPrefs.edit().remove("logged_in_username").apply()
        }
    }

    // Current user's info - Defaults to User (Customer) account
    private val _currentUser = MutableStateFlow<UserProfile>(
        getSavedCurrentUsername()?.let { _userProfiles.value[it] } ?: UserProfile(
            username = "user",
            pharmacyName = "",
            address = "",
            phone = "",
            ownerName = "ইউজার",
            pass = "",
            role = "customer",
            securityCode = "#ROC-1001"
        )
    )
    val currentUser: StateFlow<UserProfile> = _currentUser.asStateFlow()

    fun getEffectiveDatabaseUrl(): String {
        val customDbUrl = sharedPrefs.getString("custom_firebase_url", null)?.trim()
        val dbUrl = com.example.BuildConfig.FIREBASE_DATABASE_URL.trim('"', ' ')
        return if (!customDbUrl.isNullOrBlank()) {
            customDbUrl
        } else if (dbUrl.isNotBlank() && dbUrl != "MY_FIREBASE_DATABASE_URL") {
            dbUrl
        } else {
            "https://pharmasity-3cc40-default-rtdb.asia-southeast1.firebasedatabase.app"
        }
    }

    fun getEffectiveApiKey(): String {
        val custom = sharedPrefs.getString("custom_firebase_api_key", null)?.trim()
        val def = com.example.BuildConfig.FIREBASE_API_KEY.trim('"', ' ')
        return if (!custom.isNullOrBlank()) custom else if (def.isNotBlank() && def != "MY_FIREBASE_API_KEY") def else "AIzaSyCJ0R_zdWJhdQPNosoAIGERpv--2BgNcSM"
    }

    fun getEffectiveProjectId(): String {
        val custom = sharedPrefs.getString("custom_firebase_project_id", null)?.trim()
        val def = com.example.BuildConfig.FIREBASE_PROJECT_ID.trim('"', ' ')
        return if (!custom.isNullOrBlank()) custom else if (def.isNotBlank() && def != "MY_FIREBASE_PROJECT_ID") def else "pharmasity-3cc40"
    }

    fun getFirebaseRef(): com.google.firebase.database.DatabaseReference {
        val targetUrl = getEffectiveDatabaseUrl()
        
        // Ensure FirebaseApp is initialized before we try to get DatabaseReference
        try {
            if (com.google.firebase.FirebaseApp.getApps(getApplication()).isEmpty()) {
                val finalApiKey = getEffectiveApiKey()
                val appId = com.example.BuildConfig.FIREBASE_APPLICATION_ID.trim('"', ' ')
                val finalProjId = getEffectiveProjectId()
                
                val finalAppId = if (appId.isBlank() || appId == "MY_FIREBASE_APPLICATION_ID") "1:944020191590:android:cc31aae418fccc0a2b30a7" else appId

                val options = com.google.firebase.FirebaseOptions.Builder()
                    .setApiKey(finalApiKey)
                    .setApplicationId(finalAppId)
                    .setDatabaseUrl(targetUrl)
                    .setProjectId(finalProjId)
                    .build()
                com.google.firebase.FirebaseApp.initializeApp(getApplication(), options)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            FirebaseDatabase.getInstance(targetUrl).setPersistenceEnabled(true)
        } catch (e: Exception) {}

        return try {
            FirebaseDatabase.getInstance(targetUrl).reference
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                FirebaseDatabase.getInstance("https://pharmasity-3cc40-default-rtdb.asia-southeast1.firebasedatabase.app").reference
            } catch (ex: Exception) {
                ex.printStackTrace()
                FirebaseDatabase.getInstance().reference
            }
        }
    }

    fun generateUniqueId(): Long {
        return System.currentTimeMillis() * 1000 + (0..999).random()
    }

    private fun getDeletedMedicineIds(): Set<Long> {
        return sharedPrefs.getStringSet("deleted_medicine_ids", emptySet())
            ?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet()
    }

    private fun addDeletedMedicineId(id: Long) {
        val set = sharedPrefs.getStringSet("deleted_medicine_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
        set.add(id.toString())
        sharedPrefs.edit().putStringSet("deleted_medicine_ids", set).apply()
    }

    private fun removeDeletedMedicineId(id: Long) {
        val set = sharedPrefs.getStringSet("deleted_medicine_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
        if (set.remove(id.toString())) {
            sharedPrefs.edit().putStringSet("deleted_medicine_ids", set).apply()
        }
    }

    val isOnlineSyncConnected = MutableStateFlow(false)
    val firebaseSyncMessage = MutableStateFlow("সার্ভার সংযোগের অপেক্ষা...")
    val customFirebaseDatabaseUrl = MutableStateFlow(sharedPrefs.getString("custom_firebase_url", "") ?: "")
    val customFirebaseApiKey = MutableStateFlow(sharedPrefs.getString("custom_firebase_api_key", "") ?: "")
    val customFirebaseProjectId = MutableStateFlow(sharedPrefs.getString("custom_firebase_project_id", "") ?: "")
    val isTestingConnection = MutableStateFlow(false)

    fun setCustomFirebaseDatabaseUrl(url: String) {
        setCustomServerConfig(url)
    }

    fun setCustomServerConfig(
        databaseUrl: String,
        apiKey: String = "",
        projectId: String = "",
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        val trimmed = databaseUrl.trim()
        if (trimmed.isBlank()) {
            onResult(false, if (isBengali.value) "সার্ভার ডাটাবেজ URL ফাঁকা রাখা যাবে না!" else "Database URL cannot be blank!")
            return
        }

        val formatted = if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            "https://$trimmed"
        } else {
            trimmed
        }

        sharedPrefs.edit().putString("custom_firebase_url", formatted).apply()
        customFirebaseDatabaseUrl.value = formatted

        if (apiKey.isNotBlank()) {
            sharedPrefs.edit().putString("custom_firebase_api_key", apiKey.trim()).apply()
            customFirebaseApiKey.value = apiKey.trim()
        } else {
            sharedPrefs.edit().remove("custom_firebase_api_key").apply()
            customFirebaseApiKey.value = ""
        }

        if (projectId.isNotBlank()) {
            sharedPrefs.edit().putString("custom_firebase_project_id", projectId.trim()).apply()
            customFirebaseProjectId.value = projectId.trim()
        } else {
            sharedPrefs.edit().remove("custom_firebase_project_id").apply()
            customFirebaseProjectId.value = ""
        }

        reconnectFirebase(onResult)
    }

    fun resetDefaultServer(onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        sharedPrefs.edit()
            .remove("custom_firebase_url")
            .remove("custom_firebase_api_key")
            .remove("custom_firebase_project_id")
            .apply()
        customFirebaseDatabaseUrl.value = ""
        customFirebaseApiKey.value = ""
        customFirebaseProjectId.value = ""
        reconnectFirebase(onResult)
    }

    fun reconnectFirebase(onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        isTestingConnection.value = true
        firebaseSyncMessage.value = if (isBengali.value) "সার্ভারে সংযোগ করা হচ্ছে..." else "Connecting to server..."

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val targetUrl = getEffectiveDatabaseUrl()
                val finalApiKey = getEffectiveApiKey()
                val finalProjId = getEffectiveProjectId()
                val rawAppId = com.example.BuildConfig.FIREBASE_APPLICATION_ID.trim('"', ' ')
                val finalAppId = if (rawAppId.isBlank() || rawAppId == "MY_FIREBASE_APPLICATION_ID") "1:944020191590:android:cc31aae418fccc0a2b30a7" else rawAppId

                try {
                    val apps = com.google.firebase.FirebaseApp.getApps(getApplication())
                    for (app in apps) {
                        try { app.delete() } catch (_: Exception) {}
                    }
                } catch (_: Exception) {}

                val options = com.google.firebase.FirebaseOptions.Builder()
                    .setApiKey(finalApiKey)
                    .setApplicationId(finalAppId)
                    .setDatabaseUrl(targetUrl)
                    .setProjectId(finalProjId)
                    .build()
                com.google.firebase.FirebaseApp.initializeApp(getApplication(), options)

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    setupFirebaseSync()
                }

                // Verify write ping
                val pingRef = com.google.firebase.database.FirebaseDatabase.getInstance(targetUrl).reference.child("_server_status").child("last_ping")
                pingRef.setValue(System.currentTimeMillis())
                    .addOnSuccessListener {
                        isOnlineSyncConnected.value = true
                        firebaseSyncMessage.value = if (isBengali.value) "সার্ভার সংযুক্ত ও সক্রিয়" else "Server connected & active"
                        isTestingConnection.value = false

                        // Push local profiles to newly connected server
                        _userProfiles.value.forEach { (_, profile) -> pushProfileToFirebase(profile) }

                        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                            onResult(true, if (isBengali.value) "সার্ভার সফলভাবে যুক্ত ও অনলাইন!" else "Server connected successfully!")
                        }
                    }
                    .addOnFailureListener { error ->
                        isOnlineSyncConnected.value = false
                        val err = error.message ?: ""
                        if (err.contains("Permission denied", ignoreCase = true)) {
                            firebaseSyncMessage.value = if (isBengali.value) "⚠️ পারমিশন ত্রুটি: Firebase Rules-এ .read ও .write ট্রু (true) করুন" else "Permission denied in Firebase Rules"
                            viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                                onResult(false, if (isBengali.value) "পারমিশন ত্রুটি! Firebase Rules চেক করুন।" else "Permission denied! Check Firebase Rules.")
                            }
                        } else {
                            firebaseSyncMessage.value = if (isBengali.value) "সার্ভার ত্রুটি: $err" else "Server error: $err"
                            viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                                onResult(false, err)
                            }
                        }
                        isTestingConnection.value = false
                    }

                kotlinx.coroutines.delay(6000)
                if (isTestingConnection.value) {
                    isTestingConnection.value = false
                }
            } catch (e: Exception) {
                isTestingConnection.value = false
                isOnlineSyncConnected.value = false
                val msg = e.localizedMessage ?: "Connection error"
                firebaseSyncMessage.value = msg
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onResult(false, msg)
                }
            }
        }
    }

    fun pushMedicineToFirebase(medicine: Medicine) {
        removeDeletedMedicineId(medicine.id)
        try {
            getFirebaseRef()
                .child("medicines")
                .child(medicine.id.toString())
                .setValue(medicine)
                .addOnSuccessListener {
                    isOnlineSyncConnected.value = true
                }
                .addOnFailureListener { error ->
                    isOnlineSyncConnected.value = false
                    if (error.message?.contains("Permission denied", ignoreCase = true) == true) {
                        firebaseSyncMessage.value = "⚠️ পারমিশন ত্রুটি: Firebase Rules-এ .read ও .write ট্রু করুন"
                    }
                }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun deleteMedicineFromFirebase(id: Long) {
        addDeletedMedicineId(id)
        try {
            getFirebaseRef()
                .child("medicines")
                .child(id.toString())
                .removeValue()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun pushProfileToFirebase(profile: UserProfile) {
        try {
            getFirebaseRef()
                .child("users")
                .child(profile.username)
                .setValue(profile)
                .addOnSuccessListener {
                    isOnlineSyncConnected.value = true
                }
                .addOnFailureListener { error ->
                    isOnlineSyncConnected.value = false
                    if (error.message?.contains("Permission denied", ignoreCase = true) == true) {
                        firebaseSyncMessage.value = "⚠️ পারমিশন ত্রুটি: Firebase Rules-এ .read ও .write ট্রু করুন"
                    }
                }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setupFirebaseSync() {
        val targetUrl = getEffectiveDatabaseUrl()

        try {
            if (FirebaseApp.getApps(getApplication()).isEmpty()) {
                val apiKey = com.example.BuildConfig.FIREBASE_API_KEY.trim('"', ' ')
                val appId = com.example.BuildConfig.FIREBASE_APPLICATION_ID.trim('"', ' ')
                val projId = com.example.BuildConfig.FIREBASE_PROJECT_ID.trim('"', ' ')

                val finalApiKey = if (apiKey.isBlank() || apiKey == "MY_FIREBASE_API_KEY") "AIzaSyCJ0R_zdWJhdQPNosoAIGERpv--2BgNcSM" else apiKey
                val finalAppId = if (appId.isBlank() || appId == "MY_FIREBASE_APPLICATION_ID") "1:944020191590:android:cc31aae418fccc0a2b30a7" else appId
                val finalProjId = if (projId.isBlank() || projId == "MY_FIREBASE_PROJECT_ID") "pharmasity-3cc40" else projId

                val options = FirebaseOptions.Builder()
                    .setApiKey(finalApiKey)
                    .setApplicationId(finalAppId)
                    .setDatabaseUrl(targetUrl)
                    .setProjectId(finalProjId)
                    .build()
                FirebaseApp.initializeApp(getApplication(), options)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Try anonymous auth
        try {
            val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
            if (auth.currentUser == null) {
                auth.signInAnonymously().addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        isOnlineSyncConnected.value = true
                    }
                }
            }
        } catch (e: Exception) {}

        val db = try {
            FirebaseDatabase.getInstance(targetUrl).reference
        } catch (e: Exception) {
            getFirebaseRef()
        }

        // Track live connection state
        try {
            FirebaseDatabase.getInstance(targetUrl).getReference(".info/connected")
                .addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val connected = snapshot.getValue(Boolean::class.java) ?: false
                        isOnlineSyncConnected.value = connected
                        if (connected) {
                            firebaseSyncMessage.value = "অনলাইন ক্লাউড ডাটাবেজ সক্রিয় ও সংযুক্ত"
                        } else {
                            firebaseSyncMessage.value = "ক্লাউড সংযোগের অপেক্ষা..."
                        }
                    }
                    override fun onCancelled(error: DatabaseError) {
                        isOnlineSyncConnected.value = false
                        if (error.code == DatabaseError.PERMISSION_DENIED) {
                            firebaseSyncMessage.value = "ফায়ারবেস পারমিশন ত্রুটি: আপনার নিজস্ব Firebase DB URL সেট করুন"
                        }
                    }
                })
        } catch (e: Exception) {}

        // 1. Sync User Profiles
        db.child("users").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val remoteProfiles = mutableMapOf<String, UserProfile>()
                for (child in snapshot.children) {
                    val username = child.key ?: continue
                    val pharmacyName = child.child("pharmacyName").getValue(String::class.java) ?: ""
                    val address = child.child("address").getValue(String::class.java) ?: ""
                    val phone = child.child("phone").getValue(String::class.java) ?: ""
                    val ownerName = child.child("ownerName").getValue(String::class.java) ?: ""
                    val pass = child.child("pass").getValue(String::class.java) ?: ""
                    val role = child.child("role").getValue(String::class.java) ?: "admin"
                    val openTime = child.child("openTime").getValue(String::class.java) ?: "09:00 AM"
                    val closeTime = child.child("closeTime").getValue(String::class.java) ?: "11:00 PM"
                    val ward = child.child("ward").getValue(String::class.java) ?: ""
                    val neighborhood = child.child("neighborhood").getValue(String::class.java) ?: ""
                    val latitude = child.child("latitude").getValue(Double::class.java) ?: 24.9015
                    val longitude = child.child("longitude").getValue(Double::class.java) ?: 91.8683
                    val rawSecCode = child.child("securityCode").getValue(String::class.java)?.trim() ?: ""
                    val securityCode = if (rawSecCode.isNotBlank() && rawSecCode != "#ROPH001") {
                        rawSecCode
                    } else {
                        val prefix = when (role) {
                            "staff" -> "#ROS-"
                            "customer" -> "#ROC-"
                            else -> "#ROPH-"
                        }
                        val hashNum = Math.abs(username.hashCode() % 9000) + 1000
                        "$prefix$hashNum"
                    }
                    val linkedAdminUsername = child.child("linkedAdminUsername").getValue(String::class.java) ?: ""
                    val rawAddress = child.child("address").getValue(String::class.java) ?: ""
                    val cleanAddress = if (rawAddress.contains("মিরপুর ২") || rawAddress.contains("Mirpur 2")) "" else rawAddress
                    val portalTrack = child.child("portalTrack").getValue(String::class.java) ?: ""
                    remoteProfiles[username] = UserProfile(
                        username = username,
                        pharmacyName = pharmacyName,
                        address = cleanAddress,
                        phone = phone,
                        ownerName = ownerName,
                        pass = pass,
                        role = role,
                        openTime = openTime,
                        closeTime = closeTime,
                        ward = ward,
                        neighborhood = neighborhood,
                        latitude = latitude,
                        longitude = longitude,
                        securityCode = securityCode,
                        linkedAdminUsername = linkedAdminUsername,
                        portalTrack = portalTrack
                    )
                }
                val merged = _userProfiles.value.toMutableMap()
                merged.putAll(remoteProfiles)
                // Upload any local profiles missing from remote to Firebase
                _userProfiles.value.forEach { (uname, profile) ->
                    if (!remoteProfiles.containsKey(uname)) {
                        pushProfileToFirebase(profile)
                    }
                }
                if (merged.isNotEmpty()) {
                    _userProfiles.value = merged
                    saveProfiles(merged)
                    
                    // Update _currentUser state if their synced profile changed
                    val currentUsername = getSavedCurrentUsername()
                    if (currentUsername != null && merged.containsKey(currentUsername)) {
                        val updatedProfile = merged[currentUsername]!!
                        _currentUser.value = updatedProfile
                        pharmacyName.value = updatedProfile.pharmacyName
                        pharmacyAddress.value = updatedProfile.address
                        pharmacyPhone.value = updatedProfile.phone
                        ownerName.value = updatedProfile.ownerName
                        ownerPassword.value = updatedProfile.pass
                        pharmacyOpenTime.value = updatedProfile.openTime
                        pharmacyCloseTime.value = updatedProfile.closeTime
                        mapLatitude.value = updatedProfile.latitude
                        mapLongitude.value = updatedProfile.longitude
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {
                isOnlineSyncConnected.value = false
                if (error.code == DatabaseError.PERMISSION_DENIED) {
                    firebaseSyncMessage.value = "ফায়ারবেস পারমিশন ত্রুটি: আপনার নিজস্ব Firebase DB URL সেট করুন"
                }
            }
        })

        // 2. Sync Medicines
        db.child("medicines").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                viewModelScope.launch {
                    val remoteIds = mutableSetOf<Long>()
                    for (child in snapshot.children) {
                        try {
                            val id = child.key?.toLongOrNull() ?: continue
                            remoteIds.add(id)
                            val type = child.child("type").getValue(String::class.java) ?: "other"
                            val name = child.child("name").getValue(String::class.java) ?: ""
                            val dosageStrength = child.child("dosageStrength").getValue(String::class.java) ?: ""
                            val company = child.child("company").getValue(String::class.java) ?: ""
                            val genericName = child.child("genericName").getValue(String::class.java) ?: ""
                            val barcode = child.child("barcode").getValue(String::class.java) ?: ""
                            val expiryDate = child.child("expiryDate").getValue(String::class.java) ?: ""
                            val pharmacyOwnerUsername = child.child("pharmacyOwnerUsername").getValue(String::class.java) ?: "admin"
                            
                            val purchasePriceBox = child.child("purchasePriceBox").getValue(Double::class.java) ?: 0.0
                            val salePriceBox = child.child("salePriceBox").getValue(Double::class.java) ?: 0.0
                            val stripsPerBox = child.child("stripsPerBox").getValue(Int::class.java) ?: 1
                            val pcsPerStrip = child.child("pcsPerStrip").getValue(Int::class.java) ?: 1
                            val boxesInStock = child.child("boxesInStock").getValue(Int::class.java) ?: 0
                            val stripsInStock = child.child("stripsInStock").getValue(Int::class.java) ?: 0
                            val pcsInStock = child.child("pcsInStock").getValue(Int::class.java) ?: 0
                            
                            val mlPerBottle = child.child("mlPerBottle").getValue(Int::class.java) ?: 0
                            val bottlesInStock = child.child("bottlesInStock").getValue(Int::class.java) ?: 0
                            
                            val gramsPerTube = child.child("gramsPerTube").getValue(Int::class.java) ?: 0
                            val tubesInStock = child.child("tubesInStock").getValue(Int::class.java) ?: 0
                            
                            val vialsInStock = child.child("vialsInStock").getValue(Int::class.java) ?: 0
                            
                            val otherQuantity = child.child("otherQuantity").getValue(Double::class.java) ?: 0.0
                            val otherUnit = child.child("otherUnit").getValue(String::class.java) ?: ""
                            
                            val purchasePriceUnit = child.child("purchasePriceUnit").getValue(Double::class.java) ?: 0.0
                            val salePriceUnit = child.child("salePriceUnit").getValue(Double::class.java) ?: 0.0
                            val lowStockThreshold = child.child("lowStockThreshold").getValue(Int::class.java) ?: 0

                            val medicine = Medicine(
                                id = id,
                                type = type,
                                name = name,
                                dosageStrength = dosageStrength,
                                company = company,
                                genericName = genericName,
                                barcode = barcode,
                                expiryDate = expiryDate,
                                pharmacyOwnerUsername = pharmacyOwnerUsername,
                                purchasePriceBox = purchasePriceBox,
                                salePriceBox = salePriceBox,
                                stripsPerBox = stripsPerBox,
                                pcsPerStrip = pcsPerStrip,
                                boxesInStock = boxesInStock,
                                stripsInStock = stripsInStock,
                                pcsInStock = pcsInStock,
                                mlPerBottle = mlPerBottle,
                                bottlesInStock = bottlesInStock,
                                gramsPerTube = gramsPerTube,
                                tubesInStock = tubesInStock,
                                vialsInStock = vialsInStock,
                                otherQuantity = otherQuantity,
                                otherUnit = otherUnit,
                                purchasePriceUnit = purchasePriceUnit,
                                salePriceUnit = salePriceUnit,
                                lowStockThreshold = lowStockThreshold
                            )
                            repository.insertMedicine(medicine)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    try {
                        val localMeds = repository.allMedicines.first()
                        for (local in localMeds) {
                            if (!remoteIds.contains(local.id)) {
                                pushMedicineToFirebase(local)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {
                isOnlineSyncConnected.value = false
                if (error.code == DatabaseError.PERMISSION_DENIED) {
                    firebaseSyncMessage.value = "ফায়ারবেস পারমিশন ত্রুটি: আপনার নিজস্ব Firebase DB URL সেট করুন"
                }
            }
        })

        // 3. Sync Bills
        db.child("bills").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                viewModelScope.launch {
                    val remoteIds = mutableSetOf<String>()
                    for (child in snapshot.children) {
                        try {
                            val billNumber = child.key ?: continue
                            remoteIds.add(billNumber)
                            val timestamp = child.child("timestamp").getValue(Long::class.java) ?: 0L
                            val totalAmount = child.child("totalAmount").getValue(Double::class.java) ?: 0.0
                            val itemsJson = child.child("itemsJson").getValue(String::class.java) ?: "[]"
                            val isReturned = child.child("isReturned").getValue(Boolean::class.java) ?: false
                            val returnedTime = child.child("returnedTime").getValue(Long::class.java) ?: 0L
                            val returnedAmount = child.child("returnedAmount").getValue(Double::class.java) ?: 0.0
                            val returnedItemsJson = child.child("returnedItemsJson").getValue(String::class.java) ?: ""
                            val discount = child.child("discount").getValue(Double::class.java) ?: 0.0
                            val pharmacyOwnerUsername = child.child("pharmacyOwnerUsername").getValue(String::class.java) ?: "admin"
                            val isOnlineOrder = child.child("isOnlineOrder").getValue(Boolean::class.java) ?: false
                            val staffUsername = child.child("staffUsername").getValue(String::class.java) ?: ""

                            val bill = Bill(
                                billNumber = billNumber,
                                timestamp = timestamp,
                                totalAmount = totalAmount,
                                itemsJson = itemsJson,
                                isReturned = isReturned,
                                returnedTime = returnedTime,
                                returnedAmount = returnedAmount,
                                returnedItemsJson = returnedItemsJson,
                                discount = discount,
                                pharmacyOwnerUsername = pharmacyOwnerUsername,
                                isOnlineOrder = isOnlineOrder,
                                staffUsername = staffUsername
                            )
                            database.pharmacyDao().insertBill(bill)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    try {
                        val localBills = repository.allBills.first()
                        for (local in localBills) {
                            if (!remoteIds.contains(local.billNumber)) {
                                try {
                                    getFirebaseRef().child("bills").child(local.billNumber).setValue(local)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {
                isOnlineSyncConnected.value = false
            }
        })

        // 4. Sync Stock In Records
        db.child("stock_in_records").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                viewModelScope.launch {
                    val remoteIds = mutableSetOf<Long>()
                    for (child in snapshot.children) {
                        try {
                            val id = child.key?.toLongOrNull() ?: continue
                            remoteIds.add(id)
                            val medicineId = child.child("medicineId").getValue(Long::class.java) ?: 0L
                            val medicineName = child.child("medicineName").getValue(String::class.java) ?: ""
                            val medicineType = child.child("medicineType").getValue(String::class.java) ?: "other"
                            val timestamp = child.child("timestamp").getValue(Long::class.java) ?: 0L
                            val addedBoxes = child.child("addedBoxes").getValue(Int::class.java) ?: 0
                            val addedStrips = child.child("addedStrips").getValue(Int::class.java) ?: 0
                            val addedPcs = child.child("addedPcs").getValue(Int::class.java) ?: 0
                            val addedBottles = child.child("addedBottles").getValue(Int::class.java) ?: 0
                            val addedTubes = child.child("addedTubes").getValue(Int::class.java) ?: 0
                            val addedVials = child.child("addedVials").getValue(Int::class.java) ?: 0
                            val addedOtherQty = child.child("addedOtherQty").getValue(Double::class.java) ?: 0.0
                            val oldPurchasePrice = child.child("oldPurchasePrice").getValue(Double::class.java) ?: 0.0
                            val oldSalePrice = child.child("oldSalePrice").getValue(Double::class.java) ?: 0.0
                            val newPurchasePrice = child.child("newPurchasePrice").getValue(Double::class.java) ?: 0.0
                            val newSalePrice = child.child("newSalePrice").getValue(Double::class.java) ?: 0.0
                            val pharmacyOwnerUsername = child.child("pharmacyOwnerUsername").getValue(String::class.java) ?: "admin"

                            val record = StockInRecord(
                                id = id,
                                medicineId = medicineId,
                                medicineName = medicineName,
                                medicineType = medicineType,
                                timestamp = timestamp,
                                addedBoxes = addedBoxes,
                                addedStrips = addedStrips,
                                addedPcs = addedPcs,
                                addedBottles = addedBottles,
                                addedTubes = addedTubes,
                                addedVials = addedVials,
                                addedOtherQty = addedOtherQty,
                                oldPurchasePrice = oldPurchasePrice,
                                oldSalePrice = oldSalePrice,
                                newPurchasePrice = newPurchasePrice,
                                newSalePrice = newSalePrice,
                                pharmacyOwnerUsername = pharmacyOwnerUsername
                            )
                            database.pharmacyDao().insertStockInRecord(record)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    try {
                        val localRecords = repository.allStockInRecords.first()
                        for (local in localRecords) {
                            if (!remoteIds.contains(local.id)) {
                                try {
                                    getFirebaseRef().child("stock_in_records").child(local.id.toString()).setValue(local)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {
                isOnlineSyncConnected.value = false
            }
        })

        // 5. Sync Pending Medicines
        db.child("pending_medicines").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                viewModelScope.launch {
                    val remoteIds = mutableSetOf<Long>()
                    for (child in snapshot.children) {
                        try {
                            val id = child.key?.toLongOrNull() ?: continue
                            remoteIds.add(id)
                            val timestamp = child.child("timestamp").getValue(Long::class.java) ?: 0L
                            val staffUsername = child.child("staffUsername").getValue(String::class.java) ?: ""
                            val type = child.child("type").getValue(String::class.java) ?: "other"
                            val name = child.child("name").getValue(String::class.java) ?: ""
                            val dosageStrength = child.child("dosageStrength").getValue(String::class.java) ?: ""
                            val company = child.child("company").getValue(String::class.java) ?: ""
                            val genericName = child.child("genericName").getValue(String::class.java) ?: ""
                            val barcode = child.child("barcode").getValue(String::class.java) ?: ""
                            val expiryDate = child.child("expiryDate").getValue(String::class.java) ?: ""
                            
                            val purchasePriceBox = child.child("purchasePriceBox").getValue(Double::class.java) ?: 0.0
                            val salePriceBox = child.child("salePriceBox").getValue(Double::class.java) ?: 0.0
                            val stripsPerBox = child.child("stripsPerBox").getValue(Int::class.java) ?: 1
                            val pcsPerStrip = child.child("pcsPerStrip").getValue(Int::class.java) ?: 1
                            val boxesInStock = child.child("boxesInStock").getValue(Int::class.java) ?: 0
                            val stripsInStock = child.child("stripsInStock").getValue(Int::class.java) ?: 0
                            val pcsInStock = child.child("pcsInStock").getValue(Int::class.java) ?: 0
                            
                            val mlPerBottle = child.child("mlPerBottle").getValue(Int::class.java) ?: 0
                            val bottlesInStock = child.child("bottlesInStock").getValue(Int::class.java) ?: 0
                            
                            val gramsPerTube = child.child("gramsPerTube").getValue(Int::class.java) ?: 0
                            val tubesInStock = child.child("tubesInStock").getValue(Int::class.java) ?: 0
                            
                            val vialsInStock = child.child("vialsInStock").getValue(Int::class.java) ?: 0
                            
                            val otherQuantity = child.child("otherQuantity").getValue(Double::class.java) ?: 0.0
                            val otherUnit = child.child("otherUnit").getValue(String::class.java) ?: ""
                            
                            val purchasePriceUnit = child.child("purchasePriceUnit").getValue(Double::class.java) ?: 0.0
                            val salePriceUnit = child.child("salePriceUnit").getValue(Double::class.java) ?: 0.0
                            val lowStockThreshold = child.child("lowStockThreshold").getValue(Int::class.java) ?: 0
                            val targetMedicineId = child.child("targetMedicineId").getValue(Long::class.java)

                            val pending = PendingMedicine(
                                id = id,
                                timestamp = timestamp,
                                staffUsername = staffUsername,
                                type = type,
                                name = name,
                                dosageStrength = dosageStrength,
                                company = company,
                                genericName = genericName,
                                barcode = barcode,
                                expiryDate = expiryDate,
                                purchasePriceBox = purchasePriceBox,
                                salePriceBox = salePriceBox,
                                stripsPerBox = stripsPerBox,
                                pcsPerStrip = pcsPerStrip,
                                boxesInStock = boxesInStock,
                                stripsInStock = stripsInStock,
                                pcsInStock = pcsInStock,
                                mlPerBottle = mlPerBottle,
                                bottlesInStock = bottlesInStock,
                                gramsPerTube = gramsPerTube,
                                tubesInStock = tubesInStock,
                                vialsInStock = vialsInStock,
                                otherQuantity = otherQuantity,
                                otherUnit = otherUnit,
                                purchasePriceUnit = purchasePriceUnit,
                                salePriceUnit = salePriceUnit,
                                lowStockThreshold = lowStockThreshold,
                                targetMedicineId = targetMedicineId
                            )
                            database.pharmacyDao().insertPendingMedicine(pending)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    try {
                        val localPending = repository.allPendingMedicines.first()
                        for (local in localPending) {
                            if (!remoteIds.contains(local.id)) {
                                try {
                                    getFirebaseRef().child("pending_medicines").child(local.id.toString()).setValue(local)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {
                isOnlineSyncConnected.value = false
            }
        })

        // 6. Sync Pending Stock Ins
        db.child("pending_stock_ins").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                viewModelScope.launch {
                    val remoteIds = mutableSetOf<Long>()
                    for (child in snapshot.children) {
                        try {
                            val id = child.key?.toLongOrNull() ?: continue
                            remoteIds.add(id)
                            val medicineId = child.child("medicineId").getValue(Long::class.java) ?: 0L
                            val medicineName = child.child("medicineName").getValue(String::class.java) ?: ""
                            val medicineType = child.child("medicineType").getValue(String::class.java) ?: "other"
                            val timestamp = child.child("timestamp").getValue(Long::class.java) ?: 0L
                            val addedBoxes = child.child("addedBoxes").getValue(Int::class.java) ?: 0
                            val addedStrips = child.child("addedStrips").getValue(Int::class.java) ?: 0
                            val addedPcs = child.child("addedPcs").getValue(Int::class.java) ?: 0
                            val addedBottles = child.child("addedBottles").getValue(Int::class.java) ?: 0
                            val addedTubes = child.child("addedTubes").getValue(Int::class.java) ?: 0
                            val addedVials = child.child("addedVials").getValue(Int::class.java) ?: 0
                            val addedOtherQty = child.child("addedOtherQty").getValue(Double::class.java) ?: 0.0
                            val purchasePrice = child.child("purchasePrice").getValue(Double::class.java)
                            val salePrice = child.child("salePrice").getValue(Double::class.java)
                            val staffUsername = child.child("staffUsername").getValue(String::class.java) ?: ""

                            val pendingStockIn = PendingStockIn(
                                id = id,
                                medicineId = medicineId,
                                medicineName = medicineName,
                                medicineType = medicineType,
                                timestamp = timestamp,
                                addedBoxes = addedBoxes,
                                addedStrips = addedStrips,
                                addedPcs = addedPcs,
                                addedBottles = addedBottles,
                                addedTubes = addedTubes,
                                addedVials = addedVials,
                                addedOtherQty = addedOtherQty,
                                purchasePrice = purchasePrice,
                                salePrice = salePrice,
                                staffUsername = staffUsername
                            )
                            database.pharmacyDao().insertPendingStockIn(pendingStockIn)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    try {
                        val localPendingStockIns = repository.allPendingStockIns.first()
                        for (local in localPendingStockIns) {
                            if (!remoteIds.contains(local.id)) {
                                try {
                                    getFirebaseRef().child("pending_stock_ins").child(local.id.toString()).setValue(local)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {
                isOnlineSyncConnected.value = false
            }
        })

        // 7. Sync Customer Orders
        db.child("customer_orders").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                viewModelScope.launch {
                    val remoteIds = mutableSetOf<Long>()
                    for (child in snapshot.children) {
                        try {
                            val id = child.key?.toLongOrNull() ?: continue
                            remoteIds.add(id)
                            val timestamp = child.child("timestamp").getValue(Long::class.java) ?: 0L
                            val customerUsername = child.child("customerUsername").getValue(String::class.java) ?: ""
                            val medicineId = child.child("medicineId").getValue(Long::class.java) ?: 0L
                            val medicineName = child.child("medicineName").getValue(String::class.java) ?: ""
                            val medicineType = child.child("medicineType").getValue(String::class.java) ?: "other"
                            val quantity = child.child("quantity").getValue(Int::class.java) ?: 0
                            val totalPrice = child.child("totalPrice").getValue(Double::class.java) ?: 0.0
                            val status = child.child("status").getValue(String::class.java) ?: "Pending"
                            val pharmacyName = child.child("pharmacyName").getValue(String::class.java) ?: ""
                            val dateString = child.child("dateString").getValue(String::class.java) ?: ""

                            val order = CustomerOrder(
                                id = id,
                                timestamp = timestamp,
                                customerUsername = customerUsername,
                                medicineId = medicineId,
                                medicineName = medicineName,
                                medicineType = medicineType,
                                quantity = quantity,
                                totalPrice = totalPrice,
                                status = status,
                                pharmacyName = pharmacyName,
                                dateString = dateString
                            )
                            database.pharmacyDao().insertCustomerOrder(order)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    try {
                        repository.getAllCustomerOrders().first().forEach { local ->
                            if (!remoteIds.contains(local.id)) {
                                try {
                                    getFirebaseRef().child("customer_orders").child(local.id.toString()).setValue(local)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {
                isOnlineSyncConnected.value = false
            }
        })

        // 8. Sync Permission Requests
        db.child("permission_requests").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<PermissionRequest>()
                for (child in snapshot.children) {
                    try {
                        val id = child.child("id").getValue(String::class.java) ?: child.key ?: continue
                        val staffUsername = child.child("staffUsername").getValue(String::class.java) ?: ""
                        val staffPhone = child.child("staffPhone").getValue(String::class.java) ?: ""
                        val adminUsername = child.child("adminUsername").getValue(String::class.java) ?: ""
                        val pharmacyName = child.child("pharmacyName").getValue(String::class.java) ?: ""
                        val ownerName = child.child("ownerName").getValue(String::class.java) ?: ""
                        val securityCode = child.child("securityCode").getValue(String::class.java) ?: ""
                        val status = child.child("status").getValue(String::class.java) ?: "Pending"
                        val timestamp = child.child("timestamp").getValue(Long::class.java) ?: 0L

                        list.add(PermissionRequest(
                            id = id,
                            staffUsername = staffUsername,
                            staffPhone = staffPhone,
                            adminUsername = adminUsername,
                            pharmacyName = pharmacyName,
                            ownerName = ownerName,
                            securityCode = securityCode,
                            status = status,
                            timestamp = timestamp
                        ))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                _permissionRequests.value = list.sortedByDescending { it.timestamp }
            }
            override fun onCancelled(error: DatabaseError) {
                isOnlineSyncConnected.value = false
            }
        })
    }

    fun syncAllDataToCloud(onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val ref = getFirebaseRef()
                
                // 1. Upload all user profiles
                val profiles = _userProfiles.value
                profiles.forEach { (_, profile) ->
                    ref.child("users").child(profile.username).setValue(profile)
                }
                
                // 2. Upload all medicines
                val meds = repository.allMedicines.first()
                meds.forEach { med ->
                    ref.child("medicines").child(med.id.toString()).setValue(med)
                }
                
                // 3. Upload all bills
                val bills = repository.allBills.first()
                bills.forEach { bill ->
                    ref.child("bills").child(bill.billNumber).setValue(bill)
                }
                
                // 4. Upload all stock records
                val records = repository.allStockInRecords.first()
                records.forEach { rec ->
                    ref.child("stock_in_records").child(rec.id.toString()).setValue(rec)
                }
                
                // 5. Upload all customer orders
                val orders = repository.getAllCustomerOrders().first()
                orders.forEach { ord ->
                    ref.child("customer_orders").child(ord.id.toString()).setValue(ord)
                }
                
                isOnlineSyncConnected.value = true
                firebaseSyncMessage.value = "সব ডাটা ক্লাউডে সুরক্ষিত করা হয়েছে"
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onComplete(true, "সব ওষুধ, বিল ও মার্চেন্ট ডাটা ক্লাউডে সিঙ্ক করা হয়েছে!")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                isOnlineSyncConnected.value = false
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onComplete(false, "ক্লাউড সিঙ্ক ব্যর্থ: ${e.message ?: "ত্রুটি"}")
                }
            }
        }
    }

    fun getOwnerUsername(user: UserProfile): String {
        return if (user.role == "staff") {
            user.linkedAdminUsername
        } else {
            user.username
        }
    }

    fun connectStaffToPharmacy(
        pharmName: String,
        secCode: String,
        onResult: (success: Boolean, message: String) -> Unit
    ) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val staffUser = currentUser.value
            if (staffUser.role != "staff") {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onResult(false, if (isBengali.value) "শুধুমাত্র স্টাফ ফার্মেসীতে যুক্ত হতে পারেন।" else "Only staff can link to a pharmacy.")
                }
                return@launch
            }

            val cleanPharmName = pharmName.trim()
            val cleanSecCode = secCode.trim().uppercase()

            if (cleanPharmName.isBlank()) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onResult(false, if (isBengali.value) "অনুগ্রহ করে ফার্মেসীর নাম লিখুন।" else "Please enter pharmacy name.")
                }
                return@launch
            }

            if (cleanSecCode.isBlank()) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onResult(false, if (isBengali.value) "অনুগ্রহ করে সিকিউরিটি কোড লিখুন।" else "Please enter security code.")
                }
                return@launch
            }

            val normInputCode = cleanSecCode.replace("#", "").replace("-", "").replace(" ", "")
            val normInputName = cleanPharmName.lowercase()

            val matchingAdmin = userProfiles.value.values.find { profile ->
                (profile.role == "admin" || profile.role == "merchant") && run {
                    val normProfCode = profile.securityCode.trim().uppercase().replace("#", "").replace("-", "").replace(" ", "")
                    val normProfName = profile.pharmacyName.trim().lowercase()

                    val codeMatches = normInputCode.isNotEmpty() && (normProfCode == normInputCode || normProfCode.endsWith(normInputCode) || normInputCode.endsWith(normProfCode))
                    val nameMatches = normInputName.isNotEmpty() && (
                        normProfName == normInputName ||
                        normProfName.contains(normInputName) ||
                        normInputName.contains(normProfName)
                    )
                    codeMatches && nameMatches
                }
            }

            if (matchingAdmin == null) {
                val nameOnlyMatch = userProfiles.value.values.find { profile ->
                    (profile.role == "admin" || profile.role == "merchant") && run {
                        val normProfName = profile.pharmacyName.trim().lowercase()
                        normInputName.isNotEmpty() && (normProfName == normInputName || normProfName.contains(normInputName) || normInputName.contains(normProfName))
                    }
                }

                val codeOnlyMatch = userProfiles.value.values.find { profile ->
                    (profile.role == "admin" || profile.role == "merchant") && run {
                        val normProfCode = profile.securityCode.trim().uppercase().replace("#", "").replace("-", "").replace(" ", "")
                        normInputCode.isNotEmpty() && (normProfCode == normInputCode || normProfCode.endsWith(normInputCode) || normInputCode.endsWith(normProfCode))
                    }
                }

                val errorMsg = when {
                    nameOnlyMatch != null -> if (isBengali.value) "ফার্মেসীর নাম পাওয়া গেছে কিন্তু সিকিউরিটি কোড মেলেনি! সঠিক সিকিউরিটি কোড দিন।" else "Pharmacy found but security code did not match!"
                    codeOnlyMatch != null -> if (isBengali.value) "সিকিউরিটি কোড মিলেছে কিন্তু ফার্মেসীর নাম মেলেনি! ফার্মেসীর সঠিক নাম দিন।" else "Security code matched but pharmacy name differed!"
                    else -> if (isBengali.value) "উক্ত নাম ও সিকিউরিটি কোডের কোনো ফার্মেসী পাওয়া যায়নি!" else "No pharmacy found matching this name and security code."
                }

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onResult(false, errorMsg)
                }
                return@launch
            }

            // Successfully matched! Grant full access to this pharmacy immediately!
            val updatedStaff = staffUser.copy(
                linkedAdminUsername = matchingAdmin.username,
                pharmacyName = matchingAdmin.pharmacyName,
                address = matchingAdmin.address,
                latitude = matchingAdmin.latitude,
                longitude = matchingAdmin.longitude
            )
            val profs = _userProfiles.value.toMutableMap()
            profs[staffUser.username] = updatedStaff
            _userProfiles.value = profs
            saveProfiles(profs)
            pushProfileToFirebase(updatedStaff)
            _currentUser.value = updatedStaff
            pharmacyName.value = matchingAdmin.pharmacyName
            pharmacyAddress.value = matchingAdmin.address
            pharmacyPhone.value = matchingAdmin.phone

            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                val msg = if (isBengali.value) "সফলভাবে '${matchingAdmin.pharmacyName}' এর সাথে যুক্ত হয়েছেন এবং পূর্ণ এক্সেস পেয়েছেন।" else "Successfully connected to '${matchingAdmin.pharmacyName}' with full access."
                onResult(true, msg)
            }
        }
    }

    fun sendPermissionRequest(
        pharmName: String,
        ownName: String,
        secCode: String,
        onResult: (success: Boolean, message: String) -> Unit
    ) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val staffUser = currentUser.value
            if (staffUser.role != "staff") {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onResult(false, if (isBengali.value) "শুধুমাত্র স্টাফ ফার্মেসীতে যুক্ত হতে পারেন।" else "Only staff can request permission.")
                }
                return@launch
            }

            if (staffUser.linkedAdminUsername.trim().isNotEmpty()) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    val msg = if (isBengali.value) {
                        "আপনি ইতিমধ্যে একটি ফার্মেসীর সাথে যুক্ত আছেন! অন্য ফার্মেসীর সাথে নতুন সংযোগ করা সম্ভব নয়।"
                    } else {
                        "You are already linked to a pharmacy! Connecting to another one is not allowed."
                    }
                    onResult(false, msg)
                }
                return@launch
            }

            val cleanSecCode = secCode.trim().uppercase()
            val cleanPharmName = pharmName.trim().lowercase()
            val cleanOwnName = ownName.trim().lowercase()

            // Find matching admin / merchant
            val matchingAdmin = userProfiles.value.values.find { profile ->
                (profile.role == "admin" || profile.role == "merchant") &&
                (
                    (cleanSecCode.isNotEmpty() && (profile.securityCode.trim().uppercase() == cleanSecCode || profile.securityCode.trim().uppercase().replace("#", "") == cleanSecCode.replace("#", ""))) ||
                    (cleanPharmName.isNotEmpty() && profile.pharmacyName.lowercase().trim() == cleanPharmName) ||
                    (cleanOwnName.isNotEmpty() && (profile.username.lowercase().trim() == cleanOwnName || profile.ownerName.lowercase().trim() == cleanOwnName))
                )
            }

            if (matchingAdmin == null) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    val msg = if (isBengali.value) "এই তথ্য বা সিকিউরিটি কোডের কোনো ফার্মেসী পাওয়া যায়নি!" else "No matching pharmacy found with this info or security code."
                    onResult(false, msg)
                }
                return@launch
            }

            // If security code matches exactly or matches normalized, link instantly!
            val isCodeMatched = cleanSecCode.isNotEmpty() && (matchingAdmin.securityCode.trim().uppercase() == cleanSecCode || matchingAdmin.securityCode.trim().uppercase().replace("#", "") == cleanSecCode.replace("#", ""))
            if (isCodeMatched) {
                val updatedStaff = staffUser.copy(
                    linkedAdminUsername = matchingAdmin.username,
                    pharmacyName = matchingAdmin.pharmacyName,
                    address = matchingAdmin.address
                )
                val profs = _userProfiles.value.toMutableMap()
                profs[staffUser.username] = updatedStaff
                _userProfiles.value = profs
                saveProfiles(profs)
                pushProfileToFirebase(updatedStaff)
                _currentUser.value = updatedStaff
                pharmacyName.value = matchingAdmin.pharmacyName
                pharmacyAddress.value = matchingAdmin.address
                pharmacyPhone.value = matchingAdmin.phone

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    val msg = if (isBengali.value) "সঠিক সিকিউরিটি কোড! ফার্মেসী '${matchingAdmin.pharmacyName}' এর সাথে সফলভাবে যুক্ত হয়েছেন।" else "Security code verified! Successfully linked to '${matchingAdmin.pharmacyName}'."
                    onResult(true, msg)
                }
                return@launch
            }

            // Otherwise, create a pending permission request for merchant to approve
            val requestId = getFirebaseRef().child("permission_requests").push().key ?: generateUniqueId().toString()
            val request = PermissionRequest(
                id = requestId,
                staffUsername = staffUser.username,
                staffPhone = staffUser.phone,
                adminUsername = matchingAdmin.username,
                pharmacyName = matchingAdmin.pharmacyName,
                ownerName = matchingAdmin.ownerName,
                securityCode = secCode,
                status = "Pending"
            )

            try {
                getFirebaseRef().child("permission_requests").child(requestId).setValue(request)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    val msg = if (isBengali.value) "অনুমতির আবেদন সফলভাবে পাঠানো হয়েছে। মার্চেন্ট অনুমোদন দিলে যুক্ত হবেন।" else "Permission request submitted successfully. Waiting for merchant approval."
                    onResult(true, msg)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onResult(false, "Error: ${e.localizedMessage}")
                }
            }
        }
    }

    fun approvePermissionRequest(request: PermissionRequest, onComplete: () -> Unit = {}) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val staffProfile = userProfiles.value[request.staffUsername]
                val adminProfile = userProfiles.value[request.adminUsername]

                if (staffProfile != null && staffProfile.linkedAdminUsername.trim().isNotEmpty()) {
                    // Staff already linked to another admin, block this approval and auto reject
                    val rejectedRequest = request.copy(status = "Rejected")
                    getFirebaseRef().child("permission_requests").child(request.id).setValue(rejectedRequest)
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        onComplete()
                    }
                    return@launch
                }

                // 1. Mark request as Approved
                val approvedRequest = request.copy(status = "Approved")
                getFirebaseRef().child("permission_requests").child(request.id).setValue(approvedRequest)

                // 2. Load the staff profile from remote or local userProfiles
                if (staffProfile != null && adminProfile != null) {
                    val updatedStaff = staffProfile.copy(
                        linkedAdminUsername = adminProfile.username,
                        pharmacyName = adminProfile.pharmacyName,
                        address = adminProfile.address,
                        phone = adminProfile.phone
                    )
                    // Push updated staff profile to Firebase
                    pushProfileToFirebase(updatedStaff)
                }
                
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onComplete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onComplete()
                }
            }
        }
    }

    fun rejectPermissionRequest(request: PermissionRequest, onComplete: () -> Unit = {}) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val rejectedRequest = request.copy(status = "Rejected")
                getFirebaseRef().child("permission_requests").child(request.id).setValue(rejectedRequest)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onComplete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onComplete()
                }
            }
        }
    }

    fun disconnectStaff(staffUsername: String, onComplete: () -> Unit = {}) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val staffProfile = userProfiles.value[staffUsername]
                if (staffProfile != null) {
                    val updatedStaff = staffProfile.copy(
                        linkedAdminUsername = "",
                        pharmacyName = "",
                        address = "",
                        latitude = 0.0,
                        longitude = 0.0
                    )
                    val profs = _userProfiles.value.toMutableMap()
                    profs[staffUsername] = updatedStaff
                    _userProfiles.value = profs
                    saveProfiles(profs)
                    pushProfileToFirebase(updatedStaff)

                    if (_currentUser.value.username == staffUsername) {
                        _currentUser.value = updatedStaff
                        pharmacyName.value = ""
                        pharmacyAddress.value = ""
                    }

                    // Also remove approved permission requests for this staff so they can request again
                    val requests = permissionRequests.value.filter { it.staffUsername == staffUsername }
                    requests.forEach { req ->
                        getFirebaseRef().child("permission_requests").child(req.id).removeValue()
                    }
                }
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onComplete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onComplete()
                }
            }
        }
    }

    // Auth State
    private val _isLoggedIn = MutableStateFlow(
        getSavedCurrentUsername() != null && _userProfiles.value.containsKey(getSavedCurrentUsername())
    )
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    // Pharmacy Profile Settings
    val pharmacyName = MutableStateFlow(_currentUser.value.pharmacyName)
    val pharmacyAddress = MutableStateFlow(_currentUser.value.address)
    val pharmacyPhone = MutableStateFlow(_currentUser.value.phone)
    val ownerName = MutableStateFlow(_currentUser.value.ownerName)
    val ownerPassword = MutableStateFlow(_currentUser.value.pass)
    val pharmacyOpenTime = MutableStateFlow(sharedPrefs.getString("pharmacy_open_time", _currentUser.value.openTime) ?: _currentUser.value.openTime)
    val pharmacyCloseTime = MutableStateFlow(sharedPrefs.getString("pharmacy_close_time", _currentUser.value.closeTime) ?: _currentUser.value.closeTime)
    val mapLatitude = MutableStateFlow(24.9015) // Default Amberkhana Sylhet
    val mapLongitude = MutableStateFlow(91.8683)

    init {
        setupFirebaseSync()
    }

    fun deleteAllAccounts(onComplete: () -> Unit = {}) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            sharedPrefs.edit()
                .remove("saved_user_profiles")
                .remove("logged_in_username")
                .apply()
            _userProfiles.value = emptyMap()
            _isLoggedIn.value = false
            _currentUser.value = UserProfile(
                username = "user",
                pharmacyName = "",
                address = "",
                phone = "",
                ownerName = "ইউজার",
                pass = "",
                role = "customer"
            )
            try {
                getFirebaseRef().child("users").removeValue()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                onComplete()
            }
        }
    }

    private fun applyLoginProfile(profile: UserProfile) {
        val updatedMap = _userProfiles.value.toMutableMap()
        updatedMap[profile.username] = profile
        _userProfiles.value = updatedMap
        saveProfiles(updatedMap)
        pushProfileToFirebase(profile)

        _currentUser.value = profile
        pharmacyName.value = profile.pharmacyName
        pharmacyAddress.value = profile.address
        pharmacyPhone.value = profile.phone
        ownerName.value = profile.ownerName
        ownerPassword.value = profile.pass
        pharmacyOpenTime.value = profile.openTime
        pharmacyCloseTime.value = profile.closeTime
        sharedPrefs.edit()
            .putString("pharmacy_open_time", profile.openTime)
            .putString("pharmacy_close_time", profile.closeTime)
            .apply()
        saveCurrentUsername(profile.username)
        _isLoggedIn.value = true
    }

    fun loginWithResult(identifier: String, pass: String): Pair<Boolean, String> {
        val cleanInput = identifier.trim()
        val cleanPass = pass.trim()
        if (cleanInput.isEmpty() || cleanPass.isEmpty()) {
            return Pair(false, if (isBengali.value) "ইউজারনেম, ফার্মেসীর নাম বা ব্যক্তির নাম এবং পাসওয়ার্ড দিন!" else "Please provide username/pharmacy name/person name and password!")
        }

        // Secret Super Admin Portal login check (username: admin, pass: 4321)
        if (cleanInput.equals("admin", ignoreCase = true) && cleanPass == "4321") {
            val superAdminProfile = UserProfile(
                username = "admin",
                pharmacyName = "সিস্টেম মাস্টার কন্ট্রোল",
                address = "সিস্টেম হেডকোয়ার্টার্স",
                phone = "+৮৮০ ১৮০০-০০০০০০",
                ownerName = "সিস্টেম সুপার এডমিন",
                pass = "4321",
                role = "super_admin",
                portalTrack = "merchant",
                openTime = "00:00 AM",
                closeTime = "11:59 PM"
            )
            _currentUser.value = superAdminProfile
            saveCurrentUsername("admin")
            _isLoggedIn.value = true
            return Pair(true, "")
        }

        val all = _userProfiles.value.values.toList()

        // 1. Check match by Pharmacy Name (ফার্মেসীর নাম) -> Merchant Portal
        val pharmMatch = all.firstOrNull { profile ->
            profile.pharmacyName.isNotBlank() &&
            profile.pharmacyName.trim().equals(cleanInput, ignoreCase = true) &&
            profile.pass == cleanPass
        }
        if (pharmMatch != null) {
            if (pharmMatch.portalTrack == "staff") {
                val msg = if (isBengali.value)
                    "এই একাউন্টটি স্টাফ ট্র্যাকে আছে, ফার্মেসীর নাম দিয়ে মার্চেন্ট পোর্টালে লগইন করা যাবে না।"
                else
                    "This account is on Staff track and cannot login to Merchant portal."
                return Pair(false, msg)
            }
            val updated = pharmMatch.copy(role = "admin", portalTrack = "merchant")
            applyLoginProfile(updated)
            return Pair(true, "")
        }

        // 2. Check match by Person Name (ব্যক্তির নাম / ownerName) -> Staff Portal
        val personMatch = all.firstOrNull { profile ->
            profile.ownerName.isNotBlank() &&
            profile.ownerName.trim().equals(cleanInput, ignoreCase = true) &&
            profile.pass == cleanPass
        }
        if (personMatch != null) {
            if (personMatch.portalTrack == "merchant") {
                val msg = if (isBengali.value)
                    "এই একাউন্টটি মার্চেন্ট ট্র্যাকে আছে, ব্যক্তির নাম দিয়ে স্টাফ পোর্টালে লগইন করা যাবে না।"
                else
                    "This account is on Merchant track and cannot login to Staff portal."
                return Pair(false, msg)
            }
            val updated = personMatch.copy(role = "staff", portalTrack = "staff")
            applyLoginProfile(updated)
            return Pair(true, "")
        }

        // 3. Check match by Username (ইউজার নেম) -> Consumer Portal
        val userMatch = all.firstOrNull { profile ->
            profile.username.trim().equals(cleanInput, ignoreCase = true) &&
            profile.pass == cleanPass
        }
        if (userMatch != null) {
            val updated = userMatch.copy(role = "customer")
            applyLoginProfile(updated)
            return Pair(true, "")
        }

        // 4. Fallback if pharmacy name or owner name was identical to username
        val fallbackPharm = all.firstOrNull { profile ->
            profile.pharmacyName.isNotBlank() &&
            profile.pharmacyName.trim().equals(cleanInput, ignoreCase = true) &&
            profile.pass == cleanPass &&
            profile.portalTrack == "merchant"
        }
        if (fallbackPharm != null) {
            val updated = fallbackPharm.copy(role = "admin", portalTrack = "merchant")
            applyLoginProfile(updated)
            return Pair(true, "")
        }

        val failMsg = if (isBengali.value)
            "ভুল ইউজারনেম, ফার্মেসীর নাম বা ব্যক্তির নাম অথবা পাসওয়ার্ড!"
        else
            "Incorrect username, pharmacy name, person name, or password!"
        return Pair(false, failMsg)
    }

    fun login(username: String, pass: String): Boolean {
        return loginWithResult(username, pass).first
    }

    fun loginAsync(identifier: String, pass: String, onComplete: (Boolean, String) -> Unit) {
        val directResult = loginWithResult(identifier, pass)
        if (directResult.first) {
            onComplete(true, "")
            return
        }

        // Check remote Firebase users in case this is a secondary phone that just launched
        try {
            getFirebaseRef().child("users").addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    val remoteProfiles = mutableMapOf<String, UserProfile>()
                    for (child in snapshot.children) {
                        val username = child.key ?: continue
                        val pharmacyName = child.child("pharmacyName").getValue(String::class.java) ?: ""
                        val address = child.child("address").getValue(String::class.java) ?: ""
                        val phone = child.child("phone").getValue(String::class.java) ?: ""
                        val ownerName = child.child("ownerName").getValue(String::class.java) ?: ""
                        val pPass = child.child("pass").getValue(String::class.java) ?: ""
                        val role = child.child("role").getValue(String::class.java) ?: "customer"
                        val openTime = child.child("openTime").getValue(String::class.java) ?: "09:00 AM"
                        val closeTime = child.child("closeTime").getValue(String::class.java) ?: "11:00 PM"
                        val ward = child.child("ward").getValue(String::class.java) ?: ""
                        val neighborhood = child.child("neighborhood").getValue(String::class.java) ?: ""
                        val latitude = child.child("latitude").getValue(Double::class.java) ?: 24.9015
                        val longitude = child.child("longitude").getValue(Double::class.java) ?: 91.8683
                        val rawSecCode = child.child("securityCode").getValue(String::class.java)?.trim() ?: ""
                        val linkedAdmin = child.child("linkedAdminUsername").getValue(String::class.java) ?: ""
                        val portalTrack = child.child("portalTrack").getValue(String::class.java) ?: ""
                        remoteProfiles[username] = UserProfile(
                            username = username,
                            pharmacyName = pharmacyName,
                            address = address,
                            phone = phone,
                            ownerName = ownerName,
                            pass = pPass,
                            role = role,
                            openTime = openTime,
                            closeTime = closeTime,
                            ward = ward,
                            neighborhood = neighborhood,
                            latitude = latitude,
                            longitude = longitude,
                            securityCode = rawSecCode,
                            linkedAdminUsername = linkedAdmin,
                            portalTrack = portalTrack
                        )
                    }
                    if (remoteProfiles.isNotEmpty()) {
                        val merged = _userProfiles.value.toMutableMap()
                        merged.putAll(remoteProfiles)
                        _userProfiles.value = merged
                        saveProfiles(merged)

                        val retryResult = loginWithResult(identifier, pass)
                        onComplete(retryResult.first, retryResult.second)
                    } else {
                        onComplete(false, directResult.second)
                    }
                }

                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                    onComplete(false, directResult.second)
                }
            })
        } catch (_: Exception) {
            onComplete(false, directResult.second)
        }
    }

    fun deleteUserProfile(targetUsername: String): Boolean {
        val normalized = targetUsername.trim()
        if (normalized.isEmpty()) return false

        val currentProfiles = _userProfiles.value.toMutableMap()
        if (!currentProfiles.containsKey(normalized)) return false

        // Remove profile
        currentProfiles.remove(normalized)

        // Unlink any staff linked to this admin if deleted profile is an admin
        val updatedProfiles = currentProfiles.mapValues { (_, profile) ->
            if (profile.linkedAdminUsername == normalized) {
                profile.copy(linkedAdminUsername = "")
            } else {
                profile
            }
        }

        _userProfiles.value = updatedProfiles
        saveProfiles(updatedProfiles)

        // Remove from Firebase RTDB
        try {
            getFirebaseRef().child("users").child(normalized).removeValue()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        addNotification(
            titleEn = "Account Removed: $normalized",
            titleBn = "একাউন্ট মুছে ফেলা হয়েছে: $normalized",
            messageEn = "User account $normalized was removed from the system.",
            messageBn = "ইউজার একাউন্ট $normalized সিস্টেম থেকে মুছে ফেলা হয়েছে।",
            targetRole = "ALL"
        )
        return true
    }

    fun updateUserProfileBySuperAdmin(
        targetUsername: String,
        newPharmacyName: String,
        newOwnerName: String,
        newPhone: String,
        newPass: String
    ): Boolean {
        val normalized = targetUsername.trim()
        val currentProfiles = _userProfiles.value.toMutableMap()
        val existing = currentProfiles[normalized] ?: return false

        val updated = existing.copy(
            pharmacyName = if (newPharmacyName.isNotBlank()) newPharmacyName.trim() else existing.pharmacyName,
            ownerName = if (newOwnerName.isNotBlank()) newOwnerName.trim() else existing.ownerName,
            phone = if (newPhone.isNotBlank()) newPhone.trim() else existing.phone,
            pass = if (newPass.isNotBlank()) newPass.trim() else existing.pass
        )

        currentProfiles[normalized] = updated
        _userProfiles.value = currentProfiles
        saveProfiles(currentProfiles)
        pushProfileToFirebase(updated)
        return true
    }

    fun generateUniqueSecurityCode(role: String = "merchant"): String {
        val existingCodes = _userProfiles.value.values.map { it.securityCode.uppercase().trim() }.toSet()
        val prefix = when (role) {
            "staff" -> "#ROS-"
            "customer" -> "#ROC-"
            else -> "#ROPH-"
        }
        var code: String
        do {
            val num = (1000..9999).random()
            code = "$prefix$num"
        } while (existingCodes.contains(code) || code == "#ROPH001")
        return code
    }

    fun signUp(username: String, pharmName: String, addr: String, ph: String, ownName: String, pass: String, role: String = "customer"): Boolean {
        val normalized = username.trim()
        if (normalized.isEmpty() || pass.isEmpty()) return false
        if (_userProfiles.value.containsKey(normalized)) return false // Already registered

        val assignedSecCode = generateUniqueSecurityCode(role)
        val profile = UserProfile(
            username = normalized,
            pharmacyName = if (pharmName.trim().isNotEmpty()) pharmName.trim() else (if (role == "customer") "" else "$normalized Pharmacy"),
            address = addr.trim(),
            phone = if (ph.trim().isNotEmpty()) ph.trim() else "+৮৮০ ১৭০০-০০০০০০",
            ownerName = if (ownName.trim().isNotEmpty()) ownName.trim() else normalized,
            pass = pass,
            role = role,
            securityCode = assignedSecCode
        )

        val updated = _userProfiles.value.toMutableMap()
        updated[normalized] = profile
        _userProfiles.value = updated
        saveProfiles(updated)
        pushProfileToFirebase(profile)

        _currentUser.value = profile
        saveCurrentUsername(normalized)

        // Auto logo/info updates
        pharmacyName.value = profile.pharmacyName
        pharmacyAddress.value = profile.address
        pharmacyPhone.value = profile.phone
        ownerName.value = profile.ownerName
        ownerPassword.value = profile.pass
        _isLoggedIn.value = true
        return true
    }

    fun logout() {
        saveCurrentUsername(null)
        _isLoggedIn.value = false
    }

    fun updateProfile(
        name: String, 
        addr: String, 
        phone: String, 
        owner: String, 
        pass: String, 
        openTime: String, 
        closeTime: String, 
        role: String? = null,
        ward: String? = null,
        neighborhood: String? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        securityCode: String? = null
    ) {
        pharmacyName.value = name
        pharmacyAddress.value = addr
        pharmacyPhone.value = phone
        ownerName.value = owner
        if (pass.isNotEmpty()) {
            ownerPassword.value = pass
        }
        pharmacyOpenTime.value = openTime
        pharmacyCloseTime.value = closeTime

        val current = _currentUser.value
        val updatedRole = role ?: current.role
        val finalLat = latitude ?: current.latitude
        val finalLng = longitude ?: current.longitude
        val finalWard = ward ?: current.ward
        val finalNh = neighborhood ?: current.neighborhood

        mapLatitude.value = finalLat
        mapLongitude.value = finalLng
        sharedPrefs.edit()
            .putString("pharmacy_open_time", openTime)
            .putString("pharmacy_close_time", closeTime)
            .apply()

        val finalSecCode = if (!securityCode.isNullOrBlank() && securityCode != "#ROPH001") {
            securityCode
        } else if (current.securityCode.isNotBlank() && current.securityCode != "#ROPH001") {
            current.securityCode
        } else {
            generateUniqueSecurityCode(updatedRole)
        }

        val updatedUser = current.copy(
            pharmacyName = name,
            address = addr,
            phone = phone,
            ownerName = owner,
            pass = if (pass.isNotEmpty()) pass else current.pass,
            openTime = openTime,
            closeTime = closeTime,
            role = updatedRole,
            ward = finalWard,
            neighborhood = finalNh,
            latitude = finalLat,
            longitude = finalLng,
            securityCode = finalSecCode
        )
        _currentUser.value = updatedUser

        val updatedProfiles = _userProfiles.value.toMutableMap()
        updatedProfiles[current.username] = updatedUser
        _userProfiles.value = updatedProfiles
        saveProfiles(updatedProfiles)
        pushProfileToFirebase(updatedUser)
    }

    fun quickEnterPortal(portalRole: String) {
        val targetRole = when (portalRole.lowercase()) {
            "customer", "user" -> "customer"
            "staff" -> "staff"
            else -> "admin"
        }
        val defaultUser = defaultProfiles().values.firstOrNull { it.role == targetRole }
            ?: UserProfile(
                username = if (targetRole == "customer") "customer" else if (targetRole == "staff") "staff" else "merchant",
                pharmacyName = if (targetRole == "customer") "" else "নিকট ফার্মেসী",
                address = "সিলেট সিটি",
                phone = "+৮৮০ ১৭০০-০০০০০০",
                ownerName = if (targetRole == "customer") "গ্রাহক ইউজার" else if (targetRole == "staff") "স্টাফ সদস্য" else "ফার্মেসী মার্চেন্ট",
                pass = "1234",
                role = targetRole,
                securityCode = generateUniqueSecurityCode(targetRole)
            )
        val profile = _userProfiles.value.values.firstOrNull { it.role == targetRole } ?: defaultUser
        val updated = _userProfiles.value.toMutableMap()
        updated[profile.username] = profile
        _userProfiles.value = updated
        saveProfiles(updated)

        _currentUser.value = profile
        pharmacyName.value = profile.pharmacyName
        pharmacyAddress.value = profile.address
        pharmacyPhone.value = profile.phone
        ownerName.value = profile.ownerName
        ownerPassword.value = profile.pass
        saveCurrentUsername(profile.username)
        _isLoggedIn.value = true
    }

    fun canSwitchToPortal(targetRole: String, user: UserProfile = _currentUser.value): Pair<Boolean, String> {
        val target = when(targetRole.lowercase()) {
            "customer", "user" -> "customer"
            "staff" -> "staff"
            else -> "admin"
        }
        val effectiveTrack = when {
            user.portalTrack.isNotBlank() -> user.portalTrack
            user.role in listOf("admin", "merchant") -> "merchant"
            user.role == "staff" -> "staff"
            else -> ""
        }

        if (effectiveTrack == "merchant" && target == "staff") {
            val msg = if (isBengali.value)
                "কনজিউমার পোর্টাল থেকে মার্চেন্ট পোর্টালে পরীবর্তন হওয়ায় এই একাউন্ট স্টাফ পোর্টালে কোন ভাবেই যেতে পারবে না।"
            else
                "Merchant accounts cannot switch to Staff Portal."
            return Pair(false, msg)
        }

        if (effectiveTrack == "staff" && (target == "admin" || target == "merchant")) {
            val msg = if (isBengali.value)
                "কনজিউমার পোর্টাল থেকে স্টাফ পোর্টাল পরীবর্তন হওয়ায় এই একাউন্ট মার্চেন্ট একাউন্ট ক্রিয়েট করতে পারবে না।"
            else
                "Staff accounts cannot create or switch to Merchant Portal."
            return Pair(false, msg)
        }

        return Pair(true, "")
    }

    fun switchPortalDirectly(targetRole: String, customPharmacyName: String = ""): Boolean {
        val target = when(targetRole.lowercase()) {
            "customer", "user" -> "customer"
            "staff" -> "staff"
            else -> "admin"
        }

        val curr = _currentUser.value
        val (canSwitch, _) = canSwitchToPortal(target, curr)
        if (!canSwitch) {
            return false
        }

        val newTrack = when {
            target == "admin" -> "merchant"
            target == "staff" -> "staff"
            else -> curr.portalTrack
        }

        val newPharmName = if (target == "admin") {
            if (customPharmacyName.isNotBlank()) customPharmacyName else curr.pharmacyName.ifBlank { "নিকট ফার্মেসী" }
        } else {
            curr.pharmacyName
        }

        val finalProfile = curr.copy(
            role = target,
            portalTrack = newTrack,
            pharmacyName = newPharmName,
            securityCode = if (curr.securityCode.isNotBlank()) curr.securityCode else generateUniqueSecurityCode(target)
        )

        val updated = _userProfiles.value.toMutableMap()
        updated[finalProfile.username] = finalProfile
        _userProfiles.value = updated
        saveProfiles(updated)
        pushProfileToFirebase(finalProfile)

        _currentUser.value = finalProfile
        pharmacyName.value = finalProfile.pharmacyName
        pharmacyAddress.value = finalProfile.address
        pharmacyPhone.value = finalProfile.phone
        ownerName.value = finalProfile.ownerName
        ownerPassword.value = finalProfile.pass
        saveCurrentUsername(finalProfile.username)
        _isLoggedIn.value = true
        return true
    }

    // Direct Staff Member addition by Merchant
    fun addStaffMember(
        name: String,
        username: String,
        phone: String,
        pass: String,
        customSecurityCode: String? = null,
        onResult: (Boolean, String) -> Unit
    ) {
        val adminUser = _currentUser.value
        if (!isMerchant(adminUser)) {
            onResult(false, if (isBengali.value) "শুধুমাত্র মার্চেন্ট স্টাফ যুক্ত করতে পারেন!" else "Only merchants can add staff!")
            return
        }

        val normalizedUsername = username.trim().lowercase()
        if (normalizedUsername.isEmpty() || pass.isEmpty() || name.trim().isEmpty()) {
            onResult(false, if (isBengali.value) "নাম, ইউজারনেম ও পাসওয়ার্ড আবশ্যক!" else "Name, username, and password are required!")
            return
        }

        val existing = _userProfiles.value[normalizedUsername]
        if (existing != null) {
            if (existing.role == "staff" && existing.linkedAdminUsername.isNotEmpty() && existing.linkedAdminUsername != adminUser.username) {
                onResult(false, if (isBengali.value) "এই ইউজারনেম ইতিমধ্যে অন্য ফার্মেসীর সাথে যুক্ত!" else "This username is already linked to another pharmacy!")
                return
            }
            // Link existing user/staff to this pharmacy
            val updatedStaff = existing.copy(
                ownerName = name.trim().ifBlank { existing.ownerName },
                phone = phone.trim().ifBlank { existing.phone },
                pass = if (pass.isNotBlank()) pass else existing.pass,
                role = "staff",
                linkedAdminUsername = adminUser.username,
                pharmacyName = adminUser.pharmacyName,
                address = adminUser.address,
                securityCode = if (existing.securityCode.isNotBlank() && existing.securityCode != "#ROPH001") existing.securityCode else (customSecurityCode ?: generateUniqueSecurityCode("staff"))
            )
            val profiles = _userProfiles.value.toMutableMap()
            profiles[normalizedUsername] = updatedStaff
            _userProfiles.value = profiles
            saveProfiles(profiles)
            pushProfileToFirebase(updatedStaff)
            onResult(true, if (isBengali.value) "স্টাফ সদস্য সফলভাবে ফার্মেসীর সাথে যুক্ত হয়েছেন!" else "Staff member linked successfully!")
            return
        }

        // Create new staff profile
        val staffSecCode = customSecurityCode?.takeIf { it.isNotBlank() } ?: generateUniqueSecurityCode("staff")
        val newStaffProfile = UserProfile(
            username = normalizedUsername,
            pharmacyName = adminUser.pharmacyName,
            address = adminUser.address,
            phone = phone.trim().ifBlank { adminUser.phone },
            ownerName = name.trim(),
            pass = pass,
            role = "staff",
            linkedAdminUsername = adminUser.username,
            securityCode = staffSecCode
        )
        val profiles = _userProfiles.value.toMutableMap()
        profiles[normalizedUsername] = newStaffProfile
        _userProfiles.value = profiles
        saveProfiles(profiles)
        pushProfileToFirebase(newStaffProfile)
        onResult(true, if (isBengali.value) "নতুন স্টাফ সফলভাবে তৈরি ও যুক্ত করা হয়েছে!" else "New staff created and linked successfully!")
    }

    fun updateCustomerLocation(
        addr: String,
        ward: String = "",
        neighborhood: String = "",
        lat: Double = 0.0,
        lon: Double = 0.0
    ) {
        val current = _currentUser.value
        val updatedUser = current.copy(
            address = addr.trim(),
            ward = ward,
            neighborhood = neighborhood,
            latitude = lat,
            longitude = lon
        )
        _currentUser.value = updatedUser
        val updatedProfiles = _userProfiles.value.toMutableMap()
        updatedProfiles[current.username] = updatedUser
        _userProfiles.value = updatedProfiles
        saveProfiles(updatedProfiles)
        pushProfileToFirebase(updatedUser)
    }

    fun clearCustomerLocation() {
        updateCustomerLocation(addr = "", ward = "", neighborhood = "", lat = 0.0, lon = 0.0)
    }

    fun updateLatLng(lat: Double, lng: Double) {
        mapLatitude.value = lat
        mapLongitude.value = lng
    }

    // Medicine List State
    val allMedicines = repository.allMedicines.combine(currentUser) { list, user ->
        if (user.role == "customer") {
            list
        } else if (user.role == "staff" && user.linkedAdminUsername.isBlank()) {
            emptyList()
        } else {
            val ownerUsername = getOwnerUsername(user)
            list.filter { it.pharmacyOwnerUsername == ownerUsername }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Bill List State
    val allBills = repository.allBills.combine(currentUser) { list, user ->
        if (user.role == "customer") {
            list
        } else if (user.role == "staff") {
            if (user.linkedAdminUsername.isBlank()) {
                emptyList()
            } else {
                val ownerUsername = getOwnerUsername(user)
                list.filter { it.pharmacyOwnerUsername == ownerUsername && it.staffUsername == user.username }
            }
        } else {
            val ownerUsername = getOwnerUsername(user)
            list.filter { it.pharmacyOwnerUsername == ownerUsername }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Today's Bills State
    val todayBills = repository.getBillsForToday().combine(currentUser) { list, user ->
        if (user.role == "customer") {
            list
        } else if (user.role == "staff") {
            if (user.linkedAdminUsername.isBlank()) {
                emptyList()
            } else {
                val ownerUsername = getOwnerUsername(user)
                list.filter { it.pharmacyOwnerUsername == ownerUsername && it.staffUsername == user.username }
            }
        } else {
            val ownerUsername = getOwnerUsername(user)
            list.filter { it.pharmacyOwnerUsername == ownerUsername }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Stock In History State
    val allStockInRecords = repository.allStockInRecords.combine(currentUser) { list, user ->
        if (user.role == "customer") {
            list
        } else if (user.role == "staff" && user.linkedAdminUsername.isBlank()) {
            emptyList()
        } else {
            val ownerUsername = getOwnerUsername(user)
            list.filter { it.pharmacyOwnerUsername == ownerUsername }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Pending Stock In State Flow
    val allPendingStockIns = repository.allPendingStockIns.combine(currentUser) { list, user ->
        if (user.role == "customer") {
            list
        } else if (user.role == "staff" && user.linkedAdminUsername.isBlank()) {
            emptyList()
        } else {
            list
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Pending Medicine State Flow
    val allPendingMedicines = repository.allPendingMedicines.combine(currentUser) { list, user ->
        if (user.role == "customer") {
            list
        } else if (user.role == "staff" && user.linkedAdminUsername.isBlank()) {
            emptyList()
        } else {
            list
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Customer Orders State Flow
    val allCustomerOrders = repository.getAllCustomerOrders().combine(currentUser) { list, user ->
        if (user.role == "customer") {
            list.filter { it.customerUsername == user.username }
        } else if (user.role == "staff" && user.linkedAdminUsername.isBlank()) {
            emptyList()
        } else {
            list.filter { it.pharmacyName == user.pharmacyName }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Notifications State Flow
    val notifications = repository.allNotifications.combine(currentUser) { list, user ->
        list.filter { notif ->
            val roleMatches = notif.targetRole.equals("all", ignoreCase = true) ||
                    notif.targetRole.equals(user.role, ignoreCase = true) ||
                    (isMerchant(user) && (notif.targetRole.equals("admin", ignoreCase = true) || notif.targetRole.equals("merchant", ignoreCase = true)))
            roleMatches && (notif.targetUsername.isEmpty() || notif.targetUsername == user.username)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun addNotification(titleEn: String, titleBn: String, messageEn: String, messageBn: String, targetRole: String, targetUsername: String = "") {
        viewModelScope.launch {
            repository.insertNotification(
                PharmacyNotification(
                    titleEn = titleEn,
                    titleBn = titleBn,
                    messageEn = messageEn,
                    messageBn = messageBn,
                    timestamp = System.currentTimeMillis(),
                    targetRole = targetRole,
                    targetUsername = targetUsername
                )
            )
        }
    }

    fun markNotificationAsRead(notif: PharmacyNotification) {
        viewModelScope.launch {
            repository.updateNotification(notif.copy(isRead = true))
        }
    }

    fun dismissNotificationFromDashboard(notif: PharmacyNotification) {
        viewModelScope.launch {
            repository.updateNotification(notif.copy(isDismissedFromDashboard = true))
        }
    }

    // Report Filter State: "today", "week", "month"
    private val _reportPeriod = MutableStateFlow("today")
    val reportPeriod: StateFlow<String> = _reportPeriod.asStateFlow()

    // Expiry Alert warning period in months (defaults to 6 months)
    private val _expiryAlertMonths = MutableStateFlow(6)
    val expiryAlertMonths: StateFlow<Int> = _expiryAlertMonths.asStateFlow()

    // Low Stock warning threshold factor (defaults to 1x)
    private val _lowStockAlertFactor = MutableStateFlow(1)
    val lowStockAlertFactor: StateFlow<Int> = _lowStockAlertFactor.asStateFlow()

    fun setExpiryAlertMonths(months: Int) {
        _expiryAlertMonths.value = months
    }

    fun setLowStockAlertFactor(factor: Int) {
        _lowStockAlertFactor.value = factor
    }

    fun setReportPeriod(period: String) {
        _reportPeriod.value = period
    }

    val reportBills: StateFlow<List<Bill>> = combine(allBills, _reportPeriod) { bills, period ->
        val calendar = Calendar.getInstance()
        val now = calendar.timeInMillis
        when (period) {
            "today" -> {
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val start = calendar.timeInMillis
                bills.filter { it.timestamp >= start }
            }
            "week" -> {
                calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                var start = calendar.timeInMillis
                if (start > now) {
                    start -= 7 * 24 * 60 * 60 * 1000L
                }
                bills.filter { it.timestamp >= start }
            }
            "month" -> {
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val start = calendar.timeInMillis
                bills.filter { it.timestamp >= start }
            }
            else -> {
                if (period.startsWith("month_")) {
                    try {
                        val parts = period.split("_")
                        val year = parts[1].toInt()
                        val month = parts[2].toInt() // 1-indexed (1=Jan, 12=Dec)
                        
                        val startCal = Calendar.getInstance().apply {
                            set(Calendar.YEAR, year)
                            set(Calendar.MONTH, month - 1)
                            set(Calendar.DAY_OF_MONTH, 1)
                            set(Calendar.HOUR_OF_DAY, 0)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        
                        val endCal = Calendar.getInstance().apply {
                            set(Calendar.YEAR, year)
                            set(Calendar.MONTH, month - 1)
                            set(Calendar.DAY_OF_MONTH, startCal.getActualMaximum(Calendar.DAY_OF_MONTH))
                            set(Calendar.HOUR_OF_DAY, 23)
                            set(Calendar.MINUTE, 59)
                            set(Calendar.SECOND, 59)
                            set(Calendar.MILLISECOND, 999)
                        }
                        
                        bills.filter { it.timestamp in startCal.timeInMillis..endCal.timeInMillis }
                    } catch (e: Exception) {
                        bills
                    }
                } else {
                    bills
                }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Cart Management
    private val _cart = MutableStateFlow<List<CartItem>>(emptyList())
    val cart: StateFlow<List<CartItem>> = _cart.asStateFlow()

    data class CartItem(
        val medicine: Medicine,
        
        // quantities selected
        val soldBoxes: Int = 0,
        val soldStrips: Int = 0,
        val soldPcs: Int = 0,
        val soldBottles: Int = 0,
        val soldTubes: Int = 0,
        val soldVials: Int = 0,
        val soldOtherQty: Double = 0.0,
        val totalPrice: Double = 0.0
    )

    fun addToCart(
        medicine: Medicine,
        boxes: Int = 0,
        strips: Int = 0,
        pcs: Int = 0,
        bottles: Int = 0,
        tubes: Int = 0,
        vials: Int = 0,
        otherQty: Double = 0.0
    ) {
        val calculatedPrice = when (medicine.type) {
            "tablet" -> {
                (boxes * medicine.salePriceBox) +
                (strips * medicine.salePriceStrip) +
                (pcs * medicine.salePricePiece)
            }
            "syrup" -> bottles * medicine.salePriceUnit
            "ointment" -> tubes * medicine.salePriceUnit
            "injection" -> vials * medicine.salePriceUnit
            else -> otherQty * medicine.salePriceUnit
        }

        val newItem = CartItem(
            medicine = medicine,
            soldBoxes = boxes,
            soldStrips = strips,
            soldPcs = pcs,
            soldBottles = bottles,
            soldTubes = tubes,
            soldVials = vials,
            soldOtherQty = otherQty,
            totalPrice = calculatedPrice
        )

        // Add or replace
        val currentCart = _cart.value.toMutableList()
        val index = currentCart.indexOfFirst { it.medicine.id == medicine.id }
        if (index != -1) {
            currentCart[index] = newItem
        } else {
            currentCart.add(newItem)
        }
        _cart.value = currentCart
    }

    fun removeFromCart(medicineId: Long) {
        _cart.value = _cart.value.filter { it.medicine.id != medicineId }
    }

    fun clearCart() {
        _cart.value = emptyList()
    }

    // Customer Cart Management
    private val _customerCart = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val customerCart: StateFlow<Map<Long, Int>> = _customerCart.asStateFlow()

    fun addToCustomerCart(medicineId: Long, qty: Int) {
        val current = _customerCart.value.toMutableMap()
        val currentQty = current[medicineId] ?: 0
        current[medicineId] = currentQty + qty
        _customerCart.value = current
    }

    fun setCustomerCartQty(medicineId: Long, qty: Int) {
        val current = _customerCart.value.toMutableMap()
        if (qty <= 0) {
            current.remove(medicineId)
        } else {
            current[medicineId] = qty
        }
        _customerCart.value = current
    }

    fun removeFromCustomerCart(medicineId: Long) {
        val current = _customerCart.value.toMutableMap()
        current.remove(medicineId)
        _customerCart.value = current
    }

    fun clearCustomerCart() {
        _customerCart.value = emptyMap()
    }

    // Bill Sequence and Confirming
    fun generateNextBillNumber(todayBillList: List<Bill>): String {
        val sdf = SimpleDateFormat("ddMMyy", Locale.US)
        val dateStr = sdf.format(Date())
        val prefix = "RO$dateStr"
        
        // filter today's bills that match current date format prefix
        val todaySequences = todayBillList
            .filter { it.billNumber.startsWith(prefix) }
            .mapNotNull {
                val seqStr = it.billNumber.substringAfter(prefix)
                seqStr.toIntOrNull()
            }
        
        val nextSeq = if (todaySequences.isEmpty()) 1 else todaySequences.maxOrNull()!! + 1
        return "$prefix${String.format("%03d", nextSeq)}"
    }

    fun confirmSale(billNumber: String, discount: Double = 0.0, onComplete: () -> Unit) {
        viewModelScope.launch {
            val total = _cart.value.sumOf { it.totalPrice }
            val billItems = _cart.value.map { cartItem ->
                val desc = when (cartItem.medicine.type) {
                    "tablet" -> {
                        val parts = mutableListOf<String>()
                        if (cartItem.soldBoxes > 0) parts.add("${cartItem.soldBoxes} বক্স")
                        if (cartItem.soldStrips > 0) parts.add("${cartItem.soldStrips} পাতা")
                        if (cartItem.soldPcs > 0) parts.add("${cartItem.soldPcs} পিস")
                        parts.joinToString(" ")
                    }
                    "syrup" -> "${cartItem.soldBottles} বোতল"
                    "ointment" -> "${cartItem.soldTubes} টিউব"
                    "injection" -> "${cartItem.soldVials} ভায়াল"
                    else -> "${cartItem.soldOtherQty} ${cartItem.medicine.otherUnit}"
                }
                BillItem(
                    medicineId = cartItem.medicine.id,
                    name = cartItem.medicine.fullName,
                    type = cartItem.medicine.type,
                    formatDescription = desc,
                    totalPrice = cartItem.totalPrice,
                    soldBoxes = cartItem.soldBoxes,
                    soldStrips = cartItem.soldStrips,
                    soldPcs = cartItem.soldPcs,
                    soldBottles = cartItem.soldBottles,
                    soldTubes = cartItem.soldTubes,
                    soldVials = cartItem.soldVials,
                    soldOtherQty = cartItem.soldOtherQty
                )
            }

            val netTotal = (total - discount).coerceAtLeast(0.0)
            val ownerUsername = getOwnerUsername(currentUser.value)
            val activeUser = currentUser.value
            val bill = Bill(
                billNumber = billNumber,
                timestamp = System.currentTimeMillis(),
                totalAmount = netTotal,
                itemsJson = repository.serializeBillItems(billItems),
                discount = discount,
                pharmacyOwnerUsername = ownerUsername,
                staffUsername = if (activeUser?.role == "staff") activeUser.username else ""
            )

            // Save bill & decrement stock
            repository.confirmBill(bill, billItems)

            // Trigger notification
            addNotification(
                titleEn = "New Bill Created: ${bill.billNumber}",
                titleBn = "নতুন বিল তৈরি হয়েছে: ${bill.billNumber}",
                messageEn = "A new bill has been successfully generated for ৳${bill.totalAmount}.",
                messageBn = "৳${bill.totalAmount} মূল্যের একটি নতুন বিল সফলভাবে তৈরি করা হয়েছে।",
                targetRole = "admin"
            )
            addNotification(
                titleEn = "New Bill Created: ${bill.billNumber}",
                titleBn = "নতুন বিল তৈরি হয়েছে: ${bill.billNumber}",
                messageEn = "A new bill has been successfully generated for ৳${bill.totalAmount}.",
                messageBn = "৳${bill.totalAmount} মূল্যের একটি নতুন বিল সফলভাবে তৈরি করা হয়েছে।",
                targetRole = "staff"
            )

            // Sync to Firebase and queue print job
            try {
                getFirebaseRef().child("bills").child(bill.billNumber).setValue(bill)
                billItems.forEach { item ->
                    val updatedMed = repository.getMedicineById(item.medicineId)
                    if (updatedMed != null) {
                        getFirebaseRef().child("medicines").child(updatedMed.id.toString()).setValue(updatedMed)
                    }
                }
                
                // Automatically queue the print job to Firebase for the ESP32 to print over WiFi
                queuePrintJob(bill)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            clearCart()
            onComplete()
        }
    }

    fun confirmOnlineOrderSale(order: CustomerOrder, billNumber: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            val medicine = repository.getMedicineById(order.medicineId)
            if (medicine == null) {
                onComplete()
                return@launch
            }

            // Format description
            val pcsPerBox = medicine.stripsPerBox * medicine.pcsPerStrip
            val boxes = if (pcsPerBox > 0) order.quantity / pcsPerBox else 0
            val rem = if (pcsPerBox > 0) order.quantity % pcsPerBox else order.quantity
            val strips = if (medicine.pcsPerStrip > 0) rem / medicine.pcsPerStrip else 0
            val pcs = if (medicine.pcsPerStrip > 0) rem % medicine.pcsPerStrip else rem

            val desc = when (medicine.type) {
                "tablet" -> {
                    val parts = mutableListOf<String>()
                    if (boxes > 0) parts.add("$boxes বক্স")
                    if (strips > 0) parts.add("$strips পাতা")
                    if (pcs > 0) parts.add("$pcs পিস")
                    parts.joinToString(" ")
                }
                "syrup" -> "${order.quantity} বোতল"
                "ointment" -> "${order.quantity} টিউব"
                "injection" -> "${order.quantity} ভায়াল"
                else -> "${order.quantity} ${medicine.otherUnit}"
            }

            val billItem = BillItem(
                medicineId = order.medicineId,
                name = medicine.fullName,
                type = medicine.type,
                formatDescription = desc,
                totalPrice = order.totalPrice,
                soldBoxes = boxes,
                soldStrips = strips,
                soldPcs = pcs,
                soldBottles = if (medicine.type == "syrup") order.quantity else 0,
                soldTubes = if (medicine.type == "ointment") order.quantity else 0,
                soldVials = if (medicine.type == "injection") order.quantity else 0,
                soldOtherQty = if (medicine.type != "tablet" && medicine.type != "syrup" && medicine.type != "ointment" && medicine.type != "injection") order.quantity.toDouble() else 0.0
            )

            val ownerUsername = getOwnerUsername(currentUser.value)
            val activeUser = currentUser.value
            val bill = Bill(
                billNumber = billNumber,
                timestamp = System.currentTimeMillis(),
                totalAmount = order.totalPrice,
                itemsJson = repository.serializeBillItems(listOf(billItem)),
                discount = 0.0,
                pharmacyOwnerUsername = ownerUsername,
                isOnlineOrder = true,
                staffUsername = if (activeUser?.role == "staff") activeUser.username else ""
            )

            // Save bill & decrement stock
            repository.confirmBill(bill, listOf(billItem))

            // Update customer order status to "Confirmed"
            val updatedOrder = order.copy(status = "Confirmed")
            repository.insertCustomerOrder(updatedOrder)

            // Sync to Firebase and queue print job
            try {
                getFirebaseRef().child("bills").child(bill.billNumber).setValue(bill)
                getFirebaseRef().child("customer_orders").child(order.id.toString()).setValue(updatedOrder)
                
                val updatedMed = repository.getMedicineById(medicine.id)
                if (updatedMed != null) {
                    getFirebaseRef().child("medicines").child(updatedMed.id.toString()).setValue(updatedMed)
                }
                
                // Automatically queue the print job to Firebase for the ESP32 to print over WiFi
                queuePrintJob(bill)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            onComplete()
        }
    }

    fun cancelCustomerOrder(order: CustomerOrder, onComplete: () -> Unit) {
        viewModelScope.launch {
            val updatedOrder = order.copy(status = "Cancelled")
            repository.insertCustomerOrder(updatedOrder)
            try {
                getFirebaseRef().child("customer_orders").child(order.id.toString()).setValue(updatedOrder)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            onComplete()
        }
    }

    // Medicine Inventory Management Callbacks
    fun addMedicine(medicine: Medicine, onComplete: (Long) -> Unit) {
        viewModelScope.launch {
            val ownerUsername = getOwnerUsername(currentUser.value)
            val medWithOwner = medicine.copy(pharmacyOwnerUsername = ownerUsername)
            val id = repository.insertMedicine(medWithOwner)
            try {
                val finalMed = medWithOwner.copy(id = id)
                getFirebaseRef().child("medicines").child(id.toString()).setValue(finalMed)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            onComplete(id)
        }
    }

    fun updateMedicine(medicine: Medicine) {
        viewModelScope.launch {
            repository.updateMedicine(medicine)
            try {
                getFirebaseRef().child("medicines").child(medicine.id.toString()).setValue(medicine)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteMedicine(medicine: Medicine) {
        viewModelScope.launch {
            repository.deleteMedicine(medicine)
            try {
                getFirebaseRef().child("medicines").child(medicine.id.toString()).removeValue()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun addStockInRecord(record: StockInRecord) {
        viewModelScope.launch {
            val ownerUsername = getOwnerUsername(currentUser.value)
            val recordWithOwner = record.copy(pharmacyOwnerUsername = ownerUsername)
            val rId = repository.insertStockInRecord(recordWithOwner)
            try {
                val finalRecord = recordWithOwner.copy(id = rId)
                getFirebaseRef().child("stock_in_records").child(rId.toString()).setValue(finalRecord)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun requestPendingStockIn(
        medicine: Medicine,
        addBoxes: Int,
        addStrips: Int,
        addPcs: Int,
        addBottles: Int,
        addTubes: Int,
        addVials: Int,
        addOtherQty: Double,
        newPurchasePrice: Double?,
        newSalePrice: Double?,
        staffUsername: String
    ) {
        viewModelScope.launch {
            val pendingRecord = PendingStockIn(
                medicineId = medicine.id,
                medicineName = medicine.name,
                medicineType = medicine.type,
                timestamp = System.currentTimeMillis(),
                addedBoxes = addBoxes,
                addedStrips = addStrips,
                addedPcs = addPcs,
                addedBottles = addBottles,
                addedTubes = addTubes,
                addedVials = addVials,
                addedOtherQty = addOtherQty,
                purchasePrice = newPurchasePrice,
                salePrice = newSalePrice,
                staffUsername = staffUsername
            )
            val pId = repository.insertPendingStockIn(pendingRecord)
            try {
                val finalRecord = pendingRecord.copy(id = pId)
                getFirebaseRef().child("pending_stock_ins").child(pId.toString()).setValue(finalRecord)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            addNotification(
                titleEn = "New Stock-In Request from @$staffUsername",
                titleBn = "নতুন স্টক-ইন অনুরোধ এসেছে: @$staffUsername",
                messageEn = "Submitted stock-in request for ${medicine.name}.",
                messageBn = "${medicine.name} এর জন্য একটি নতুন স্টক-ইন অনুরোধ পাঠানো হয়েছে।",
                targetRole = "admin"
            )
        }
    }

    fun approvePendingStockIn(pendingStockIn: PendingStockIn) {
        viewModelScope.launch {
            val targetMed = allMedicines.value.find { it.id == pendingStockIn.medicineId }
            if (targetMed != null) {
                addStockAndPrices(
                    medicine = targetMed,
                    addBoxes = pendingStockIn.addedBoxes,
                    addStrips = pendingStockIn.addedStrips,
                    addPcs = pendingStockIn.addedPcs,
                    addBottles = pendingStockIn.addedBottles,
                    addTubes = pendingStockIn.addedTubes,
                    addVials = pendingStockIn.addedVials,
                    addOtherQty = pendingStockIn.addedOtherQty,
                    newPurchasePrice = pendingStockIn.purchasePrice,
                    newSalePrice = pendingStockIn.salePrice
                )

                // Create a stock-in record log as requested to keep stock logs updated
                val ownerUsername = getOwnerUsername(currentUser.value)
                val record = StockInRecord(
                    medicineId = targetMed.id,
                    medicineName = targetMed.name,
                    medicineType = targetMed.type,
                    timestamp = System.currentTimeMillis(),
                    addedBoxes = pendingStockIn.addedBoxes,
                    addedStrips = pendingStockIn.addedStrips,
                    addedPcs = pendingStockIn.addedPcs,
                    addedBottles = pendingStockIn.addedBottles,
                    addedTubes = pendingStockIn.addedTubes,
                    addedVials = pendingStockIn.addedVials,
                    addedOtherQty = pendingStockIn.addedOtherQty,
                    oldPurchasePrice = if (targetMed.type == "tablet") targetMed.purchasePriceBox else targetMed.purchasePriceUnit,
                    oldSalePrice = if (targetMed.type == "tablet") targetMed.salePriceBox else targetMed.salePriceUnit,
                    newPurchasePrice = pendingStockIn.purchasePrice ?: 0.0,
                    newSalePrice = pendingStockIn.salePrice ?: 0.0,
                    pharmacyOwnerUsername = ownerUsername
                )
                val rId = repository.insertStockInRecord(record)
                try {
                    getFirebaseRef().child("stock_in_records").child(rId.toString()).setValue(record.copy(id = rId))
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                addNotification(
                    titleEn = "Stock-In Approved: ${pendingStockIn.medicineName}",
                    titleBn = "স্টক-ইন অনুমোদিত: ${pendingStockIn.medicineName}",
                    messageEn = "The stock-in request submitted by @${pendingStockIn.staffUsername} has been approved.",
                    messageBn = "@${pendingStockIn.staffUsername} কর্তৃক প্রেরিত ${pendingStockIn.medicineName} এর স্টক-ইন অনুরোধটি অনুমোদন করা হয়েছে।",
                    targetRole = "staff",
                    targetUsername = pendingStockIn.staffUsername
                )
            }
            repository.deletePendingStockIn(pendingStockIn)
            try {
                getFirebaseRef().child("pending_stock_ins").child(pendingStockIn.id.toString()).removeValue()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun rejectPendingStockIn(pendingStockIn: PendingStockIn) {
        viewModelScope.launch {
            repository.deletePendingStockIn(pendingStockIn)
            try {
                getFirebaseRef().child("pending_stock_ins").child(pendingStockIn.id.toString()).removeValue()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            addNotification(
                titleEn = "Stock-In Request Rejected",
                titleBn = "স্টক-ইন অনুরোধ প্রত্যাখ্যাত",
                messageEn = "Your stock-in request for ${pendingStockIn.medicineName} was rejected.",
                messageBn = "আপনার ${pendingStockIn.medicineName} এর স্টক-ইন অনুরোধটি প্রত্যাখ্যান করা হয়েছে।",
                targetRole = "staff",
                targetUsername = pendingStockIn.staffUsername
            )
        }
    }

    fun requestPendingMedicine(newMed: Medicine, staffUsername: String, targetMedicineId: Long?) {
        viewModelScope.launch {
            val pending = PendingMedicine(
                timestamp = System.currentTimeMillis(),
                staffUsername = staffUsername,
                type = newMed.type,
                name = newMed.name,
                dosageStrength = newMed.dosageStrength,
                company = newMed.company,
                genericName = newMed.genericName,
                barcode = newMed.barcode,
                expiryDate = newMed.expiryDate,
                purchasePriceBox = newMed.purchasePriceBox,
                salePriceBox = newMed.salePriceBox,
                stripsPerBox = newMed.stripsPerBox,
                pcsPerStrip = newMed.pcsPerStrip,
                boxesInStock = newMed.boxesInStock,
                stripsInStock = newMed.stripsInStock,
                pcsInStock = newMed.pcsInStock,
                mlPerBottle = newMed.mlPerBottle,
                bottlesInStock = newMed.bottlesInStock,
                gramsPerTube = newMed.gramsPerTube,
                tubesInStock = newMed.tubesInStock,
                vialsInStock = newMed.vialsInStock,
                otherQuantity = newMed.otherQuantity,
                otherUnit = newMed.otherUnit,
                purchasePriceUnit = newMed.purchasePriceUnit,
                salePriceUnit = newMed.salePriceUnit,
                lowStockThreshold = newMed.lowStockThreshold,
                targetMedicineId = targetMedicineId
            )
            val pId = repository.insertPendingMedicine(pending)
            try {
                val finalPending = pending.copy(id = pId)
                getFirebaseRef().child("pending_medicines").child(pId.toString()).setValue(finalPending)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            addNotification(
                titleEn = "New Medicine Request from @$staffUsername",
                titleBn = "নতুন ওষুধের অনুরোধ এসেছে: @$staffUsername",
                messageEn = "Submitted request for ${newMed.name} (${newMed.dosageStrength}).",
                messageBn = "${newMed.name} (${newMed.dosageStrength}) ওষুধের জন্য নতুন অনুরোধ সাবমিট করা হয়েছে।",
                targetRole = "admin"
            )
        }
    }

    fun approvePendingMedicine(pending: PendingMedicine) {
        viewModelScope.launch {
            val ownerUsername = getOwnerUsername(currentUser.value)
            if (pending.targetMedicineId == null) {
                // Brand new medicine
                val newMed = Medicine(
                    type = pending.type,
                    name = pending.name,
                    dosageStrength = pending.dosageStrength,
                    company = pending.company,
                    genericName = pending.genericName,
                    barcode = pending.barcode,
                    expiryDate = pending.expiryDate,
                    pharmacyOwnerUsername = ownerUsername,
                    purchasePriceBox = pending.purchasePriceBox,
                    salePriceBox = pending.salePriceBox,
                    stripsPerBox = pending.stripsPerBox,
                    pcsPerStrip = pending.pcsPerStrip,
                    boxesInStock = pending.boxesInStock,
                    stripsInStock = pending.stripsInStock,
                    pcsInStock = pending.pcsInStock,
                    mlPerBottle = pending.mlPerBottle,
                    bottlesInStock = pending.bottlesInStock,
                    gramsPerTube = pending.gramsPerTube,
                    tubesInStock = pending.tubesInStock,
                    vialsInStock = pending.vialsInStock,
                    otherQuantity = pending.otherQuantity,
                    otherUnit = pending.otherUnit,
                    purchasePriceUnit = pending.purchasePriceUnit,
                    salePriceUnit = pending.salePriceUnit,
                    lowStockThreshold = pending.lowStockThreshold
                )
                val newId = repository.insertMedicine(newMed)
                try {
                    getFirebaseRef().child("medicines").child(newId.toString()).setValue(newMed.copy(id = newId))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                val hasChangeInStock = (pending.boxesInStock > 0 || pending.stripsInStock > 0 || pending.pcsInStock > 0 ||
                                       pending.bottlesInStock > 0 || pending.tubesInStock > 0 ||
                                       pending.vialsInStock > 0 || pending.otherQuantity > 0.0)
                if (hasChangeInStock) {
                    val record = StockInRecord(
                        medicineId = newId,
                        medicineName = pending.name,
                        medicineType = pending.type,
                        timestamp = System.currentTimeMillis(),
                        addedBoxes = pending.boxesInStock,
                        addedStrips = pending.stripsInStock,
                        addedPcs = pending.pcsInStock,
                        addedBottles = pending.bottlesInStock,
                        addedTubes = pending.tubesInStock,
                        addedVials = pending.vialsInStock,
                        addedOtherQty = pending.otherQuantity,
                        oldPurchasePrice = 0.0,
                        oldSalePrice = 0.0,
                        newPurchasePrice = if (pending.type == "tablet") pending.purchasePriceBox else pending.purchasePriceUnit,
                        newSalePrice = if (pending.type == "tablet") pending.salePriceBox else pending.salePriceUnit,
                        pharmacyOwnerUsername = ownerUsername
                    )
                    val rId = repository.insertStockInRecord(record)
                    try {
                        getFirebaseRef().child("stock_in_records").child(rId.toString()).setValue(record.copy(id = rId))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } else {
                // Updating an existing medicine
                val existing = repository.getMedicineById(pending.targetMedicineId)
                if (existing != null) {
                    val mergedMed = when (existing.type) {
                        "tablet" -> {
                            val oldTotal = existing.totalPiecesInStock
                            val addedTotal = (pending.boxesInStock * pending.stripsPerBox * pending.pcsPerStrip) +
                                             (pending.stripsInStock * pending.pcsPerStrip) +
                                             pending.pcsInStock
                            val combinedTotal = oldTotal + addedTotal
                            
                            val stripsPerBox = if (pending.stripsPerBox > 0) pending.stripsPerBox else existing.stripsPerBox
                            val pcsPerStrip = if (pending.pcsPerStrip > 0) pending.pcsPerStrip else existing.pcsPerStrip
                            val factor = stripsPerBox * pcsPerStrip
                            
                            val finalBoxes = if (factor > 0) combinedTotal / factor else 0
                            val remAfterBoxes = if (factor > 0) combinedTotal % factor else 0
                            val finalStrips = if (pcsPerStrip > 0) remAfterBoxes / pcsPerStrip else 0
                            val finalPcs = if (pcsPerStrip > 0) remAfterBoxes % pcsPerStrip else 0
                            
                            existing.copy(
                                name = pending.name,
                                company = pending.company,
                                genericName = pending.genericName,
                                barcode = pending.barcode,
                                expiryDate = pending.expiryDate,
                                purchasePriceBox = pending.purchasePriceBox,
                                salePriceBox = pending.salePriceBox,
                                stripsPerBox = stripsPerBox,
                                pcsPerStrip = pcsPerStrip,
                                boxesInStock = finalBoxes,
                                stripsInStock = finalStrips,
                                pcsInStock = finalPcs,
                                lowStockThreshold = pending.lowStockThreshold
                            )
                        }
                        "syrup" -> {
                            existing.copy(
                                name = pending.name,
                                company = pending.company,
                                genericName = pending.genericName,
                                barcode = pending.barcode,
                                expiryDate = pending.expiryDate,
                                mlPerBottle = pending.mlPerBottle,
                                purchasePriceUnit = pending.purchasePriceUnit,
                                salePriceUnit = pending.salePriceUnit,
                                bottlesInStock = existing.bottlesInStock + pending.bottlesInStock,
                                lowStockThreshold = pending.lowStockThreshold
                            )
                        }
                        "ointment" -> {
                            existing.copy(
                                name = pending.name,
                                company = pending.company,
                                genericName = pending.genericName,
                                barcode = pending.barcode,
                                expiryDate = pending.expiryDate,
                                gramsPerTube = pending.gramsPerTube,
                                purchasePriceUnit = pending.purchasePriceUnit,
                                salePriceUnit = pending.salePriceUnit,
                                tubesInStock = existing.tubesInStock + pending.tubesInStock,
                                lowStockThreshold = pending.lowStockThreshold
                            )
                        }
                        "injection" -> {
                            existing.copy(
                                name = pending.name,
                                company = pending.company,
                                genericName = pending.genericName,
                                barcode = pending.barcode,
                                expiryDate = pending.expiryDate,
                                purchasePriceUnit = pending.purchasePriceUnit,
                                salePriceUnit = pending.salePriceUnit,
                                vialsInStock = existing.vialsInStock + pending.vialsInStock,
                                lowStockThreshold = pending.lowStockThreshold
                            )
                        }
                        else -> {
                            existing.copy(
                                name = pending.name,
                                company = pending.company,
                                genericName = pending.genericName,
                                barcode = pending.barcode,
                                expiryDate = pending.expiryDate,
                                otherUnit = pending.otherUnit,
                                purchasePriceUnit = pending.purchasePriceUnit,
                                salePriceUnit = pending.salePriceUnit,
                                otherQuantity = existing.otherQuantity + pending.otherQuantity,
                                lowStockThreshold = pending.lowStockThreshold
                            )
                        }
                    }
                    repository.updateMedicine(mergedMed)
                    try {
                        getFirebaseRef().child("medicines").child(mergedMed.id.toString()).setValue(mergedMed)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    
                    val hasChangeInStock = (pending.boxesInStock > 0 || pending.stripsInStock > 0 || pending.pcsInStock > 0 ||
                                           pending.bottlesInStock > 0 || pending.tubesInStock > 0 ||
                                           pending.vialsInStock > 0 || pending.otherQuantity > 0.0)
                    if (hasChangeInStock) {
                        val record = StockInRecord(
                            medicineId = existing.id,
                            medicineName = existing.name,
                            medicineType = existing.type,
                            timestamp = System.currentTimeMillis(),
                            addedBoxes = pending.boxesInStock,
                            addedStrips = pending.stripsInStock,
                            addedPcs = pending.pcsInStock,
                            addedBottles = pending.bottlesInStock,
                            addedTubes = pending.tubesInStock,
                            addedVials = pending.vialsInStock,
                            addedOtherQty = pending.otherQuantity,
                            oldPurchasePrice = if (existing.type == "tablet") existing.purchasePriceBox else existing.purchasePriceUnit,
                            oldSalePrice = if (existing.type == "tablet") existing.salePriceBox else existing.salePriceUnit,
                            newPurchasePrice = if (existing.type == "tablet") pending.purchasePriceBox else pending.purchasePriceUnit,
                            newSalePrice = if (existing.type == "tablet") pending.salePriceBox else pending.salePriceUnit,
                            pharmacyOwnerUsername = ownerUsername
                        )
                        val rId = repository.insertStockInRecord(record)
                        try {
                            getFirebaseRef().child("stock_in_records").child(rId.toString()).setValue(record.copy(id = rId))
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
            repository.deletePendingMedicine(pending)
            try {
                getFirebaseRef().child("pending_medicines").child(pending.id.toString()).removeValue()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            addNotification(
                titleEn = "Medicine Request Approved: ${pending.name}",
                titleBn = "নতুন ওষুধের অনুরোধ অনুমোদিত: ${pending.name}",
                messageEn = "Your request for ${pending.name} has been approved and added to inventory.",
                messageBn = "আপনার করা ${pending.name} ওষুধের অনুরোধটি অনুমোদন করে স্টক তালিকায় যুক্ত করা হয়েছে।",
                targetRole = "staff",
                targetUsername = pending.staffUsername
            )
        }
    }

    fun rejectPendingMedicine(pending: PendingMedicine) {
        viewModelScope.launch {
            repository.deletePendingMedicine(pending)
            try {
                getFirebaseRef().child("pending_medicines").child(pending.id.toString()).removeValue()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            addNotification(
                titleEn = "Medicine Request Rejected: ${pending.name}",
                titleBn = "ওষুধের অনুরোধ প্রত্যাখ্যাত: ${pending.name}",
                messageEn = "Your request for ${pending.name} was rejected.",
                messageBn = "আপনার ${pending.name} ওষুধের অনুরোধটি প্রত্যাখ্যান করা হয়েছে।",
                targetRole = "staff",
                targetUsername = pending.staffUsername
            )
        }
    }

    fun placeOrder(medicine: Medicine, qty: Int, pharmacyName: String, totalPrice: Double) {
        viewModelScope.launch {
            val user = _currentUser.value
            val formatter = java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault())
            val dateStr = formatter.format(java.util.Date())

            val order = CustomerOrder(
                timestamp = System.currentTimeMillis(),
                customerUsername = user.username,
                medicineId = medicine.id,
                medicineName = medicine.name,
                medicineType = medicine.type,
                quantity = qty,
                totalPrice = totalPrice,
                status = "Pending",
                pharmacyName = pharmacyName,
                dateString = dateStr
            )
            val oId = repository.insertCustomerOrder(order)
            try {
                val finalOrder = order.copy(id = oId)
                getFirebaseRef().child("customer_orders").child(oId.toString()).setValue(finalOrder)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            addNotification(
                titleEn = "New Order Received from ${user.username}",
                titleBn = "নতুন কাস্টমার অর্ডার এসেছে: ${user.username}",
                messageEn = "Ordered: ${medicine.name} (${qty} qty). Total price: ৳${totalPrice}.",
                messageBn = "ওষুধ: ${medicine.name} (পরিমাণ: ${qty} টি)। মোট মূল্য: ৳${totalPrice}।",
                targetRole = "admin"
            )
            addNotification(
                titleEn = "New Order Received from ${user.username}",
                titleBn = "নতুন কাস্টমার অর্ডার এসেছে: ${user.username}",
                messageEn = "Ordered: ${medicine.name} (${qty} qty). Total price: ৳${totalPrice}.",
                messageBn = "ওষুধ: ${medicine.name} (পরিমাণ: ${qty} টি)। মোট মূল্য: ৳${totalPrice}।",
                targetRole = "staff"
            )
            addNotification(
                titleEn = "Order Placed Successfully",
                titleBn = "আপনার অর্ডার সফলভাবে সাবমিট হয়েছে",
                messageEn = "Your order for ${medicine.name} has been submitted to $pharmacyName.",
                messageBn = "আপনার ${medicine.name} ওষুধ ক্রয়ের অর্ডারটি সফলভাবে $pharmacyName এ সাবমিট করা হয়েছে।",
                targetRole = "customer",
                targetUsername = user.username
            )
        }
    }

    fun placeGroupedOrder(medicinesWithQty: List<Pair<Medicine, Int>>, pharmacyName: String) {
        viewModelScope.launch {
            val user = _currentUser.value
            val formatter = java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault())
            val dateStr = formatter.format(java.util.Date())

            medicinesWithQty.forEach { (medicine, qty) ->
                val order = CustomerOrder(
                    timestamp = System.currentTimeMillis(),
                    customerUsername = user.username,
                    medicineId = medicine.id,
                    medicineName = medicine.name,
                    medicineType = medicine.type,
                    quantity = qty,
                    totalPrice = medicine.calculatePriceForQuantity(qty),
                    status = "Pending",
                    pharmacyName = pharmacyName,
                    dateString = dateStr
                )
                val oId = repository.insertCustomerOrder(order)
                try {
                    val finalOrder = order.copy(id = oId)
                    getFirebaseRef().child("customer_orders").child(oId.toString()).setValue(finalOrder)
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                addNotification(
                    titleEn = "New Order Received from ${user.username}",
                    titleBn = "নতুন কাস্টমার অর্ডার এসেছে: ${user.username}",
                    messageEn = "Ordered: ${medicine.name} (${qty} qty). Total: ৳${medicine.calculatePriceForQuantity(qty)}.",
                    messageBn = "ওষুধ: ${medicine.name} (পরিমাণ: ${qty} টি)। মোট: ৳${medicine.calculatePriceForQuantity(qty)}।",
                    targetRole = "admin"
                )
                addNotification(
                    titleEn = "New Order Received from ${user.username}",
                    titleBn = "নতুন কাস্টমার অর্ডার এসেছে: ${user.username}",
                    messageEn = "Ordered: ${medicine.name} (${qty} qty). Total: ৳${medicine.calculatePriceForQuantity(qty)}.",
                    messageBn = "ওষুধ: ${medicine.name} (পরিমাণ: ${qty} টি)। মোট: ৳${medicine.calculatePriceForQuantity(qty)}।",
                    targetRole = "staff"
                )
                addNotification(
                    titleEn = "Order Placed Successfully",
                    titleBn = "আপনার অর্ডার সফলভাবে সাবমিট হয়েছে",
                    messageEn = "Your order for ${medicine.name} (qty: $qty) has been submitted to $pharmacyName.",
                    messageBn = "আপনার ${medicine.name} ওষুধ ক্রয়ের অর্ডারটি (পরিমাণ: $qty টি) সফলভাবে $pharmacyName এ সাবমিট করা হয়েছে।",
                    targetRole = "customer",
                    targetUsername = user.username
                )
                
                // Remove matching demands from cart
                val cartKeysToRemove = _customerCart.value.keys.filter { cartId ->
                    val m = allMedicines.value.find { it.id == cartId }
                    m != null && m.name.equals(medicine.name, ignoreCase = true) && m.dosageStrength.equals(medicine.dosageStrength, ignoreCase = true)
                }
                cartKeysToRemove.forEach { id ->
                    removeFromCustomerCart(id)
                }
            }
        }
    }

    fun updateOrderStatus(order: CustomerOrder, status: String) {
        viewModelScope.launch {
            val updated = order.copy(status = status)
            repository.insertCustomerOrder(updated)
            try {
                getFirebaseRef().child("customer_orders").child(updated.id.toString()).setValue(updated)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val statusTextBn = when (status) {
                "Confirmed" -> "অনুমোদন করা হয়েছে"
                "Cancelled" -> "বাতিল করা হয়েছে"
                else -> status
            }
            addNotification(
                titleEn = "Order Update: Order #${order.id}",
                titleBn = "আপনার অর্ডারের নতুন আপডেট: #${order.id}",
                messageEn = "Your order for ${order.medicineName} has been $status.",
                messageBn = "আপনার ${order.medicineName} ওষুধের অর্ডারটি #${order.id} বর্তমানে '$statusTextBn' করা হয়েছে।",
                targetRole = "customer",
                targetUsername = order.customerUsername
            )
        }
    }

    fun deleteCustomerOrder(order: CustomerOrder) {
        viewModelScope.launch {
            repository.deleteCustomerOrder(order)
            try {
                getFirebaseRef().child("customer_orders").child(order.id.toString()).removeValue()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateStockOnly(medicine: Medicine, boxes: Int, strips: Int, pcs: Int, bottles: Int, tubes: Int, vials: Int, otherQty: Double) {
        val updated = when (medicine.type) {
            "tablet" -> medicine.copy(boxesInStock = boxes, stripsInStock = strips, pcsInStock = pcs)
            "syrup" -> medicine.copy(bottlesInStock = bottles)
            "ointment" -> medicine.copy(tubesInStock = tubes)
            "injection" -> medicine.copy(vialsInStock = vials)
            else -> medicine.copy(otherQuantity = otherQty)
        }
        updateMedicine(updated)
    }

    fun addStockAndPrices(
        medicine: Medicine,
        addBoxes: Int,
        addStrips: Int,
        addPcs: Int,
        addBottles: Int,
        addTubes: Int,
        addVials: Int,
        addOtherQty: Double,
        newPurchasePrice: Double?, // optional
        newSalePrice: Double?,      // optional
        newLowStockThreshold: Int? = null // optional
    ) {
        viewModelScope.launch {
            val hasChangeInStock = (addBoxes > 0 || addStrips > 0 || addPcs > 0 || addBottles > 0 || addTubes > 0 || addVials > 0 || addOtherQty > 0.0)
            
            var updatedMed = when (medicine.type) {
                "tablet" -> {
                    val oldTotalPieces = (medicine.boxesInStock * medicine.stripsPerBox * medicine.pcsPerStrip) +
                                         (medicine.stripsInStock * medicine.pcsPerStrip) +
                                         medicine.pcsInStock
                    val addedTotalPieces = (addBoxes * medicine.stripsPerBox * medicine.pcsPerStrip) +
                                           (addStrips * medicine.pcsPerStrip) +
                                           addPcs
                    val finalTotalPieces = oldTotalPieces + addedTotalPieces
                    
                    val finalBoxes = finalTotalPieces / (medicine.stripsPerBox * medicine.pcsPerStrip)
                    val finalPcsAfterBoxes = finalTotalPieces % (medicine.stripsPerBox * medicine.pcsPerStrip)
                    val finalStrips = finalPcsAfterBoxes / medicine.pcsPerStrip
                    val finalPcs = finalPcsAfterBoxes % medicine.pcsPerStrip
                    
                    medicine.copy(
                        boxesInStock = finalBoxes,
                        stripsInStock = finalStrips,
                        pcsInStock = finalPcs
                    )
                }
                "syrup" -> {
                    medicine.copy(bottlesInStock = medicine.bottlesInStock + addBottles)
                }
                "ointment" -> {
                    medicine.copy(tubesInStock = medicine.tubesInStock + addTubes)
                }
                "injection" -> {
                    medicine.copy(vialsInStock = medicine.vialsInStock + addVials)
                }
                else -> {
                    medicine.copy(otherQuantity = medicine.otherQuantity + addOtherQty)
                }
            }

            if (medicine.type == "tablet") {
                if (newPurchasePrice != null && newPurchasePrice > 0.0) {
                    updatedMed = updatedMed.copy(purchasePriceBox = newPurchasePrice)
                }
                if (newSalePrice != null && newSalePrice > 0.0) {
                    updatedMed = updatedMed.copy(salePriceBox = newSalePrice)
                }
            } else {
                if (newPurchasePrice != null && newPurchasePrice > 0.0) {
                    updatedMed = updatedMed.copy(purchasePriceUnit = newPurchasePrice)
                }
                if (newSalePrice != null && newSalePrice > 0.0) {
                    updatedMed = updatedMed.copy(salePriceUnit = newSalePrice)
                }
            }
            
            if (newLowStockThreshold != null) {
                updatedMed = updatedMed.copy(lowStockThreshold = newLowStockThreshold)
            }

            repository.updateMedicine(updatedMed)
            try {
                getFirebaseRef().child("medicines").child(updatedMed.id.toString()).setValue(updatedMed)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            if (hasChangeInStock) {
                val record = StockInRecord(
                    medicineId = medicine.id,
                    medicineName = medicine.name,
                    medicineType = medicine.type,
                    timestamp = System.currentTimeMillis(),
                    addedBoxes = addBoxes,
                    addedStrips = addStrips,
                    addedPcs = addPcs,
                    addedBottles = addBottles,
                    addedTubes = addTubes,
                    addedVials = addVials,
                    addedOtherQty = addOtherQty,
                    oldPurchasePrice = if (medicine.type == "tablet") medicine.purchasePriceBox else medicine.purchasePriceUnit,
                    oldSalePrice = if (medicine.type == "tablet") medicine.salePriceBox else medicine.salePriceUnit,
                    newPurchasePrice = newPurchasePrice ?: 0.0,
                    newSalePrice = newSalePrice ?: 0.0,
                    pharmacyOwnerUsername = getOwnerUsername(currentUser.value)
                )
                val rId = repository.insertStockInRecord(record)
                try {
                    getFirebaseRef().child("stock_in_records").child(rId.toString()).setValue(record.copy(id = rId))
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                addNotification(
                    titleEn = "Stock Updated: ${medicine.name}",
                    titleBn = "স্টক আপডেট করা হয়েছে: ${medicine.name}",
                    messageEn = "Stock has been successfully updated/added for ${medicine.name}.",
                    messageBn = "${medicine.name} এর স্টক সফলভাবে আপডেট/যোগ করা হয়েছে।",
                    targetRole = "admin"
                )
                addNotification(
                    titleEn = "Stock Updated: ${medicine.name}",
                    titleBn = "স্টক আপডেট করা হয়েছে: ${medicine.name}",
                    messageEn = "Stock has been successfully updated/added for ${medicine.name}.",
                    messageBn = "${medicine.name} এর স্টক সফলভাবে আপডেট/যোগ করা হয়েছে।",
                    targetRole = "staff"
                )
            }
        }
    }

    fun updatePriceOnly(medicine: Medicine, boxPurchase: Double, boxSale: Double, unitPurchase: Double, unitSale: Double, lowStockThreshold: Int? = null) {
        var updated = if (medicine.type == "tablet") {
            medicine.copy(purchasePriceBox = boxPurchase, salePriceBox = boxSale)
        } else {
            medicine.copy(purchasePriceUnit = unitPurchase, salePriceUnit = unitSale)
        }
        if (lowStockThreshold != null) {
            updated = updated.copy(lowStockThreshold = lowStockThreshold)
        }
        updateMedicine(updated)
    }

    // Deserialization helper for views
    fun getBillItemsList(bill: Bill): List<BillItem> {
        return repository.deserializeBillItems(bill.itemsJson)
    }

    fun getReturnedItemsList(bill: Bill): List<BillItem> {
        return repository.deserializeBillItems(bill.returnedItemsJson)
    }

    fun queuePrintJob(bill: Bill, isReturned: Boolean = false) {
        viewModelScope.launch {
            try {
                val ref = getFirebaseRef()
                val jobId = ref.child("print_jobs").push().key ?: return@launch
                
                val itemsList = if (isReturned) getReturnedItemsList(bill) else getBillItemsList(bill)
                val formattedItems = itemsList.map { item ->
                    val qtyDesc = buildString {
                        if (item.soldBoxes > 0) append("${item.soldBoxes} Box ")
                        if (item.soldStrips > 0) append("${item.soldStrips} Strip ")
                        if (item.soldPcs > 0) append("${item.soldPcs} Pcs ")
                        if (item.soldBottles > 0) append("${item.soldBottles} Bot ")
                        if (item.soldTubes > 0) append("${item.soldTubes} Tube ")
                        if (item.soldVials > 0) append("${item.soldVials} Vial ")
                        if (item.soldOtherQty > 0.0) append("${item.soldOtherQty} ")
                    }.trim()
                    
                    val uPrice = if (qtyDesc.contains("Box")) {
                        item.totalPrice / (if (item.soldBoxes > 0) item.soldBoxes else 1)
                    } else if (qtyDesc.contains("Strip")) {
                        item.totalPrice / (if (item.soldStrips > 0) item.soldStrips else 1)
                    } else if (qtyDesc.contains("Pcs")) {
                        item.totalPrice / (if (item.soldPcs > 0) item.soldPcs else 1)
                    } else {
                        item.totalPrice
                    }

                    mapOf(
                        "name" to item.name,
                        "qty" to qtyDesc,
                        "price" to uPrice,
                        "total" to item.totalPrice
                    )
                }

                val jobData = mapOf(
                    "id" to jobId,
                    "billNumber" to bill.billNumber,
                    "timestamp" to bill.timestamp,
                    "ownerUsername" to bill.pharmacyOwnerUsername,
                    "soldBy" to (if (bill.staffUsername.isNotEmpty()) bill.staffUsername else "admin"),
                    "totalAmount" to bill.totalAmount,
                    "discount" to bill.discount,
                    "finalAmount" to (bill.totalAmount - bill.discount).coerceAtLeast(0.0),
                    "isReturned" to isReturned,
                    "returnedAmount" to bill.returnedAmount,
                    "returnedBy" to (if (bill.staffUsername.isNotEmpty()) bill.staffUsername else "admin"),
                    "items" to formattedItems,
                    "status" to "pending"
                )
                
                val ownerKey = if (bill.pharmacyOwnerUsername.isNotEmpty()) bill.pharmacyOwnerUsername else "default"
                ref.child("print_jobs").child(ownerKey).child(jobId).setValue(jobData)
                
                addNotification(
                    titleEn = "Print Job Queued: ${bill.billNumber}",
                    titleBn = "প্রিন্ট জব কিউতে পাঠানো হয়েছে: ${bill.billNumber}",
                    messageEn = "Bill ${bill.billNumber} has been successfully sent to the cloud printer queue.",
                    messageBn = "বিল ${bill.billNumber} সফলভাবে ক্লাউড প্রিন্ট কিউতে পাঠানো হয়েছে।",
                    targetRole = "staff",
                    targetUsername = bill.staffUsername
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun returnBill(bill: Bill, returnedItems: List<BillItem>, refundAmount: Double) {
        viewModelScope.launch {
            repository.returnBill(bill, returnedItems, refundAmount)

            // Trigger notification
            addNotification(
                titleEn = "Bill Returned: ${bill.billNumber}",
                titleBn = "বিল ফেরত ও সমন্বয়: ${bill.billNumber}",
                messageEn = "Bill ${bill.billNumber} was refunded for ৳$refundAmount.",
                messageBn = "বিল ${bill.billNumber} এর মোট ৳$refundAmount ফেরত বা মূল্য সমন্বয় করা হয়েছে।",
                targetRole = "admin"
            )
            addNotification(
                titleEn = "Bill Returned: ${bill.billNumber}",
                titleBn = "বিল ফেরত ও সমন্বয়: ${bill.billNumber}",
                messageEn = "Bill ${bill.billNumber} was refunded for ৳$refundAmount.",
                messageBn = "বিল ${bill.billNumber} এর মোট ৳$refundAmount ফেরত বা মূল্য সমন্বয় করা হয়েছে।",
                targetRole = "staff"
            )

            try {
                val updatedBill = bill.copy(
                    isReturned = true,
                    returnedTime = System.currentTimeMillis(),
                    returnedAmount = refundAmount,
                    returnedItemsJson = repository.serializeBillItems(returnedItems)
                )
                getFirebaseRef().child("bills").child(updatedBill.billNumber).setValue(updatedBill)
                returnedItems.forEach { item ->
                    val updatedMed = repository.getMedicineById(item.medicineId)
                    if (updatedMed != null) {
                        getFirebaseRef().child("medicines").child(updatedMed.id.toString()).setValue(updatedMed)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // --- Gemini AI Smart Pharmacy Integration ---
    private val _aiResponse = MutableStateFlow<String>("")
    val aiResponse: StateFlow<String> = _aiResponse.asStateFlow()

    private val _aiIsLoading = MutableStateFlow(false)
    val aiIsLoading: StateFlow<Boolean> = _aiIsLoading.asStateFlow()

    fun askGeminiPharmacyInsight(customUserQuery: String = "") {
        viewModelScope.launch {
            _aiIsLoading.value = true
            _aiResponse.value = "বিশ্লেষণ করা হচ্ছে... অনুগ্রহ করে অপেক্ষা করুন।"

            val medicinesList = allMedicines.value
            val billsList = allBills.value

            val inventoryBrief = medicinesList.joinToString("\n") { m ->
                val stockStr = when (m.type) {
                    "tablet" -> "${m.boxesInStock} Box, ${m.stripsInStock} Strips, ${m.pcsInStock} Pcs"
                    "syrup" -> "${m.bottlesInStock} Bottles"
                    "ointment" -> "${m.tubesInStock} Tubes"
                    "injection" -> "${m.vialsInStock} Vials"
                    else -> "${m.otherQuantity} ${m.otherUnit}"
                }
                "- ${m.name} (${m.type}) | Generic: ${m.genericName} | Stock: $stockStr | Exp: ${m.expiryDate}"
            }

            val salesBrief = billsList.take(5).joinToString("\n") { b ->
                "- Bill: ${b.billNumber} | Amount: ৳${b.totalAmount} | Time: ${Date(b.timestamp)}"
            }

            val systemPrompt = """
                You are a highly experienced pharmaceutical business assistant for 'RO Pharmacy'.
                You analyze the inventory and sales data provided and suggest optimization strategies, restock warnings, and sales trends.
                Help the pharmacy owner make smart decisions.
                Respond in Bengali (বাংলা) or English depending on context. Default to pleasant and concise Bengali since the app is mainly in Bengali.
                Focus on:
                1. Identifying critical low-stock items.
                2. Identifying soon-to-expire or expired medicines.
                3. Highlighting recent sale values and suggesting fast-moving stock to order.
                Keep your output highly actionable, and formatted beautifully using clear Markdown lists.
            """.trimIndent()

            val userPrompt = if (customUserQuery.trim().isNotEmpty()) {
                """
                    My question: "$customUserQuery"
                    
                    Below is our latest Pharmacy Information:
                    
                    Inventory Details:
                    $inventoryBrief
                    
                    Recent Sales Bills:
                    $salesBrief
                """.trimIndent()
            } else {
                """
                    Please perform an automated analysis of our pharmacy.
                    Provide a restock list, identify expiring items, and share 3 tips to improve sales today.
                    
                    Inventory Details:
                    $inventoryBrief
                    
                    Recent Sales Bills:
                    $salesBrief
                """.trimIndent()
            }

            val responseText = GeminiClient.generatePharmacySuggestions(systemPrompt, userPrompt)
            _aiResponse.value = responseText
            _aiIsLoading.value = false
        }
    }

    fun wipeAllDataExceptSystem(onComplete: () -> Unit = {}) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // 1. Wipe local Room database tables
                database.clearAllTables()
                
                // 2. Wipe Firebase dynamic children
                getFirebaseRef().child("medicines").removeValue()
                getFirebaseRef().child("bills").removeValue()
                getFirebaseRef().child("stock_in_records").removeValue()
                getFirebaseRef().child("pending_stock_ins").removeValue()
                getFirebaseRef().child("pending_medicines").removeValue()
                getFirebaseRef().child("customer_orders").removeValue()
                
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onComplete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onComplete()
                }
            }
        }
    }

    fun wipeAllDataAndProfiles(onComplete: () -> Unit = {}) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // 1. Wipe local Room database tables
                database.clearAllTables()
                
                // 2. Wipe Firebase nodes including users
                getFirebaseRef().child("medicines").removeValue()
                getFirebaseRef().child("bills").removeValue()
                getFirebaseRef().child("stock_in_records").removeValue()
                getFirebaseRef().child("pending_stock_ins").removeValue()
                getFirebaseRef().child("pending_medicines").removeValue()
                getFirebaseRef().child("customer_orders").removeValue()
                getFirebaseRef().child("users").removeValue()
                
                // 3. Clear local SharedPreferences
                sharedPrefs.edit().clear().apply()
                
                // 4. Reset VM state flows
                _userProfiles.value = emptyMap()
                _currentUser.value = UserProfile(
                    username = "admin",
                    pharmacyName = "আমার ফার্মেসী",
                    address = "সিলেট সিটি, বাংলাদেশ",
                    phone = "+৮৮০ ১৭০০-০০০০০০",
                    ownerName = "ফার্মেসী মালিক",
                    pass = "admin",
                    role = "admin"
                )
                _isLoggedIn.value = false
                
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onComplete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onComplete()
                }
            }
        }
    }
}

class PharmacyViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PharmacyViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PharmacyViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
