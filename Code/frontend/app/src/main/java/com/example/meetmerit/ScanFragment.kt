package com.example.meetmerit

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.util.UUID

class ScanFragment : Fragment() {

    private enum class SocialUiState {
        DISABLED,
        ENABLED,
        SCANNING,
        FOUND,
        CONFIRMED
    }

    private lateinit var nearbyStudentAdapter: NearbyStudentAdapter
    private lateinit var socialInboxAdapter: SocialInboxAdapter
    private lateinit var friendAdapter: FriendAdapter
    private lateinit var switchSocialEnabled: SwitchMaterial
    private lateinit var btnScan: MaterialButton
    private lateinit var progressBar: ProgressBar
    private lateinit var tvInteractionsCount: TextView
    private lateinit var tvConfirmedMessage: TextView
    private lateinit var tvRequestsLabel: TextView
    private lateinit var tvRequestsEmpty: TextView
    private lateinit var tvNearbyLabel: TextView
    private lateinit var tvEmptyState: TextView
    private lateinit var tvFriendsLabel: TextView
    private lateinit var tvFriendsEmpty: TextView
    private lateinit var rvRequests: RecyclerView
    private lateinit var rvDevices: RecyclerView
    private lateinit var rvFriends: RecyclerView
    private lateinit var layoutSocialDisabled: View
    private lateinit var layoutSocialEnabled: View
    private lateinit var cardScanningState: MaterialCardView
    private lateinit var cardConfirmedState: MaterialCardView

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothAdvertiser: BluetoothLeAdvertiser? = null
    private var currentUserId: Int = -1
    private var isScanning = false
    private var isAdvertising = false
    private var isStartingPresence = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val nearbyStudentsByToken = linkedMapOf<String, NearbyStudent>()
    private val pendingResolutionTokens = mutableSetOf<String>()
    private val incomingRequestsByStudentId = mutableMapOf<Int, SocialConnectionRequest>()
    private val outgoingRequestsByStudentId = mutableMapOf<Int, SocialConnectionRequest>()
    private val acceptedRequestsByStudentId = mutableMapOf<Int, SocialConnectionRequest>()
    private val resolvingInboxStudentIds = mutableSetOf<Int>()
    private val friendsByStudentId = mutableMapOf<Int, SocialFriendSummary>()
    private val friendStudentIds = mutableSetOf<Int>()

    private var interactionsToday: Int = 0
    private var socialEnabled: Boolean = false
    private var uiState: SocialUiState = SocialUiState.DISABLED
    private var pendingScanAfterPermission: Boolean = false
    private var advertisedToken: String? = null
    private var serviceUuid: UUID = DEFAULT_SERVICE_UUID

    private val scanTimeoutRunnable = Runnable {
        stopBleScan()
        renderState(SocialUiState.FOUND)
    }

    private val staleCleanupRunnable = object : Runnable {
        override fun run() {
            pruneStaleNearbyStudents()
            if (socialEnabled) {
                mainHandler.postDelayed(this, STALE_CLEANUP_INTERVAL_MS)
            }
        }
    }

