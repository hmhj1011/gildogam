package kr.gildogam.car

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import kr.gildogam.core.CarEvent
import kr.gildogam.core.Gear

/**
 * Gear, speed and outside temperature through the Android Automotive car API.
 * Pleos Connect aligns its vehicle API with Android Automotive, and these properties are
 * the standard way to read them. Any property the head unit does not expose or allow is skipped.
 */
class CarPropertySignals(
    private val context: Context,
    private val emit: (CarEvent) -> Unit,
) {
    private var car: Car? = null
    private var manager: CarPropertyManager? = null
    private val registered = mutableListOf<Int>()

    /** Human-readable status for the source panel. */
    var status: String = "미연결"
        private set

    private val callback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            when (value.propertyId) {
                VehiclePropertyIds.GEAR_SELECTION -> (value.value as? Int)?.let { emit(CarEvent.GearChanged(gearOf(it))) }
                VehiclePropertyIds.PERF_VEHICLE_SPEED -> (value.value as? Float)?.let { emit(CarEvent.Speed(it * 3.6f)) }
                VehiclePropertyIds.ENV_OUTSIDE_TEMPERATURE -> (value.value as? Float)?.let { emit(CarEvent.OutsideTemp(it)) }
            }
        }

        override fun onErrorEvent(propId: Int, zone: Int) {
            Log.w(TAG, "property error $propId/$zone")
        }
    }

    fun start() {
        if (car != null) return
        if (!hasCarApi(context)) {
            status = "차량 API 없음"
            return
        }
        try {
            val c = Car.createCar(context.applicationContext) ?: run {
                status = "차량 서비스 연결 실패"
                return
            }
            car = c
            val mgr = c.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
            manager = mgr
            register(mgr, VehiclePropertyIds.GEAR_SELECTION, CarPropertyManager.SENSOR_RATE_ONCHANGE)
            register(mgr, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_NORMAL)
            register(mgr, VehiclePropertyIds.ENV_OUTSIDE_TEMPERATURE, CarPropertyManager.SENSOR_RATE_ONCHANGE)
            status = if (registered.isEmpty()) "권한 대기" else "차량 신호 ${registered.size}개 연결"
        } catch (t: Throwable) {
            Log.w(TAG, "car api unavailable", t)
            status = "차량 API 오류"
        }
    }

    @Suppress("DEPRECATION")
    private fun register(mgr: CarPropertyManager, propertyId: Int, rate: Float) {
        try {
            if (mgr.getCarPropertyConfig(propertyId) == null) return
            if (mgr.registerCallback(callback, propertyId, rate)) registered += propertyId
        } catch (e: SecurityException) {
            Log.w(TAG, "no permission for $propertyId")
        } catch (t: Throwable) {
            Log.w(TAG, "register $propertyId failed", t)
        }
    }

    /** Call again after runtime permissions are granted. */
    fun restart() {
        stop()
        start()
    }

    @Suppress("DEPRECATION")
    fun stop() {
        runCatching { manager?.unregisterCallback(callback) }
        runCatching { car?.disconnect() }
        registered.clear()
        manager = null
        car = null
    }

    companion object {
        private const val TAG = "CarPropertySignals"

        fun hasCarApi(context: Context): Boolean {
            if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) return false
            return try {
                Class.forName("android.car.Car")
                true
            } catch (t: Throwable) {
                false
            }
        }

        /** android.car VehicleGear: PARK 4, DRIVE 8, REVERSE 2, NEUTRAL 1, manual gears 16+. */
        fun gearOf(value: Int): Gear = when (value) {
            4 -> Gear.PARK
            8 -> Gear.DRIVE
            2 -> Gear.REVERSE
            1 -> Gear.NEUTRAL
            else -> if (value >= 16) Gear.DRIVE else Gear.UNKNOWN
        }
    }
}
