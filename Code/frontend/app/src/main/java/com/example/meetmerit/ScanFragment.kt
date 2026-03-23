package com.example.meetmerit

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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

class ScanFragment : Fragment() {

    private enum class SocialUiState {
        DISABLED,
        ENABLED,
        SCANNING,
        FOUND,
        CONFIRMED
    }

    private lateinit var deviceAdapter: DeviceAdapter
    private lateinit var switchSocialEnabled: SwitchMaterial
    private lateinit var btnScan: MaterialButton
    private lateinit var progressBar: ProgressBar
    private lateinit var tvInteractionsCount: TextView
    private lateinit var tvConfirmedMessage: TextView
    private lateinit var tvNearbyLabel: TextView
    private lateinit var tvEmptyState: TextView
    private lateinit var rvDevices: RecyclerView

    private lateinit var layoutSocialDisabled: View
    private lateinit var layoutSocialEnabled: View
    private lateinit var cardScanningState: MaterialCardView
    private lateinit var cardConfirmedState: MaterialCardView

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var isScanning = false
    private val handler = Handler(Looper.getMainLooper())
    private val scanPeriod: Long = 10000

    private var interactionsToday: Int = 0
    private var socialEnabled: Boolean = false
    private var uiState: SocialUiState = SocialUiState.DISABLED

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.entries.all { it.value }
            if (allGranted) {
                startBleScan()
            } else {
                Toast.makeText(
                    context,
                    "Bluetooth permissions are required to use the social feature.",
                    Toast.LENGTH_SHORT
                ).show()
                renderState(SocialUiState.ENABLED)
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_scan, container, false)

        switchSocialEnabled = view.findViewById(R.id.switchSocialEnabled)
        btnScan = view.findViewById(R.id.btnStartScan)
        progressBar = view.findViewById(R.id.progressScan)
        tvInteractionsCount = view.findViewById(R.id.tvInteractionsCount)
        tvConfirmedMessage = view.findViewById(R.id.tvConfirmedMessage)
        tvNearbyLabel = view.findViewById(R.id.tvNearbyLabel)
        tvEmptyState = view.findViewById(R.id.tvEmptyState)
        rvDevices = view.findViewById(R.id.rvDevices)

        layoutSocialDisabled = view.findViewById(R.id.layoutSocialDisabled)
        layoutSocialEnabled = view.findViewById(R.id.layoutSocialEnabled)
        cardScanningState = view.findViewById(R.id.cardScanningState)
        cardConfirmedState = view.findViewById(R.id.cardConfirmedState)

        val prefs = requireActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        socialEnabled = prefs.getBoolean(PREF_SOCIAL_ENABLED, false)
        interactionsToday = prefs.getInt(PREF_SOCIAL_INTERACTIONS, 0)

        val bluetoothManager =
            context?.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        deviceAdapter = DeviceAdapter(mutableListOf()) { device ->
            confirmInteraction(device)
        }
        rvDevices.layoutManager = LinearLayoutManager(context)
        rvDevices.adapter = deviceAdapter

        switchSocialEnabled.isChecked = socialEnabled
        switchSocialEnabled.setOnCheckedChangeListener { _, isChecked ->
            socialEnabled = isChecked
            saveSocialPrefs()

            if (!isChecked) {
                stopBleScan(showToast = false)
                deviceAdapter.clearDevices()
                renderState(SocialUiState.DISABLED)
            } else {
                renderState(SocialUiState.ENABLED)
            }
        }

        btnScan.setOnClickListener {
            checkPermissionsAndScan()
        }

        tvInteractionsCount.text = interactionsToday.toString()
        renderState(if (socialEnabled) SocialUiState.ENABLED else SocialUiState.DISABLED)

