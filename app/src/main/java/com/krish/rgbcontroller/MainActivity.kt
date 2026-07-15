package com.krish.rgbcontroller

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.util.UUID

class MainActivity : AppCompatActivity() {

    // BLE service and writable characteristic
    private val SERVICE =
        UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")

    private val WRITE =
        UUID.fromString("0000fff3-0000-1000-8000-00805f9b34fb")

    // Known device MAC address
    private val TARGET_ADDRESS = "E4:98:BB:DA:87:49"

    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null

    private lateinit var status: TextView

    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        status = findViewById(R.id.status)

        requestPermissionsIfNeeded()

        findViewById<Button>(R.id.connect).setOnClickListener {
            scanAndConnect()
        }

        // RED
        findViewById<Button>(R.id.red).setOnClickListener {
            sendHex("BC 04 06 00 00 03 E8 00 00 55")
        }

        // YELLOW
        findViewById<Button>(R.id.yellow).setOnClickListener {
            sendHex("BC 04 06 00 3C 03 E8 00 00 55")
        }

        // WHITE
        findViewById<Button>(R.id.white).setOnClickListener {
            sendHex("BC 05 06 01 C6 00 00 00 00 55")
        }

        // EFFECT 01
        findViewById<Button>(R.id.effect1).setOnClickListener {
            sendHex("BC 06 02 00 01 55")
        }

        // EFFECT 4C
        findViewById<Button>(R.id.effect4c).setOnClickListener {
            sendHex("BC 06 02 00 4C 55")
        }

