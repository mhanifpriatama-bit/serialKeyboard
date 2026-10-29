package com.example.serialkeyboard

import android.content.Context
import com.hoho.android.usbserial.driver.UsbSerialPort

object Prefs {
    const val FILE = "settings"
    const val BAUD = "baud"
    const val DATA_BITS = "data_bits"
    const val STOP_BITS = "stop_bits"
    const val PARITY = "parity"
    const val MODE = "mode"
    const val SUFFIX = "suffix"

    // Nilai MODE (sama dengan posisi di spinner)
    const val MODE_LINE = 0
    const val MODE_CHAR = 1

    // Nilai SUFFIX (sama dengan posisi di spinner)
    const val SUFFIX_NONE = 0
    const val SUFFIX_ENTER = 1
    const val SUFFIX_TAB = 2
}

data class SerialConfig(
    val baud: Int,
    val dataBits: Int,
    val stopBits: Int,
    val parity: Int,
    val mode: Int,
    val suffix: Int,
) {
    companion object {
        fun load(context: Context): SerialConfig {
            val p = context.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)
            return SerialConfig(
                baud = p.getInt(Prefs.BAUD, 9600),
                dataBits = p.getInt(Prefs.DATA_BITS, 8),
                stopBits = p.getInt(Prefs.STOP_BITS, UsbSerialPort.STOPBITS_1),
                parity = p.getInt(Prefs.PARITY, UsbSerialPort.PARITY_NONE),
                mode = p.getInt(Prefs.MODE, Prefs.MODE_LINE),
                suffix = p.getInt(Prefs.SUFFIX, Prefs.SUFFIX_ENTER),
            )
        }
    }
}
