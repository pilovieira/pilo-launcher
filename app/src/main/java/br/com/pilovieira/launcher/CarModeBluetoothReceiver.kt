package br.com.pilovieira.launcher

import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

private const val TAG = "CarModeBluetooth"

/**
 * Automatically turns Car Mode on/off when the phone connects to or
 * disconnects from the car's Bluetooth stereo. If the user picked a specific
 * paired device in settings, that exact device is matched by address. If no
 * device was picked, falls back to guessing by Bluetooth device class
 * (AUDIO_VIDEO_CAR_AUDIO), which isn't reported consistently by every head
 * unit. Only acts when the user has enabled Auto Car Mode in settings.
 */
class CarModeBluetoothReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!CarModePrefs.isAutoEnabled(context)) {
            Log.d(TAG, "Ignoring ${intent.action}: Auto Car Mode is disabled")
            return
        }

        val device = getDeviceExtra(intent)
        if (device == null) {
            Log.d(TAG, "Ignoring ${intent.action}: no BluetoothDevice extra")
            return
        }

        val matches = runCatching { matchesTargetDevice(context, device) }
            .getOrElse { error ->
                Log.w(TAG, "Could not inspect device (missing BLUETOOTH_CONNECT permission?)", error)
                false
            }

        Log.d(TAG, "${intent.action} from ${device.address} (matches=$matches)")
        if (!matches) return

        when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                CarModePrefs.setEnabled(context, true)
                bringLauncherToFront(context)
            }
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                CarModePrefs.setEnabled(context, false)
            }
        }
    }

    private fun matchesTargetDevice(context: Context, device: BluetoothDevice): Boolean {
        val targetAddress = CarModePrefs.getAutoDeviceAddress(context)
        if (targetAddress != null) {
            return device.address == targetAddress
        }
        // No specific device configured: fall back to a best-effort class guess.
        return device.bluetoothClass?.deviceClass == BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO
    }

    private fun getDeviceExtra(intent: Intent): BluetoothDevice? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
    }

    private fun bringLauncherToFront(context: Context) {
        runCatching {
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(launchIntent)
        }
    }
}