    private val refreshPresenceRunnable = Runnable {
        refreshSocialPresence()
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.entries.all { it.value }
            if (allGranted) {
                ensureSocialModeActive(startScanAfter = pendingScanAfterPermission)
            } else {
                Toast.makeText(
                    context,
                    "Bluetooth permissions are required to use the social feature.",
                    Toast.LENGTH_SHORT,
                ).show()
                renderState(if (socialEnabled) SocialUiState.ENABLED else SocialUiState.DISABLED)
            }
            pendingScanAfterPermission = false
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = inflater.inflate(R.layout.fragment_scan, container, false)

        switchSocialEnabled = view.findViewById(R.id.switchSocialEnabled)
        btnScan = view.findViewById(R.id.btnStartScan)
        progressBar = view.findViewById(R.id.progressScan)
        tvInteractionsCount = view.findViewById(R.id.tvInteractionsCount)
        tvConfirmedMessage = view.findViewById(R.id.tvConfirmedMessage)
        tvRequestsLabel = view.findViewById(R.id.tvRequestsLabel)
        tvRequestsEmpty = view.findViewById(R.id.tvRequestsEmpty)
        tvNearbyLabel = view.findViewById(R.id.tvNearbyLabel)
        tvEmptyState = view.findViewById(R.id.tvEmptyState)
        tvFriendsLabel = view.findViewById(R.id.tvFriendsLabel)
        tvFriendsEmpty = view.findViewById(R.id.tvFriendsEmpty)
        rvRequests = view.findViewById(R.id.rvRequests)
        rvDevices = view.findViewById(R.id.rvDevices)
        rvFriends = view.findViewById(R.id.rvFriends)
        layoutSocialDisabled = view.findViewById(R.id.layoutSocialDisabled)
        layoutSocialEnabled = view.findViewById(R.id.layoutSocialEnabled)
        cardScanningState = view.findViewById(R.id.cardScanningState)
        cardConfirmedState = view.findViewById(R.id.cardConfirmedState)

        val prefs = requireActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        currentUserId = prefs.getInt(PREF_USER_ID, -1)
        socialEnabled = prefs.getBoolean(PREF_SOCIAL_ENABLED, false)
        interactionsToday = prefs.getInt(PREF_SOCIAL_INTERACTIONS, 0)

        val bluetoothManager =
            requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        bluetoothAdvertiser = bluetoothAdapter?.bluetoothLeAdvertiser

        nearbyStudentAdapter = NearbyStudentAdapter(emptyList()) { student ->
            onNearbyStudentSelected(student)
        }
        socialInboxAdapter = SocialInboxAdapter(
            items = emptyList(),
            onPrimaryAction = { item -> onInboxPrimaryAction(item) },
            onRejectAction = { item -> rejectConnectionRequest(item) },
        )
        friendAdapter = FriendAdapter(emptyList())

        rvRequests.layoutManager = LinearLayoutManager(context)
        rvRequests.adapter = socialInboxAdapter
        rvDevices.layoutManager = LinearLayoutManager(context)
        rvDevices.adapter = nearbyStudentAdapter
        rvFriends.layoutManager = LinearLayoutManager(context)
        rvFriends.adapter = friendAdapter

        switchSocialEnabled.isChecked = socialEnabled
        switchSocialEnabled.setOnCheckedChangeListener { _, isChecked ->
            socialEnabled = isChecked
            saveSocialPrefs()
            if (!isChecked) {
                shutdownSocialRuntime(clearNearby = true, stopServerPresence = true)
                renderState(SocialUiState.DISABLED)
            } else {
                renderState(SocialUiState.ENABLED)
                checkPermissionsAndPrepareSocial(startScanAfter = false)
            }
        }

        btnScan.setOnClickListener {
            checkPermissionsAndPrepareSocial(startScanAfter = true)
        }

        tvInteractionsCount.text = interactionsToday.toString()
        renderState(if (socialEnabled) SocialUiState.ENABLED else SocialUiState.DISABLED)
        if (socialEnabled) {
            checkPermissionsAndPrepareSocial(startScanAfter = false)
        }
        return view
    }

