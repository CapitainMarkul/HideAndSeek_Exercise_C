package ru.palestra.hide_and_seek_exercise_c.domain.location

import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.provider.Settings
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import io.reactivex.disposables.Disposable
import ru.palestra.hide_and_seek_exercise_c.domain.utils.disposeIfNeeded

/** Объект, который отвечает за работу с геолокацией. */
internal class NearbyLocationManager(
    private val context: Context,
    private var lifecycleOwner: LifecycleOwner? = (context as? LifecycleOwner)
) : NearbyLocationManagerApi, LifecycleEventObserver {

    private companion object {
        /* Запрашиваем новое местоположение каждые 5 секунд. */
        private const val INTERVAL_REQUEST_NEW_GEOLOCATION = 5000L
    }

    init {
        lifecycleOwner?.lifecycle?.addObserver(this)
    }

    private var observableActualGeolocation: Disposable? = null

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        if (event == Lifecycle.Event.ON_DESTROY) {
            observableActualGeolocation?.disposeIfNeeded()

            lifecycleOwner?.lifecycle?.removeObserver(this)
            lifecycleOwner = null
        }
    }

    override fun isGpsLocationEnabled(): Boolean {
        val mLocationManager =
            context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        return mLocationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) ?: false
    }

    override fun requestToEnableGpsLocation() =
        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
}