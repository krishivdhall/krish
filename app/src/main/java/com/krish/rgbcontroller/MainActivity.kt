package com.krish.rgbcontroller

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.ParcelUuid
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.util.UUID

class MainActivity : AppCompatActivity() {
    private val SERVICE = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
    private val WRITE = UUID.fromString("0000fff3-0000-1000-8000-00805f9b34fb")
    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); setContentView(R.layout.activity_main)
        status = findViewById(R.id.status)
        requestPermissionsIfNeeded()
        findViewById<Button>(R.id.connect).setOnClickListener { scanAndConnect() }

        // Test candidates from the user's captured sequence.
        findViewById<Button>(R.id.red).setOnClickListener {
            sendHex("BC 04 06 00 00 03 E8 00 00 55")
        }
        findViewById<Button>(R.id.yellow).setOnClickListener {
            sendHex("BC 04 06 00 3C 03 E8 00 00 55")
        }
        findViewById<Button>(R.id.white).setOnClickListener {
            sendHex("BC 05 06 01 C6 00 00 00 00 55")
        }
        findViewById<Button>(R.id.effect1).setOnClickListener { sendHex("BC 06 02 00 01 55") }
        findViewById<Button>(R.id.effect4c).setOnClickListener { sendHex("BC 06 02 00 4C 55") }

        findViewById<SeekBar>(R.id.speed).setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) sendHex("BC 08 01 %02X 55".format(p.coerceIn(0,100)))
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        findViewById<Button>(R.id.sendRaw).setOnClickListener {
            sendHex(findViewById<EditText>(R.id.rawHex).text.toString())
        }
    }

    private fun requestPermissionsIfNeeded() {
        val needed = mutableListOf<String>()
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)
                needed += Manifest.permission.BLUETOOTH_SCAN
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                needed += Manifest.permission.BLUETOOTH_CONNECT
        }
        if (needed.isNotEmpty()) ActivityCompat.requestPermissions(this, needed.toTypedArray(), 7)
    }

    private fun scanAndConnect() {
        val manager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = manager.adapter ?: return setStatus("No Bluetooth adapter")
        if (android.os.Build.VERSION.SDK_INT >= 31 &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            return setStatus("Allow Nearby devices permission first")
        }
        setStatus("Scanning for GATT-DEMO…")
        val scanner = adapter.bluetoothLeScanner ?: return setStatus("BLE scanner unavailable")
        val callback = object : ScanCallback() {
            override fun onScanResult(type: Int, result: ScanResult) {
                val name = result.device.name ?: result.scanRecord?.deviceName
                if (name == "GATT-DEMO") {
                    scanner.stopScan(this)
                    setStatus("Found GATT-DEMO. Connecting…")
                    gatt = result.device.connectGatt(this@MainActivity, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
                }
            }
            override fun onScanFailed(errorCode: Int) { setStatus("Scan failed: $errorCode") }
        }
        scanner.startScan(callback)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, statusCode: Int, state: Int) {
            if (state == BluetoothProfile.STATE_CONNECTED) {
                setStatus("Connected. Discovering services…"); g.discoverServices()
            } else setStatus("Disconnected")
        }
        override fun onServicesDiscovered(g: BluetoothGatt, statusCode: Int) {
            writeChar = g.getService(SERVICE)?.getCharacteristic(WRITE)
            setStatus(if (writeChar != null) "Ready • FFF0 / FFF3" else "FFF3 not found")
        }
    }

    private fun sendHex(hex: String) {
        val c = writeChar ?: return setStatus("Connect first")
        try {
            val clean = hex.replace(" ", "").replace("\n", "")
            require(clean.length % 2 == 0)
            val bytes = clean.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                gatt?.writeCharacteristic(c, bytes, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
            } else {
                @Suppress("DEPRECATION")
                c.value = bytes
                @Suppress("DEPRECATION")
                gatt?.writeCharacteristic(c)
            }
            setStatus("Sent: ${hex.uppercase()}")
        } catch (e: Exception) { setStatus("Bad HEX: ${e.message}") }
    }

    private fun setStatus(s: String) = runOnUiThread { status.text = s }
}
