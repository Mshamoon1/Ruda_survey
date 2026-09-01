package com.ruda.survey.utils

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.*
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.*
import kotlin.coroutines.resume

data class GpsLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float?,
    val timestamp: Long,
    val areaName: String?
)

class LocationHelper(private val context: Context) {

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    var currentLocation: Location? = null
        private set
    private var locationCallback: LocationCallback? = null

    @SuppressLint("MissingPermission")
    fun startUpdates(onLocationUpdate: (Location) -> Unit) {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
            .setMinUpdateDistanceMeters(0f)
            .setMinUpdateIntervalMillis(1000L)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { loc ->
                    currentLocation = loc
                    onLocationUpdate(loc)
                }
            }
        }

        fusedClient.requestLocationUpdates(request, locationCallback!!, Looper.getMainLooper())
    }

    fun stopUpdates() {
        locationCallback?.let { fusedClient.removeLocationUpdates(it) }
        locationCallback = null
    }

    @SuppressLint("MissingPermission")
    suspend fun getLastLocation(): Location? {
        return suspendCancellableCoroutine { cont ->
            fusedClient.lastLocation.addOnSuccessListener { location ->
                if (cont.isActive) cont.resume(location)
            }.addOnFailureListener {
                if (cont.isActive) cont.resume(null)
            }
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun getFreshLocation(timeoutMs: Long = 10000L): Location? {
        return suspendCancellableCoroutine { cont ->
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 0)
                .setMaxUpdates(1)
                .setDurationMillis(timeoutMs)
                .build()

            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    fusedClient.removeLocationUpdates(this)
                    if (cont.isActive) {
                        cont.resume(result.lastLocation)
                    }
                }
            }

            fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())

            cont.invokeOnCancellation {
                fusedClient.removeLocationUpdates(callback)
            }
        }
    }

    fun reverseGeocode(latitude: Double, longitude: Double): String? {
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)
            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                buildString {
                    addr.subAdminArea?.let { append(it) }
                    addr.adminArea?.let {
                        if (isNotEmpty()) append(", ")
                        append(it)
                    }
                    addr.countryName?.let {
                        if (isNotEmpty()) append(", ")
                        append(it)
                    }
                }.ifBlank { null }
            } else null
        } catch (e: Exception) {
            null
        }
    }

    fun buildGpsLocation(location: Location): GpsLocation? {
        if (location.latitude == 0.0 && location.longitude == 0.0) return null
        val areaName = try {
            reverseGeocode(location.latitude, location.longitude)
        } catch (e: Exception) {
            null
        }

        return GpsLocation(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracy = location.accuracy,
            timestamp = location.time,
            areaName = areaName
        )
    }
}