        // SPEED CONTROL
        findViewById<SeekBar>(R.id.speed)
            .setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener {

                    override fun onProgressChanged(
                        seekBar: SeekBar?,
                        progress: Int,
                        fromUser: Boolean
                    ) {
                        if (fromUser) {
                            val speed = progress.coerceIn(0, 100)

                            sendHex(
                                "BC 08 01 %02X 55".format(speed)
                            )
                        }
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}

                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                }
            )

        // RAW HEX COMMAND
        findViewById<Button>(R.id.sendRaw).setOnClickListener {

            val hex =
                findViewById<EditText>(R.id.rawHex)
                    .text
                    .toString()

            sendHex(hex)
        }
    }

    private fun requestPermissionsIfNeeded() {

        val needed = mutableListOf<String>()

        if (android.os.Build.VERSION.SDK_INT >= 31) {

            if (
                checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED
            ) {
                needed += Manifest.permission.BLUETOOTH_SCAN
            }

            if (
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED
            ) {
                needed += Manifest.permission.BLUETOOTH_CONNECT
            }
        }

        if (needed.isNotEmpty()) {

            ActivityCompat.requestPermissions(
                this,
                needed.toTypedArray(),
                7
            )
        }
    }

    private fun scanAndConnect() {

        // Check permissions
        if (
            android.os.Build.VERSION.SDK_INT >= 31 &&
            (
                checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED
            )
        ) {
            requestPermissionsIfNeeded()
            setStatus("Allow Nearby devices permission, then press Connect again")
            return
        }

        val manager =
            getSystemService(BLUETOOTH_SERVICE) as BluetoothManager

        val adapter = manager.adapter
            ?: return setStatus("No Bluetooth adapter")

        if (!adapter.isEnabled) {
            return setStatus("Turn Bluetooth ON first")
        }

        val scanner = adapter.bluetoothLeScanner
            ?: return setStatus("BLE scanner unavailable")

        bluetoothLeScanner = scanner

        setStatus("Scanning for your RGB light…")

        val callback = object : ScanCallback() {

            override fun onScanResult(
                callbackType: Int,
                result: ScanResult
            ) {

                val device = result.device

                val name =
                    device.name
                        ?: result.scanRecord?.deviceName
                        ?: "Unknown"

                val address = device.address

                // Accept either device name or known MAC address
                val isOurLight =
                    name.equals(
                        "GATT--DEMO",
                        ignoreCase = true
                    ) ||
                    name.equals(
                        "GATT-DEMO",
                        ignoreCase = true
                    ) ||
                    address.equals(
                        TARGET_ADDRESS,
                        ignoreCase = true
                    )

                if (isOurLight) {

                    setStatus(
                        "Found $name ($address). Connecting…"
                    )

                    stopBleScan()

                    gatt?.close()
                    gatt = null
                    writeChar = null

                    gatt = device.connectGatt(
                        this@MainActivity,
                        false,
                        gattCallback,
                        BluetoothDevice.TRANSPORT_LE
                    )
                }
            }

            override fun onScanFailed(errorCode: Int) {

                setStatus(
                    "Scan failed. Error code: $errorCode"
                )
            }
        }

        scanCallback = callback

        try {
            scanner.startScan(callback)
        } catch (e: SecurityException) {

            setStatus(
                "Bluetooth permission error: ${e.message}"
            )

            return
        }

        // Stop scanning after 15 seconds
        handler.postDelayed({

            if (gatt == null) {

                stopBleScan()

                setStatus(
                    "Light not found. Close MR Star, keep the light ON, and try again."
                )
            }

        }, 15_000)
    }

    private fun stopBleScan() {

        val scanner = bluetoothLeScanner
        val callback = scanCallback

        if (scanner != null && callback != null) {

            try {

                if (
                    android.os.Build.VERSION.SDK_INT < 31 ||
                    checkSelfPermission(
                        Manifest.permission.BLUETOOTH_SCAN
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    scanner.stopScan(callback)
                }

            } catch (_: Exception) {
            }
        }

        scanCallback = null
    }

    private val gattCallback =
        object : BluetoothGattCallback() {

            override fun onConnectionStateChange(
                g: BluetoothGatt,
                statusCode: Int,
                newState: Int
            ) {

                if (
                    newState ==
                    BluetoothProfile.STATE_CONNECTED
                ) {

                    setStatus(
                        "Connected! Discovering services…"
                    )

                    g.discoverServices()

                } else if (
                    newState ==
                    BluetoothProfile.STATE_DISCONNECTED
                ) {

                    writeChar = null

                    setStatus(
                        "Disconnected. GATT status: $statusCode"
                    )
                }
            }

            override fun onServicesDiscovered(
                g: BluetoothGatt,
                statusCode: Int
            ) {

                if (
                    statusCode != BluetoothGatt.GATT_SUCCESS
                ) {

                    setStatus(
                        "Service discovery failed: $statusCode"
                    )

                    return
                }

                val service =
                    g.getService(SERVICE)

                writeChar =
                    service?.getCharacteristic(WRITE)

                if (writeChar != null) {

                    setStatus(
                        "Ready • Connected to FFF0 / FFF3"
                    )

                } else {

                    setStatus(
                        "Connected, but FFF3 was not found"
                    )
                }
            }
        }

    private fun sendHex(hex: String) {

        val characteristic = writeChar
            ?: return setStatus(
                "Connect to the light first"
            )

        val currentGatt = gatt
            ?: return setStatus(
                "Bluetooth connection missing"
            )

        try {

            val clean = hex
                .replace(" ", "")
                .replace("\n", "")
                .replace("\r", "")

            require(clean.isNotEmpty()) {
                "HEX command is empty"
            }

            require(clean.length % 2 == 0) {
                "HEX must contain complete byte pairs"
            }

            val bytes =
                clean
                    .chunked(2)
                    .map {
                        it.toInt(16).toByte()
                    }
                    .toByteArray()

            if (
                android.os.Build.VERSION.SDK_INT >= 33
            ) {

                val result =
                    currentGatt.writeCharacteristic(
                        characteristic,
                        bytes,
                        BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    )

                setStatus(
                    "Sent: ${hex.uppercase()} • result $result"
                )

            } else {

                @Suppress("DEPRECATION")
                characteristic.writeType =
                    BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE

                @Suppress("DEPRECATION")
                characteristic.value = bytes

                @Suppress("DEPRECATION")
                val success =
                    currentGatt.writeCharacteristic(
                        characteristic
                    )

                setStatus(
                    "Sent: ${hex.uppercase()} • $success"
                )
            }

        } catch (e: Exception) {

            setStatus(
                "HEX error: ${e.message}"
            )
        }
    }

    private fun setStatus(message: String) {

        runOnUiThread {
            status.text = message
        }
    }

    override fun onDestroy() {

        stopBleScan()

        handler.removeCallbacksAndMessages(null)

        try {
            gatt?.disconnect()
            gatt?.close()
        } catch (_: Exception) {
        }

        gatt = null

        super.onDestroy()
    }
}
