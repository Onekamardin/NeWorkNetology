package ru.netology.nework.ui.fragments

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import ru.netology.nework.ui.common.AppUiActions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.annotations.MarkerOptions
import ru.netology.nework.R
import ru.netology.nework.databinding.ScreenLocationPickerBinding

@Suppress("DEPRECATION")
class LocationPickerFragment : Fragment(R.layout.screen_location_picker) {
    private var _binding: ScreenLocationPickerBinding? = null
    private val binding get() = _binding!!
    private var mapView: MapView? = null
    private var markerTitle = R.string.location_picker_title
    private var selectedPoint = LatLng(55.751244, 37.618423)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = ScreenLocationPickerBinding.bind(view)
        mapView = binding.locationMap
        mapView?.onCreate(savedInstanceState)
        if (savedInstanceState == null) readInitialCoordinates()
        else selectedPoint = LatLng(savedInstanceState.getDouble("latitude"), savedInstanceState.getDouble("longitude"))
        val readOnly = arguments?.getBoolean(ARG_READ_ONLY) == true
        if (readOnly) {
            binding.locationControls.visibility = View.GONE
            markerTitle = R.string.detail_map_title
        }
        (activity as? AppUiActions)?.setScreenTitle(
            getString(if (readOnly) R.string.detail_map_title else R.string.location_picker_title),
        )
        mapView?.getMapAsync { loadedMap ->
            if (_binding == null) return@getMapAsync
            loadedMap.cameraPosition = CameraPosition.Builder().target(selectedPoint).zoom(11.0).build()
            loadedMap.setStyle(MAP_STYLE) {
                if (_binding == null) return@setStyle
                putMarker(loadedMap, selectedPoint)
                if (!readOnly) {
                    loadedMap.addOnMapClickListener { point ->
                        selectedPoint = point
                        putMarker(loadedMap, point)
                        true
                    }
                }
            }
        }
        binding.selectLocationButton.setOnClickListener {
            if (readOnly) return@setOnClickListener
            val previousEntry = findNavController().previousBackStackEntry
            previousEntry?.savedStateHandle?.set(
                RESULT_KEY,
                "${selectedPoint.latitude},${selectedPoint.longitude}",
            )
            findNavController().navigateUp()
        }
    }

    private fun readInitialCoordinates() {
        val value = arguments?.getString(ARG_INITIAL_COORDINATES).orEmpty()
        val parts = value.split(",")
        if (parts.size == 2) {
            val latitude = parts[0].toDoubleOrNull()
            val longitude = parts[1].toDoubleOrNull()
            if (latitude != null && longitude != null) selectedPoint = LatLng(latitude, longitude)
        }
    }

    private fun putMarker(loadedMap: MapLibreMap, point: LatLng) {
        loadedMap.clear()
        loadedMap.addMarker(MarkerOptions().position(point).title(getString(markerTitle)))
    }

    override fun onStart() {
        super.onStart()
        mapView?.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
    }

    override fun onPause() {
        mapView?.onPause()
        super.onPause()
    }

    override fun onStop() {
        mapView?.onStop()
        super.onStop()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView?.onLowMemory()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putDouble("latitude", selectedPoint.latitude)
        outState.putDouble("longitude", selectedPoint.longitude)
        mapView?.onSaveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        mapView?.onDestroy()
        mapView = null
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val RESULT_KEY = "picked_location_coordinates"
        const val ARG_INITIAL_COORDINATES = "initial_coordinates"
        const val ARG_READ_ONLY = "read_only"
        private const val MAP_STYLE = "https://demotiles.maplibre.org/style.json"
    }
}
