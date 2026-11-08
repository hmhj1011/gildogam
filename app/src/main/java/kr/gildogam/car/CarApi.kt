package kr.gildogam.car

import android.content.Context
import android.content.pm.PackageManager

/**
 * Whether this device has the Android Automotive car API. Kept apart from [CarPropertySignals]
 * so checking never loads android.car classes on a device that does not have them.
 */
object CarApi {
    fun available(context: Context): Boolean {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) return false
        return try {
            Class.forName("android.car.Car")
            Class.forName("android.car.hardware.property.CarPropertyManager\$CarPropertyEventCallback")
            true
        } catch (t: Throwable) {
            false
        }
    }
}
