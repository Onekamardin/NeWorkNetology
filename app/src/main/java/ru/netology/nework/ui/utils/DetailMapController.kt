package ru.netology.nework.ui.utils

import android.view.View
import android.widget.FrameLayout
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import ru.netology.nework.domain.model.Coordinates

@Suppress("DEPRECATION")
class DetailMapController(
    private val container: FrameLayout,
    owner: LifecycleOwner,
) : DefaultLifecycleObserver {
    private val mapView = MapView(container.context)
    private var map: MapLibreMap? = null
    private var coordinates: Coordinates? = null
    private var destroyed = false

    init {
        container.addView(mapView)
        mapView.onCreate(null)
        owner.lifecycle.addObserver(this)
        mapView.getMapAsync { loaded ->
            if (!destroyed) {
                loaded.uiSettings.setAllGesturesEnabled(false)
                loaded.setStyle("https://demotiles.maplibre.org/style.json") {
                    if (!destroyed) {
                        map = loaded
                        updateMarker()
                    }
                }
            }
        }
    }

    fun show(value: Coordinates?) {
        container.visibility = if (value == null) View.GONE else View.VISIBLE
        if (coordinates == value) return
        coordinates = value
        updateMarker()
    }

    private fun updateMarker() {
        val loaded = map ?: return
        loaded.clear()
        val value = coordinates ?: return
        val point = LatLng(value.latitude, value.longitude)
        loaded.cameraPosition = CameraPosition.Builder().target(point).zoom(12.0).build()
        loaded.addMarker(MarkerOptions().position(point))
    }

    override fun onStart(owner: LifecycleOwner) = mapView.onStart()
    override fun onResume(owner: LifecycleOwner) = mapView.onResume()
    override fun onPause(owner: LifecycleOwner) = mapView.onPause()
    override fun onStop(owner: LifecycleOwner) = mapView.onStop()

    override fun onDestroy(owner: LifecycleOwner) {
        destroyed = true
        map = null
        mapView.onDestroy()
        container.removeAllViews()
        owner.lifecycle.removeObserver(this)
    }
}
