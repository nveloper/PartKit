package com.partkit.widgets.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Play 서비스 없이 LocationManager/Geocoder만으로 대략적 위치와 지명을 얻는다.
 * 백그라운드 위치 권한을 요구하지 않도록 새 위치는 앱이 화면에 있을 때만 받는다.
 */
object LocationResolver {

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** 현재 위치를 받아 [WeatherStore]에 지명과 함께 저장한다. 실패하면 false. */
    suspend fun updateFromDevice(context: Context): Boolean {
        if (!hasPermission(context)) return false
        val location = currentLocation(context) ?: return false
        val name = placeName(context, location.latitude, location.longitude)
            ?: String.format("%.2f, %.2f", location.latitude, location.longitude)
        WeatherStore.setLocation(context, location.latitude, location.longitude, name)
        return true
    }

    /**
     * 저장된 위치의 지명을 현재 표시 언어로 다시 받는다(언어 설정을 바꿨을 때). 위치 권한은 필요 없다.
     * 기본 위치(저장한 적 없음)는 문자열 리소스를 쓰므로 건너뛴다.
     */
    suspend fun relocalizeSavedPlace(context: Context) {
        val location = WeatherStore.current(context).location
        if (location.isDefault) return
        val name = placeName(context, location.latitude, location.longitude) ?: return
        WeatherStore.setLocation(context, location.latitude, location.longitude, name)
    }

    @SuppressLint("MissingPermission")
    private suspend fun currentLocation(context: Context): Location? {
        val manager = context.getSystemService(LocationManager::class.java)
        val provider = listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstOrNull { manager.isProviderEnabled(it) } ?: return lastKnown(manager)

        val fresh = withTimeoutOrNull(15_000) {
            suspendCancellableCoroutine { cont ->
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                manager.getCurrentLocation(provider, signal, context.mainExecutor) { cont.resume(it) }
            }
        }
        return fresh ?: lastKnown(manager)
    }

    @SuppressLint("MissingPermission")
    private fun lastKnown(manager: LocationManager): Location? =
        manager.allProviders.mapNotNull { manager.getLastKnownLocation(it) }.maxByOrNull { it.time }

    private suspend fun placeName(context: Context, latitude: Double, longitude: Double): String? {
        if (!Geocoder.isPresent()) return null
        // 지명 언어는 앱 표시 언어를 따른다(영어로 표시 설정이면 영어 지명).
        val geocoder = Geocoder(context, AppLocale.locale(context))
        val address: Address? = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                withTimeoutOrNull(10_000) {
                    suspendCancellableCoroutine { cont ->
                        geocoder.getFromLocation(latitude, longitude, 1, object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) = cont.resume(addresses.firstOrNull())
                            override fun onError(errorMessage: String?) = cont.resume(null)
                        })
                    }
                }
            } else {
                withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull()
                }
            }
        }.getOrNull()
        // 한국 주소는 locality가 비는 경우가 많아 구(subLocality)로 내려간다.
        return address?.let { it.locality ?: it.subLocality ?: it.subAdminArea ?: it.adminArea }
    }
}
