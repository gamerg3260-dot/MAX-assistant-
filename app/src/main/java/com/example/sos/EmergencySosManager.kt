package com.example.sos

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val addressName: String? = null,
    val mapsUrl: String = "https://maps.google.com/?q=$latitude,$longitude"
)

data class SosVoiceResult(
    val isHandled: Boolean,
    val feedbackMessage: String,
    val actionTaken: String? = null
)

/**
 * Manager for Live Location Tracking and Emergency SOS SMS Dispatcher.
 */
class EmergencySosManager private constructor(private val context: Context) {
    private val tag = "EmergencySosManager"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val prefs: SharedPreferences = context.getSharedPreferences("max_sos_prefs", Context.MODE_PRIVATE)
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val _currentLocation = MutableStateFlow<LocationData?>(null)
    val currentLocation: StateFlow<LocationData?> = _currentLocation.asStateFlow()

    private val _sosContacts = MutableStateFlow<List<String>>(emptyList())
    val sosContacts: StateFlow<List<String>> = _sosContacts.asStateFlow()

    private val _isDispatching = MutableStateFlow(false)
    val isDispatching: StateFlow<Boolean> = _isDispatching.asStateFlow()

    private val _lastSosStatus = MutableStateFlow<String?>("Emergency SOS Ready.")
    val lastSosStatus: StateFlow<String?> = _lastSosStatus.asStateFlow()

    private var locationListener: LocationListener? = null

    init {
        loadSosContacts()
        fetchCurrentLocation()
    }

    private fun loadSosContacts() {
        val savedString = prefs.getString("sos_contacts", "") ?: ""
        if (savedString.isNotBlank()) {
            _sosContacts.value = savedString.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            _sosContacts.value = emptyList()
        }
    }

    fun saveSosContacts(contacts: List<String>) {
        val cleanContacts = contacts.map { it.trim() }.filter { it.isNotEmpty() }
        _sosContacts.value = cleanContacts
        prefs.edit().putString("sos_contacts", cleanContacts.joinToString(",")).apply()
        _lastSosStatus.value = "Updated SOS contacts list (${cleanContacts.size} numbers)."
    }

    fun addSosContact(number: String) {
        val current = _sosContacts.value.toMutableList()
        val clean = number.trim()
        if (clean.isNotBlank() && !current.contains(clean)) {
            current.add(clean)
            saveSosContacts(current)
        }
    }