    private fun renderState(state: SocialUiState) {
        uiState = state
        tvInteractionsCount.text = interactionsToday.toString()
        layoutSocialDisabled.visibility = if (state == SocialUiState.DISABLED) View.VISIBLE else View.GONE
        layoutSocialEnabled.visibility = if (state != SocialUiState.DISABLED) View.VISIBLE else View.GONE
        cardScanningState.visibility = if (state == SocialUiState.SCANNING) View.VISIBLE else View.GONE
        cardConfirmedState.visibility = if (state == SocialUiState.CONFIRMED) View.VISIBLE else View.GONE

        val socialSectionsVisible = state != SocialUiState.DISABLED
        val showNearbyEmpty = socialSectionsVisible && state != SocialUiState.SCANNING && !nearbyStudentAdapter.hasStudents()
        val showRequestsEmpty = socialSectionsVisible && !socialInboxAdapter.hasItems()
        val showFriendsEmpty = socialSectionsVisible && !friendAdapter.hasFriends()

        tvRequestsLabel.visibility = if (socialSectionsVisible) View.VISIBLE else View.GONE
        rvRequests.visibility = if (socialSectionsVisible && socialInboxAdapter.hasItems()) View.VISIBLE else View.GONE
        tvRequestsEmpty.visibility = if (showRequestsEmpty) View.VISIBLE else View.GONE

        tvNearbyLabel.visibility = if (socialSectionsVisible) View.VISIBLE else View.GONE
        rvDevices.visibility = if (socialSectionsVisible && nearbyStudentAdapter.hasStudents()) View.VISIBLE else View.GONE
        tvEmptyState.visibility = if (showNearbyEmpty) View.VISIBLE else View.GONE
        tvEmptyState.text = "No nearby Meet & Merit students found yet."

        tvFriendsLabel.visibility = if (socialSectionsVisible) View.VISIBLE else View.GONE
        rvFriends.visibility = if (socialSectionsVisible && friendAdapter.hasFriends()) View.VISIBLE else View.GONE
        tvFriendsEmpty.visibility = if (showFriendsEmpty) View.VISIBLE else View.GONE

        when (state) {
            SocialUiState.DISABLED -> {
                btnScan.text = "Find nearby students"
                btnScan.isEnabled = false
                progressBar.visibility = View.GONE
            }
            SocialUiState.ENABLED -> {
                btnScan.text = "Find nearby students"
                btnScan.isEnabled = socialEnabled
                progressBar.visibility = View.GONE
            }
            SocialUiState.SCANNING -> {
                progressBar.visibility = View.VISIBLE
                btnScan.isEnabled = false
                tvEmptyState.visibility = View.GONE
            }
            SocialUiState.FOUND -> {
                progressBar.visibility = View.GONE
                btnScan.text = "Scan again"
                btnScan.isEnabled = true
            }
            SocialUiState.CONFIRMED -> {
                progressBar.visibility = View.GONE
                btnScan.text = "Find more students"
                btnScan.isEnabled = true
            }
        }
    }

    private fun saveSocialPrefs() {
        val prefs = requireActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(PREF_SOCIAL_ENABLED, socialEnabled)
            .putInt(PREF_SOCIAL_INTERACTIONS, interactionsToday)
            .apply()
    }

    private fun checkPermissionsAndPrepareSocial(startScanAfter: Boolean) {
        if (!socialEnabled) {
            Toast.makeText(context, "Enable the social feature first.", Toast.LENGTH_SHORT).show()
            return
        }
        if (currentUserId <= 0) {
            Toast.makeText(context, "Please log in again to use nearby social.", Toast.LENGTH_SHORT).show()
            return
        }

        val notGranted = requiredPermissions().filter {
            ActivityCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
        }
        if (notGranted.isNotEmpty()) {
            pendingScanAfterPermission = startScanAfter
            requestPermissionLauncher.launch(notGranted.toTypedArray())
            return
        }
        ensureSocialModeActive(startScanAfter)
    }

