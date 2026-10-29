package com.example.serialkeyboard

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.hoho.android.usbserial.driver.UsbSerialPort

class MainActivity : AppCompatActivity() {

    private val dataBitsValues = intArrayOf(5, 6, 7, 8)
    private val stopBitsValues = intArrayOf(
        UsbSerialPort.STOPBITS_1,
        UsbSerialPort.STOPBITS_1_5,
        UsbSerialPort.STOPBITS_2,
    )
    private val parityValues = intArrayOf(
        UsbSerialPort.PARITY_NONE,
        UsbSerialPort.PARITY_ODD,
        UsbSerialPort.PARITY_EVEN,
    )

    private lateinit var etBaud: EditText
    private lateinit var spDataBits: Spinner
    private lateinit var spStopBits: Spinner
    private lateinit var spParity: Spinner
    private lateinit var spMode: Spinner
    private lateinit var spSuffix: Spinner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etBaud = findViewById(R.id.etBaud)
        spDataBits = findViewById(R.id.spDataBits)
        spStopBits = findViewById(R.id.spStopBits)
        spParity = findViewById(R.id.spParity)
        spMode = findViewById(R.id.spMode)
        spSuffix = findViewById(R.id.spSuffix)

        spDataBits.setItems(dataBitsValues.map { it.toString() }.toTypedArray())
        spStopBits.setItems(resources.getStringArray(R.array.stop_bits_labels))
        spParity.setItems(resources.getStringArray(R.array.parity_labels))
        spMode.setItems(resources.getStringArray(R.array.mode_labels))
        spSuffix.setItems(resources.getStringArray(R.array.suffix_labels))

        findViewById<Button>(R.id.btnEnable).setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        findViewById<Button>(R.id.btnPick).setOnClickListener {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        }
        findViewById<Button>(R.id.btnSave).setOnClickListener { save() }

        load()
    }

    private fun Spinner.setItems(labels: Array<String>) {
        adapter = ArrayAdapter(
            this@MainActivity,
            android.R.layout.simple_spinner_dropdown_item,
            labels,
        )
    }

    private fun load() {
        val cfg = SerialConfig.load(this)
        etBaud.setText(cfg.baud.toString())
        spDataBits.setSelection(dataBitsValues.indexOf(cfg.dataBits).coerceAtLeast(0))
        spStopBits.setSelection(stopBitsValues.indexOf(cfg.stopBits).coerceAtLeast(0))
        spParity.setSelection(parityValues.indexOf(cfg.parity).coerceAtLeast(0))
        spMode.setSelection(cfg.mode)
        spSuffix.setSelection(cfg.suffix)
    }

    private fun save() {
        val baud = etBaud.text.toString().trim().toIntOrNull()
        if (baud == null || baud <= 0) {
            Toast.makeText(this, R.string.invalid_baud, Toast.LENGTH_SHORT).show()
            return
        }
        getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE).edit()
            .putInt(Prefs.BAUD, baud)
            .putInt(Prefs.DATA_BITS, dataBitsValues[spDataBits.selectedItemPosition])
            .putInt(Prefs.STOP_BITS, stopBitsValues[spStopBits.selectedItemPosition])
            .putInt(Prefs.PARITY, parityValues[spParity.selectedItemPosition])
            .putInt(Prefs.MODE, spMode.selectedItemPosition)
            .putInt(Prefs.SUFFIX, spSuffix.selectedItemPosition)
            .apply()
        Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
    }
}