    fun removeSosContact(number: String) {
        val current = _sosContacts.value.toMutableList()
        if (current.remove(number)) {
            saveSosContacts(current)
        }
    }

    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Requests immediate GPS / Network location update.
     */
    @SuppressLint("MissingPermission")
    fun fetchCurrentLocation() {
        if (!hasLocationPermission()) {
            _lastSosStatus.value = "Location permission needed to access GPS coordinates."
            return
        }

        try {
            // Get last known location first as quick cache
            var lastLoc: Location? = null
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lastLoc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            }
            if (lastLoc == null && locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lastLoc = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            }

            if (lastLoc != null) {
                updateLocationState(lastLoc)
            }

            // Register single update listener for fresh location
            if (locationListener == null) {
                locationListener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        updateLocationState(location)
                        try {
                            locationManager.removeUpdates(this)
                        } catch (e: Exception) {
                            Log.e(tag, "Error removing location updates: ${e.message}")
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                }
            }

            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    5000L,
                    5f,
                    locationListener!!
                )
            } else if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    5000L,
                    5f,
                    locationListener!!
                )
            }
        } catch (e: Exception) {
            Log.e(tag, "Error fetching location: ${e.message}", e)
            _lastSosStatus.value = "Failed to access location: ${e.localizedMessage}"
        }
    }

    private fun updateLocationState(location: Location) {
        scope.launch {
            var addressText: String? = null
            try {
                if (Geocoder.isPresent()) {
                    val geocoder = Geocoder(context, Locale.getDefault())
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        geocoder.getFromLocation(location.latitude, location.longitude, 1) { addresses ->
                            if (!addresses.isNullOrEmpty()) {
                                val addr = addresses[0]
                                addressText = addr.getAddressLine(0) ?: "${addr.locality ?: ""}, ${addr.countryName ?: ""}"
                                _currentLocation.value = LocationData(
                                    latitude = location.latitude,
                                    longitude = location.longitude,
                                    accuracy = location.accuracy,
                                    addressName = addressText
                                )
                            }
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                        if (!addresses.isNullOrEmpty()) {
                            val addr = addresses[0]
                            addressText = addr.getAddressLine(0) ?: "${addr.locality ?: ""}, ${addr.countryName ?: ""}"
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(tag, "Geocoder failed: ${e.message}")
            }

            _currentLocation.value = LocationData(
                latitude = location.latitude,
                longitude = location.longitude,
                accuracy = location.accuracy,
                addressName = addressText
            )
            _lastSosStatus.value = "Updated GPS coordinates (${String.format(Locale.ROOT, "%.4f", location.latitude)}, ${String.format(Locale.ROOT, "%.4f", location.longitude)})."
        }
    }

    /**
     * Triggers Emergency SOS Alert dispatching live location SMS to all designated contacts.
     */
    fun dispatchSosAlert(customNote: String = ""): SosVoiceResult {
        if (!hasSmsPermission()) {
            _lastSosStatus.value = "SEND_SMS permission is required to send Emergency SOS alerts."
            return SosVoiceResult(
                isHandled = true,
                feedbackMessage = "SMS permission is missing. Please grant SMS permission to send SOS alerts.",
                actionTaken = "SMS_PERMISSION_NEEDED"
            )
        }

        val contacts = _sosContacts.value
        if (contacts.isEmpty()) {
            _lastSosStatus.value = "No emergency contacts configured. Please add contacts in SOS settings."
            return SosVoiceResult(
                isHandled = true,
                feedbackMessage = "No emergency contacts configured. Please add emergency contact numbers first.",
                actionTaken = "NO_CONTACTS"
            )
        }

        _isDispatching.value = true
        fetchCurrentLocation()

        val loc = _currentLocation.value
        val mapsUrl = loc?.mapsUrl ?: "Location updating..."
        val addressInfo = loc?.addressName?.let { " ($it)" } ?: ""
        val noteText = if (customNote.isNotBlank()) " Note: $customNote" else ""

        val sosMsg = "EMERGENCY SOS ALERT! I need immediate help from MAX Assistant. My current live location is: $mapsUrl$addressInfo$noteText"

        var sentCount = 0
        var failCount = 0

        try {
            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            contacts.forEach { phone ->
                try {
                    val parts = smsManager.divideMessage(sosMsg)
                    smsManager.sendMultipartTextMessage(phone, null, parts, null, null)
                    sentCount++
                } catch (e: Exception) {
                    Log.e(tag, "Failed to send SOS SMS to $phone: ${e.message}")
                    failCount++
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Critical failure during SOS SMS dispatch: ${e.message}", e)
        }

        _isDispatching.value = false

        val feedback = if (sentCount > 0) {
            "EMERGENCY SOS DISPATCHED! Sent emergency alert with live GPS coordinates to $sentCount contact(s)."
        } else {
            "Failed to send Emergency SOS SMS alert."
        }

        _lastSosStatus.value = feedback
        return SosVoiceResult(isHandled = true, feedbackMessage = feedback, actionTaken = "SOS_DISPATCHED")
    }

    /**
     * Parses voice triggers for Emergency SOS and Live Location requests.
     */
    fun processVoiceSosCommand(query: String): SosVoiceResult {
        val q = query.lowercase(Locale.ROOT).trim()

        // 1. SOS Emergency Triggers: "send sos", "emergency alert", "help me", "एसओएस भेजो", "इमरजेंसी"
        if (q.contains("send sos") || q.contains("emergency") || q.contains("sos alert") ||
            q.contains("help me") || q.contains("danger") || q.contains("एसओएस भेजो") ||
            q.contains("इमरजेंसी अलर्ट") || q.contains("मदद करो")) {
            return dispatchSosAlert()
        }

        // 2. Location Requests: "send my location", "where am i", "share location", "मेरी लोकेशन भेजो"
        if (q.contains("send my location") || q.contains("share location") || q.contains("where am i") ||
            q.contains("my location") || q.contains("लोकेशन बताओ") || q.contains("लोकेशन शेयर")) {
            fetchCurrentLocation()
            val loc = _currentLocation.value
            return if (loc != null) {
                val addrStr = loc.addressName ?: "Lat: ${String.format(Locale.ROOT, "%.4f", loc.latitude)}, Lng: ${String.format(Locale.ROOT, "%.4f", loc.longitude)}"
                SosVoiceResult(
                    isHandled = true,
                    feedbackMessage = "Your current location is $addrStr. Google Maps link: ${loc.mapsUrl}",
                    actionTaken = "LOCATION_FOUND"
                )
            } else {
                SosVoiceResult(
                    isHandled = true,
                    feedbackMessage = "Accessing GPS location... Please try again in a moment.",
                    actionTaken = "LOCATION_FETCHING"
                )
            }
        }

        return SosVoiceResult(false, "Command not recognized as Emergency SOS trigger.")
    }

    companion object {
        @Volatile
        private var INSTANCE: EmergencySosManager? = null

        fun getInstance(context: Context): EmergencySosManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: EmergencySosManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
