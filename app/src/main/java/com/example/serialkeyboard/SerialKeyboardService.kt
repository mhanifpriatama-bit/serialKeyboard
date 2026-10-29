package com.example.serialkeyboard

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import java.io.ByteArrayOutputStream

/**
 * Keyboard (IME) yang membaca data dari USB serial dan mengetikkannya
 * ke kolom input yang sedang aktif di aplikasi mana pun.
 */
class SerialKeyboardService : InputMethodService(), SerialInputOutputManager.Listener {

    companion object {
        private const val ACTION_USB_PERMISSION = "com.example.serialkeyboard.USB_PERMISSION"
        private const val RETRY_INTERVAL_MS = 2000L
    }

    private lateinit var usbManager: UsbManager
    private val main = Handler(Looper.getMainLooper())

    private var port: UsbSerialPort? = null
    private var ioManager: SerialInputOutputManager? = null

    @Volatile
    private var activeConfig: SerialConfig? = null

    private val lineBuffer = ByteArrayOutputStream()

    // Perangkat yang izinnya sudah diminta, supaya dialog tidak muncul berulang.
    private val requestedDevices = mutableSetOf<Int>()

    private var statusView: TextView? = null
    private var statusText: String = ""
    private var lastText: String = ""

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_USB_PERMISSION -> {
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        connect()
                    } else {
                        setStatus(getString(R.string.status_denied))
                    }
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    requestedDevices.clear()
                    disconnect()
                    setStatus(getString(R.string.status_detached))
                }
            }
        }
    }

    // ACTION_USB_DEVICE_ATTACHED tidak dikirim ke receiver dinamis,
    // jadi layanan memeriksa perangkat secara berkala selama belum terhubung.
    private val retryRunnable = object : Runnable {
        override fun run() {
            connect()
            main.postDelayed(this, RETRY_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        statusText = getString(R.string.status_waiting)

        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        main.post(retryRunnable)
    }

    override fun onDestroy() {
        main.removeCallbacks(retryRunnable)
        try {
            unregisterReceiver(receiver)
        } catch (_: Exception) {
        }
        disconnect()
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        val view = layoutInflater.inflate(R.layout.keyboard_view, null)
        statusView = view.findViewById(R.id.tvKbStatus)
        view.findViewById<Button>(R.id.btnKbSwitch).setOnClickListener {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        }
        renderStatus()
        return view
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        connect()
    }

    // ---------------------------------------------------------------- koneksi

    private fun connect() {
        val cfg = SerialConfig.load(this)

        if (port != null) {
            if (cfg == activeConfig) return
            // Pengaturan berubah: sambungkan ulang dengan parameter baru.
            disconnect()
        }

        val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        if (drivers.isEmpty()) {
            setStatus(getString(R.string.status_waiting))
            return
        }

        val driver = drivers[0]
        val device = driver.device

        if (!usbManager.hasPermission(device)) {
            if (requestedDevices.add(device.deviceId)) {
                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_MUTABLE
                } else {
                    0
                }
                val pi = PendingIntent.getBroadcast(
                    this,
                    0,
                    Intent(ACTION_USB_PERMISSION).setPackage(packageName),
                    flags,
                )
                usbManager.requestPermission(device, pi)
                setStatus(getString(R.string.status_permission))
            }
            return
        }

        val connection = usbManager.openDevice(device)
        if (connection == null) {
            setStatus(getString(R.string.status_open_failed))
            return
        }

        try {
            val p = driver.ports[0]
            p.open(connection)
            p.setParameters(cfg.baud, cfg.dataBits, cfg.stopBits, cfg.parity)
            try {
                p.setDTR(true)
            } catch (_: Exception) {
                // Tidak semua chip mendukung DTR.
            }

            port = p
            activeConfig = cfg
            ioManager = SerialInputOutputManager(p, this).also { it.start() }

            val name = device.productName ?: "USB"
            setStatus(getString(R.string.status_connected, name, cfg.baud))
        } catch (e: Exception) {
            try {
                port?.close()
            } catch (_: Exception) {
            }
            port = null
            activeConfig = null
            ioManager = null
            setStatus(getString(R.string.status_error, e.message ?: e.javaClass.simpleName))
        }
    }

    private fun disconnect() {
        ioManager?.stop()
        ioManager = null
        try {
            port?.close()
        } catch (_: Exception) {
        }
        port = null
        activeConfig = null
        synchronized(lineBuffer) { lineBuffer.reset() }
    }

    // ------------------------------------------- SerialInputOutputManager.Listener

    // Dipanggil dari thread I/O.
    override fun onNewData(data: ByteArray) {
        val cfg = activeConfig ?: return

        if (cfg.mode == Prefs.MODE_CHAR) {
            deliver(String(data, Charsets.UTF_8), endOfLine = false)
            return
        }

        val lines = mutableListOf<String>()
        synchronized(lineBuffer) {
            for (b in data) {
                if (b == '\n'.code.toByte() || b == '\r'.code.toByte()) {
                    if (lineBuffer.size() > 0) {
                        lines.add(lineBuffer.toString("UTF-8"))
                        lineBuffer.reset()
                    }
                } else {
                    lineBuffer.write(b.toInt())
                }
            }
        }
        for (line in lines) deliver(line, endOfLine = true)
    }

    override fun onRunError(e: Exception) {
        main.post {
            disconnect()
            setStatus(getString(R.string.status_error, e.message ?: e.javaClass.simpleName))
        }
    }

    // ------------------------------------------------------- mengetik ke input

    private fun deliver(text: String, endOfLine: Boolean) {
        main.post {
            if (currentInputConnection == null) {
                setStatus(getString(R.string.status_no_field))
                return@post
            }
            typeText(text)
            if (endOfLine) {
                when (activeConfig?.suffix) {
                    Prefs.SUFFIX_ENTER -> sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
                    Prefs.SUFFIX_TAB -> sendDownUpKeyEvents(KeyEvent.KEYCODE_TAB)
                }
            }
            lastText = text.filter { it.code >= 0x20 }
            renderStatus()
        }
    }

    private fun typeText(text: String) {
        val ic = currentInputConnection ?: return
        val sb = StringBuilder()

        fun flush() {
            if (sb.isNotEmpty()) {
                ic.commitText(sb.toString(), 1)
                sb.setLength(0)
            }
        }

        for (ch in text) {
            when {
                ch == '\n' -> {
                    flush()
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
                }
                ch == '\t' -> {
                    flush()
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_TAB)
                }
                ch.code < 0x20 -> Unit // abaikan karakter kontrol lain termasuk '\r'
                else -> sb.append(ch)
            }
        }
        flush()
    }

    // ------------------------------------------------------------------ status

    private fun setStatus(text: String) {
        statusText = text
        main.post { renderStatus() }
    }

    private fun renderStatus() {
        val last = if (lastText.isEmpty()) "" else "\n" + getString(R.string.status_last, lastText)
        statusView?.text = statusText + last
    }
}
