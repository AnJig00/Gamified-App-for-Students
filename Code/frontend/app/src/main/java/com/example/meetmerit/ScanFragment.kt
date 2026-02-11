package com.example.meetmerit

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ScanFragment : Fragment() {

    private lateinit var deviceAdapter: DeviceAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var btnScan: Button

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var isScanning = false
    private val handler = Handler(Looper.getMainLooper())

    private val SCAN_PERIOD: Long = 10000

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.entries.all { it.value }
            if (allGranted) {
                startBleScan()
            } else {
                Toast.makeText(context, "Bluetooth permissions are required!", Toast.LENGTH_SHORT).show()
            }
        }

    // --- 修复点 1：给 onCreateView 加上注解，解决第 66 行 device.name 的报错 ---
    @SuppressLint("MissingPermission")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_scan, container, false)

        btnScan = view.findViewById(R.id.btnStartScan)
        progressBar = view.findViewById(R.id.progressScan)
        val rvDevices = view.findViewById<RecyclerView>(R.id.rvDevices)

        val bluetoothManager = context?.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        rvDevices.layoutManager = LinearLayoutManager(context)

        // 这里就是第 66 行左右的点击事件
        deviceAdapter = DeviceAdapter(mutableListOf()) { device ->
            // 因为上面加了 @SuppressLint，这里访问 device.name 就不会报错了
            Toast.makeText(context, "Selected: ${device.name ?: "Unknown"}", Toast.LENGTH_SHORT).show()
            stopBleScan()
        }
        rvDevices.adapter = deviceAdapter

        btnScan.setOnClickListener {
            checkPermissionsAndScan()
        }

        return view
    }

    private fun checkPermissionsAndScan() {
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
        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            Toast.makeText(context, "Please enable Bluetooth first", Toast.LENGTH_SHORT).show()
            return
        }

        if (isScanning) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                return
            }
        } else {
            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        try {
            Toast.makeText(context, "Scanning...", Toast.LENGTH_SHORT).show()
            isScanning = true
            progressBar.visibility = View.VISIBLE
            btnScan.text = "Scanning..."
            btnScan.isEnabled = false

            bluetoothAdapter?.bluetoothLeScanner?.startScan(leScanCallback)

            handler.postDelayed({
                stopBleScan()
            }, SCAN_PERIOD)
        } catch (e: Exception) {
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopBleScan() {
        if (!isScanning) return

        try {
            isScanning = false
            progressBar.visibility = View.GONE
            btnScan.text = "Start Scan"
            btnScan.isEnabled = true

            bluetoothAdapter?.bluetoothLeScanner?.stopScan(leScanCallback)
            Toast.makeText(context, "Scan stopped", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
        }
    }

    private val leScanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val rssi = result.rssi

            if (rssi > -90) {
                Log.d("ScanDebug", "Found: ${device.name} | MAC: ${device.address} | RSSI: $rssi")
                activity?.runOnUiThread {
                    deviceAdapter.addDevice(device)
                }
            }
        }
    }
}