        return view
    }

    private fun renderState(state: SocialUiState) {
        uiState = state
        tvInteractionsCount.text = interactionsToday.toString()

        layoutSocialDisabled.visibility =
            if (state == SocialUiState.DISABLED) View.VISIBLE else View.GONE
        layoutSocialEnabled.visibility =
            if (
                state == SocialUiState.ENABLED ||
                state == SocialUiState.FOUND ||
                state == SocialUiState.CONFIRMED
            ) {
                View.VISIBLE
            } else {
                View.GONE
            }
        cardScanningState.visibility =
            if (state == SocialUiState.SCANNING) View.VISIBLE else View.GONE
        cardConfirmedState.visibility =
            if (state == SocialUiState.CONFIRMED) View.VISIBLE else View.GONE

        val showNearbySection = state == SocialUiState.FOUND
        tvNearbyLabel.visibility = if (showNearbySection) View.VISIBLE else View.GONE
        rvDevices.visibility =
            if (showNearbySection && deviceAdapter.hasDevices()) View.VISIBLE else View.GONE
        tvEmptyState.visibility =
            if (showNearbySection && !deviceAdapter.hasDevices()) View.VISIBLE else View.GONE

        when (state) {
            SocialUiState.DISABLED -> {
                btnScan.text = "Find nearby students"
                btnScan.isEnabled = false
                progressBar.visibility = View.GONE
            }

            SocialUiState.ENABLED -> {
                btnScan.text = "Find nearby students"
                btnScan.isEnabled = true
                progressBar.visibility = View.GONE
            }

            SocialUiState.SCANNING -> {
                progressBar.visibility = View.VISIBLE
                tvNearbyLabel.visibility = View.GONE
                rvDevices.visibility = View.GONE
                tvEmptyState.visibility = View.GONE
            }

            SocialUiState.FOUND -> {
                progressBar.visibility = View.GONE
                tvEmptyState.text = "No nearby students found yet."
            }

            SocialUiState.CONFIRMED -> {
                progressBar.visibility = View.GONE
                btnScan.text = "Find more students"
                btnScan.isEnabled = true
                tvNearbyLabel.visibility = View.GONE
                rvDevices.visibility = View.GONE
                tvEmptyState.visibility = View.GONE
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

    private fun checkPermissionsAndScan() {
        if (!socialEnabled) {
            Toast.makeText(context, "Enable the social feature first.", Toast.LENGTH_SHORT).show()
            return
        }

        val permissionsToRequest = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        val notGranted = permissionsToRequest.filter {
            ActivityCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGranted.isNotEmpty()) {
            requestPermissionLauncher.launch(notGranted.toTypedArray())
        } else {
            startBleScan()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startBleScan() {
        if (bluetoothAdapter == null || bluetoothAdapter?.isEnabled != true) {
            Toast.makeText(context, "Please enable Bluetooth first.", Toast.LENGTH_SHORT).show()
            renderState(SocialUiState.ENABLED)
            return
        }

        if (isScanning) {
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        } else {
            if (ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        try {
            deviceAdapter.clearDevices()
            isScanning = true
            renderState(SocialUiState.SCANNING)
            bluetoothAdapter?.bluetoothLeScanner?.startScan(leScanCallback)

            handler.postDelayed({
                stopBleScan(showToast = false)
                renderState(
                    if (deviceAdapter.hasDevices()) SocialUiState.FOUND else SocialUiState.ENABLED
                )
            }, scanPeriod)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not start scan: ${e.message}", Toast.LENGTH_SHORT).show()
            renderState(SocialUiState.ENABLED)
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopBleScan(showToast: Boolean) {
        if (!isScanning) {
            return
        }

        isScanning = false
        handler.removeCallbacksAndMessages(null)

        try {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(leScanCallback)
            if (showToast) {
                Toast.makeText(context, "Scan stopped", Toast.LENGTH_SHORT).show()
            }
        } catch (_: Exception) {
        }
    }

    @SuppressLint("MissingPermission")
    private fun confirmInteraction(device: BluetoothDevice) {
        stopBleScan(showToast = false)
        interactionsToday += 1
        saveSocialPrefs()

        val displayName = device.name ?: "a nearby student"
        tvConfirmedMessage.text =
            "You confirmed an interaction with $displayName. Social XP syncing can be added next."

        renderState(SocialUiState.CONFIRMED)
        deviceAdapter.clearDevices()
    }

    private val leScanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val rssi = result.rssi

            if (rssi > -90) {
                activity?.runOnUiThread {
                    deviceAdapter.addDevice(device)
                    if (uiState == SocialUiState.SCANNING) {
                        stopBleScan(showToast = false)
                        renderState(SocialUiState.FOUND)
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        stopBleScan(showToast = false)
        super.onDestroyView()
    }

    companion object {
        private const val PREF_SOCIAL_ENABLED = "SOCIAL_ENABLED"
        private const val PREF_SOCIAL_INTERACTIONS = "SOCIAL_INTERACTIONS"
    }
}