    private fun requiredPermissions(): List<String> {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions += Manifest.permission.BLUETOOTH_SCAN
            permissions += Manifest.permission.BLUETOOTH_CONNECT
            permissions += Manifest.permission.BLUETOOTH_ADVERTISE
        } else {
            permissions += Manifest.permission.ACCESS_FINE_LOCATION
        }
        return permissions
    }

    private fun ensureSocialModeActive(startScanAfter: Boolean) {
        if (!isBluetoothReady()) {
            renderState(SocialUiState.ENABLED)
            return
        }
        startStaleCleanup()
        fetchSocialRequests()
        fetchFriendList()
        if (advertisedToken != null && isAdvertising) {
            if (startScanAfter) {
                startBleScan()
            } else if (uiState != SocialUiState.CONFIRMED) {
                renderState(if (nearbyStudentAdapter.hasStudents()) SocialUiState.FOUND else SocialUiState.ENABLED)
            }
            return
        }
        startSocialPresence(startScanAfter)
    }

    private fun isBluetoothReady(): Boolean {
        if (bluetoothAdapter == null || bluetoothAdapter?.isEnabled != true) {
            Toast.makeText(context, "Please enable Bluetooth first.", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun startSocialPresence(startScanAfter: Boolean) {
        if (isStartingPresence) {
            return
        }
        isStartingPresence = true
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = RetrofitClient.instance.startSocialPresence(currentUserId)
                withContext(Dispatchers.Main) {
                    isStartingPresence = false
                    serviceUuid = runCatching {
                        UUID.fromString(response.serviceUuid)
                    }.getOrDefault(DEFAULT_SERVICE_UUID)
                    advertisedToken = response.token
                    startBleAdvertising(response.token)
                    schedulePresenceRefresh(response.ttlSeconds)
                    if (startScanAfter) {
                        startBleScan()
                    } else if (uiState != SocialUiState.CONFIRMED) {
                        renderState(if (nearbyStudentAdapter.hasStudents()) SocialUiState.FOUND else SocialUiState.ENABLED)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isStartingPresence = false
                    Toast.makeText(
                        context,
                        "Could not start social mode: ${e.message}",
                        Toast.LENGTH_SHORT,
                    ).show()
                    renderState(SocialUiState.ENABLED)
                }
            }
        }
    }

    private fun refreshSocialPresence() {
        if (!socialEnabled || currentUserId <= 0) {
            return
        }
        startSocialPresence(startScanAfter = false)
    }

    private fun schedulePresenceRefresh(ttlSeconds: Int) {
        mainHandler.removeCallbacks(refreshPresenceRunnable)
        val refreshDelayMs = (ttlSeconds.coerceAtLeast(30) - 20).coerceAtLeast(15) * 1000L
        mainHandler.postDelayed(refreshPresenceRunnable, refreshDelayMs)
    }

    private fun startStaleCleanup() {
        mainHandler.removeCallbacks(staleCleanupRunnable)
        mainHandler.postDelayed(staleCleanupRunnable, STALE_CLEANUP_INTERVAL_MS)
    }

    private fun pruneStaleNearbyStudents() {
        val cutoff = System.currentTimeMillis() - STUDENT_STALE_AFTER_MS
        val iterator = nearbyStudentsByToken.iterator()
        var removed = false
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value.lastSeenAt < cutoff) {
                iterator.remove()
                pendingResolutionTokens.remove(entry.key)
                removed = true
            }
        }
        if (removed) {
            updateNearbyStudentList()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startBleAdvertising(token: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.BLUETOOTH_ADVERTISE,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        if (bluetoothAdapter?.isMultipleAdvertisementSupported != true || bluetoothAdvertiser == null) {
            Toast.makeText(
                context,
                "This device can scan nearby students but cannot broadcast social presence.",
                Toast.LENGTH_SHORT,
            ).show()
            return
        }

        stopBleAdvertising()
        val parcelUuid = ParcelUuid(serviceUuid)
        val serviceData = hexTokenToBytes(token)
        if (serviceData == null) {
            Toast.makeText(context, "Could not prepare social presence token.", Toast.LENGTH_SHORT).show()
            isAdvertising = false
            return
        }
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(false)
            .build()
        val data = AdvertiseData.Builder()
            .addServiceData(parcelUuid, serviceData)
            .setIncludeDeviceName(false)
            .build()

        try {
            bluetoothAdvertiser?.startAdvertising(settings, data, advertiseCallback)
            isAdvertising = true
        } catch (_: Exception) {
            Toast.makeText(context, "Could not start BLE advertising.", Toast.LENGTH_SHORT).show()
            isAdvertising = false
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopBleAdvertising() {
        if (!isAdvertising) {
            return
        }
        runCatching {
            bluetoothAdvertiser?.stopAdvertising(advertiseCallback)
        }
        isAdvertising = false
    }

    @SuppressLint("MissingPermission")
    private fun startBleScan() {
        if (!isBluetoothReady() || isScanning) {
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.BLUETOOTH_SCAN,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S &&
            ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        nearbyStudentsByToken.clear()
        pendingResolutionTokens.clear()
        updateNearbyStudentList()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            val scanner = bluetoothAdapter?.bluetoothLeScanner
            if (scanner == null) {
                Toast.makeText(context, "BLE scanning is not available on this device.", Toast.LENGTH_SHORT).show()
                renderState(SocialUiState.ENABLED)
                return
            }
            isScanning = true
            renderState(SocialUiState.SCANNING)
            scanner.startScan(null, settings, leScanCallback)
            mainHandler.removeCallbacks(scanTimeoutRunnable)
            mainHandler.postDelayed(scanTimeoutRunnable, SCAN_PERIOD_MS)
        } catch (e: Exception) {
            isScanning = false
            Toast.makeText(context, "Could not start scan: ${e.message}", Toast.LENGTH_SHORT).show()
            renderState(SocialUiState.ENABLED)
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopBleScan() {
        if (!isScanning) {
            return
        }
        isScanning = false
        mainHandler.removeCallbacks(scanTimeoutRunnable)
        runCatching {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(leScanCallback)
        }
    }

    private fun onNearbyStudentSelected(student: NearbyStudent) {
        when (student.action) {
            NearbyStudentAction.CONNECT -> sendConnectionRequest(student)
            NearbyStudentAction.ACCEPT -> acceptConnectionRequest(
                requestId = student.requestId ?: return,
                studentId = student.studentId ?: return,
                username = student.username,
                token = student.token,
            )
            NearbyStudentAction.ADD_FRIEND -> addFriend(
                requestId = student.requestId ?: return,
                studentId = student.studentId ?: return,
                username = student.username,
                token = student.token,
            )
            NearbyStudentAction.PENDING ->
                Toast.makeText(context, "Connection request already pending.", Toast.LENGTH_SHORT).show()
            NearbyStudentAction.FRIEND ->
                Toast.makeText(context, "You're already friends.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun onInboxPrimaryAction(item: SocialInboxItem) {
        when (item.primaryAction) {
            SocialInboxPrimaryAction.ACCEPT -> acceptConnectionRequest(
                requestId = item.requestId,
                studentId = item.studentId,
                username = item.username,
            )
            SocialInboxPrimaryAction.ADD_FRIEND -> addFriend(
                requestId = item.requestId,
                studentId = item.studentId,
                username = item.username,
            )
        }
    }

    private fun sendConnectionRequest(student: NearbyStudent) {
        val studentId = student.studentId ?: return
        updateStudentResolvingState(student.token, true)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = RetrofitClient.instance.connectToStudent(
                    currentUserId,
                    SocialConnectRequest(targetStudentId = studentId, rssi = student.rssi),
                )
                withContext(Dispatchers.Main) {
                    outgoingRequestsByStudentId[studentId] = response.request
                    refreshNearbyStudentActions()
                    updateInboxList()
                    showConfirmationMessage("Connection request sent to ${student.username}.")
                }
            } catch (e: HttpException) {
                withContext(Dispatchers.Main) {
                    fetchSocialRequests()
                    Toast.makeText(context, "Could not send request right now.", Toast.LENGTH_SHORT).show()
                    updateStudentResolvingState(student.token, false)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Failed to send request: ${e.message}",
                        Toast.LENGTH_SHORT,
                    ).show()
                    updateStudentResolvingState(student.token, false)
                }
            }
        }
    }

    private fun acceptConnectionRequest(
        requestId: Int,
        studentId: Int,
        username: String,
        token: String? = null,
    ) {
        setResolvingState(token, studentId, true)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = RetrofitClient.instance.confirmSocialRequest(
                    currentUserId,
                    SocialRespondRequest(requestId = requestId, action = "accept"),
                )
                withContext(Dispatchers.Main) {
                    val otherStudentId = counterpartStudentId(response.request)
                    interactionsToday += 1
                    saveSocialPrefs()
                    acceptedRequestsByStudentId[otherStudentId] = response.request
                    incomingRequestsByStudentId.remove(otherStudentId)
                    outgoingRequestsByStudentId.remove(otherStudentId)
                    setResolvingState(token, otherStudentId, false)
                    refreshNearbyStudentActions()
                    updateInboxList()
                    val rewardMessage = if (response.xpAwarded) " +5 XP awarded." else ""
                    showConfirmationMessage("You connected with $username.$rewardMessage")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Failed to accept request: ${e.message}",
                        Toast.LENGTH_SHORT,
                    ).show()
                    setResolvingState(token, studentId, false)
                }
            }
        }
    }

    private fun rejectConnectionRequest(item: SocialInboxItem) {
        setResolvingState(token = null, studentId = item.studentId, isResolving = true)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                RetrofitClient.instance.confirmSocialRequest(
                    currentUserId,
                    SocialRespondRequest(requestId = item.requestId, action = "reject"),
                )
                withContext(Dispatchers.Main) {
                    incomingRequestsByStudentId.remove(item.studentId)
                    setResolvingState(token = null, studentId = item.studentId, isResolving = false)
                    updateInboxList()
                    refreshNearbyStudentActions()
                    Toast.makeText(context, "Rejected ${item.username}'s request.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    setResolvingState(token = null, studentId = item.studentId, isResolving = false)
                    Toast.makeText(
                        context,
                        "Failed to reject request: ${e.message}",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }

    private fun addFriend(
        requestId: Int,
        studentId: Int,
        username: String,
        token: String? = null,
    ) {
        setResolvingState(token, studentId, true)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                RetrofitClient.instance.addFriend(currentUserId, SocialFriendRequest(requestId))
                withContext(Dispatchers.Main) {
                    friendStudentIds += studentId
                    setResolvingState(token, studentId, false)
                    refreshNearbyStudentActions()
                    updateInboxList()
                    fetchFriendList()
                    showConfirmationMessage("$username was added to your friends.")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    setResolvingState(token, studentId, false)
                    Toast.makeText(
                        context,
                        "Could not add friend: ${e.message}",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }

    private fun showConfirmationMessage(message: String) {
        tvConfirmedMessage.text = message
        renderState(SocialUiState.CONFIRMED)
    }

    private fun fetchSocialRequests() {
        if (currentUserId <= 0) {
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = RetrofitClient.instance.getSocialRequests(currentUserId)
                withContext(Dispatchers.Main) {
                    incomingRequestsByStudentId.clear()
                    outgoingRequestsByStudentId.clear()
                    acceptedRequestsByStudentId.clear()

                    response.incoming.forEach { request ->
                        when (request.status) {
                            REQUEST_PENDING -> incomingRequestsByStudentId[request.fromStudentId] = request
                            REQUEST_ACCEPTED -> acceptedRequestsByStudentId[request.fromStudentId] = request
                        }
                    }
                    response.outgoing.forEach { request ->
                        when (request.status) {
                            REQUEST_PENDING -> outgoingRequestsByStudentId[request.toStudentId] = request
                            REQUEST_ACCEPTED -> acceptedRequestsByStudentId[request.toStudentId] = request
                        }
                    }
                    updateInboxList()
                    refreshNearbyStudentActions()
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun fetchFriendList() {
        if (currentUserId <= 0) {
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = RetrofitClient.instance.getFriends(currentUserId)
                withContext(Dispatchers.Main) {
                    friendsByStudentId.clear()
                    friendStudentIds.clear()
                    response.friends.forEach { friend ->
                        friendsByStudentId[friend.friendId] = friend
                        friendStudentIds += friend.friendId
                    }
                    updateFriendList()
                    updateInboxList()
                    refreshNearbyStudentActions()
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun refreshNearbyStudentActions() {
        val refreshed = nearbyStudentsByToken.mapValues { (_, student) ->
            val studentId = student.studentId
            if (studentId == null) {
                student
            } else {
                val (action, requestId) = deriveActionState(studentId)
                student.copy(action = action, requestId = requestId, isResolving = false)
            }
        }
        nearbyStudentsByToken.clear()
        nearbyStudentsByToken.putAll(refreshed)
        updateNearbyStudentList()
    }

    private fun deriveActionState(studentId: Int): Pair<NearbyStudentAction, Int?> {
        if (friendStudentIds.contains(studentId)) {
            val acceptedRequest = acceptedRequestsByStudentId[studentId]
            return NearbyStudentAction.FRIEND to acceptedRequest?.id
        }
        incomingRequestsByStudentId[studentId]?.let { return NearbyStudentAction.ACCEPT to it.id }
        outgoingRequestsByStudentId[studentId]?.let { return NearbyStudentAction.PENDING to it.id }
        acceptedRequestsByStudentId[studentId]?.let { return NearbyStudentAction.ADD_FRIEND to it.id }
        return NearbyStudentAction.CONNECT to null
    }

    private fun setResolvingState(token: String?, studentId: Int, isResolving: Boolean) {
        token?.let { updateStudentResolvingState(it, isResolving) }
        if (isResolving) {
            resolvingInboxStudentIds += studentId
        } else {
            resolvingInboxStudentIds.remove(studentId)
        }
        updateInboxList()
    }

    private fun updateStudentResolvingState(token: String, isResolving: Boolean) {
        val student = nearbyStudentsByToken[token] ?: return
        nearbyStudentsByToken[token] = student.copy(isResolving = isResolving)
        updateNearbyStudentList()
    }

    private fun updateNearbyStudentList() {
        val sortedStudents = nearbyStudentsByToken.values
            .sortedWith(compareByDescending<NearbyStudent> { it.rssi }.thenByDescending { it.lastSeenAt })
        nearbyStudentAdapter.updateData(sortedStudents)
        if (uiState != SocialUiState.DISABLED) {
            renderState(uiState)
        }
    }

    private fun updateInboxList() {
        val items = mutableListOf<SocialInboxItem>()

        incomingRequestsByStudentId.values
            .sortedByDescending { it.createdAt }
            .forEach { request ->
                val studentId = counterpartStudentId(request)
                items += SocialInboxItem(
                    requestId = request.id,
                    studentId = studentId,
                    username = counterpartUsername(request),
                    avatarUrl = counterpartAvatarUrl(request),
                    department = counterpartDepartment(request),
                    yearOfStudy = counterpartYearOfStudy(request),
                    subtitleSuffix = "Wants to connect",
                    primaryAction = SocialInboxPrimaryAction.ACCEPT,
                    showReject = true,
                    isResolving = resolvingInboxStudentIds.contains(studentId),
                )
            }

        acceptedRequestsByStudentId.values
            .filter { !friendStudentIds.contains(counterpartStudentId(it)) }
            .sortedByDescending { it.respondedAt ?: it.createdAt }
            .forEach { request ->
                val studentId = counterpartStudentId(request)
                items += SocialInboxItem(
                    requestId = request.id,
                    studentId = studentId,
                    username = counterpartUsername(request),
                    avatarUrl = counterpartAvatarUrl(request),
                    department = counterpartDepartment(request),
                    yearOfStudy = counterpartYearOfStudy(request),
                    subtitleSuffix = "Connection accepted",
                    primaryAction = SocialInboxPrimaryAction.ADD_FRIEND,
                    isResolving = resolvingInboxStudentIds.contains(studentId),
                )
            }

        socialInboxAdapter.updateData(items)
        if (uiState != SocialUiState.DISABLED) {
            renderState(uiState)
        }
    }

    private fun updateFriendList() {
        val friends = friendsByStudentId.values
            .sortedBy { it.username.lowercase() }
            .map { friend ->
                SocialFriendItem(
                    friendshipId = friend.id,
                    friendId = friend.friendId,
                    username = friend.username,
                    avatarUrl = friend.avatarUrl,
                    department = friend.department,
                    yearOfStudy = friend.yearOfStudy,
                )
            }
        friendAdapter.updateData(friends)
        if (uiState != SocialUiState.DISABLED) {
            renderState(uiState)
        }
    }

    private fun counterpartStudentId(request: SocialConnectionRequest): Int {
        return if (request.fromStudentId == currentUserId) request.toStudentId else request.fromStudentId
    }

    private fun counterpartUsername(request: SocialConnectionRequest): String {
        return if (request.fromStudentId == currentUserId) request.toUsername else request.fromUsername
    }

    private fun counterpartDepartment(request: SocialConnectionRequest): String {
        return if (request.fromStudentId == currentUserId) request.toDepartment else request.fromDepartment
    }

    private fun counterpartAvatarUrl(request: SocialConnectionRequest): String? {
        return if (request.fromStudentId == currentUserId) request.toAvatarUrl else request.fromAvatarUrl
    }

    private fun counterpartYearOfStudy(request: SocialConnectionRequest): Int? {
        return if (request.fromStudentId == currentUserId) request.toYearOfStudy else request.fromYearOfStudy
    }

    @SuppressLint("MissingPermission")
    private val leScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val serviceData = result.scanRecord?.getServiceData(ParcelUuid(serviceUuid)) ?: return
            val token = bytesToHexToken(serviceData)
            if (token.isBlank() || token == advertisedToken) {
                return
            }

            val existing = nearbyStudentsByToken[token]
            val smoothedRssi = if (existing == null) result.rssi else (existing.rssi + result.rssi) / 2
            nearbyStudentsByToken[token] = (existing ?: NearbyStudent(token = token, isResolving = true)).copy(
                rssi = smoothedRssi,
                lastSeenAt = System.currentTimeMillis(),
                isResolving = existing?.studentId == null,
            )

            activity?.runOnUiThread {
                updateNearbyStudentList()
            }

            if (existing?.studentId == null && pendingResolutionTokens.add(token)) {
                resolveNearbyStudent(token)
            }
        }
    }

    private fun resolveNearbyStudent(token: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = RetrofitClient.instance.resolveSocialToken(SocialResolveRequest(token))
                if (response.studentId == currentUserId) {
                    withContext(Dispatchers.Main) {
                        nearbyStudentsByToken.remove(token)
                        pendingResolutionTokens.remove(token)
                        updateNearbyStudentList()
                    }
                    return@launch
                }
                withContext(Dispatchers.Main) {
                    val existing = nearbyStudentsByToken[token] ?: return@withContext
                    val (action, requestId) = deriveActionState(response.studentId)
                    nearbyStudentsByToken[token] = existing.copy(
                        studentId = response.studentId,
                        username = response.username,
                        avatarUrl = response.avatarUrl,
                        department = response.department,
                        yearOfStudy = response.yearOfStudy,
                        requestId = requestId,
                        action = action,
                        isResolving = false,
                    )
                    pendingResolutionTokens.remove(token)
                    updateNearbyStudentList()
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    nearbyStudentsByToken.remove(token)
                    pendingResolutionTokens.remove(token)
                    updateNearbyStudentList()
                }
            }
        }
    }

    private fun shutdownSocialRuntime(clearNearby: Boolean, stopServerPresence: Boolean) {
        stopBleScan()
        stopBleAdvertising()
        mainHandler.removeCallbacks(scanTimeoutRunnable)
        mainHandler.removeCallbacks(staleCleanupRunnable)
        mainHandler.removeCallbacks(refreshPresenceRunnable)
        advertisedToken = null
        pendingResolutionTokens.clear()
        isStartingPresence = false

        if (clearNearby) {
            nearbyStudentsByToken.clear()
            incomingRequestsByStudentId.clear()
            outgoingRequestsByStudentId.clear()
            acceptedRequestsByStudentId.clear()
            resolvingInboxStudentIds.clear()
            friendsByStudentId.clear()
            friendStudentIds.clear()
            updateInboxList()
            updateFriendList()
            updateNearbyStudentList()
        }

        if (stopServerPresence && currentUserId > 0) {
            CoroutineScope(Dispatchers.IO).launch {
                runCatching { RetrofitClient.instance.stopSocialPresence(currentUserId) }
            }
        }
    }

    override fun onDestroyView() {
        shutdownSocialRuntime(clearNearby = false, stopServerPresence = true)
        super.onDestroyView()
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            isAdvertising = true
        }

        override fun onStartFailure(errorCode: Int) {
            isAdvertising = false
            activity?.runOnUiThread {
                val message = when (errorCode) {
                    ADVERTISE_FAILED_DATA_TOO_LARGE ->
                        "Bluetooth payload is too large for advertising."
                    ADVERTISE_FAILED_TOO_MANY_ADVERTISERS ->
                        "This device is already running too many Bluetooth advertisers."
                    ADVERTISE_FAILED_FEATURE_UNSUPPORTED ->
                        "This device does not support Bluetooth advertising."
                    ADVERTISE_FAILED_INTERNAL_ERROR ->
                        "Bluetooth advertising failed because of an internal error."
                    ADVERTISE_FAILED_ALREADY_STARTED ->
                        "Bluetooth advertising is already running."
                    else -> "Bluetooth advertising failed."
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun hexTokenToBytes(token: String): ByteArray? {
        if (token.length % 2 != 0) {
            return null
        }
        return runCatching {
            ByteArray(token.length / 2) { index ->
                token.substring(index * 2, index * 2 + 2).toInt(16).toByte()
            }
        }.getOrNull()
    }

    private fun bytesToHexToken(bytes: ByteArray): String {
        return bytes.joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }

    companion object {
        private const val PREF_USER_ID = "USER_ID"
        private const val PREF_SOCIAL_ENABLED = "SOCIAL_ENABLED"
        private const val PREF_SOCIAL_INTERACTIONS = "SOCIAL_INTERACTIONS"
        private const val REQUEST_PENDING = "pending"
        private const val REQUEST_ACCEPTED = "accepted"
        private const val SCAN_PERIOD_MS = 10_000L
        private const val STALE_CLEANUP_INTERVAL_MS = 5_000L
        private const val STUDENT_STALE_AFTER_MS = 30_000L
        private val DEFAULT_SERVICE_UUID = UUID.fromString("8d0c5a5e-4e7b-4c2f-8c0d-3d5d89d8f321")
    }
}
