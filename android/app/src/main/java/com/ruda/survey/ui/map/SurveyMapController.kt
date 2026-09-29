package com.ruda.survey.ui.map

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.LayoutInflater
import android.view.MotionEvent
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.Point
import com.mapbox.maps.*
import com.mapbox.maps.extension.style.expressions.generated.Expression
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.circleLayer
import com.mapbox.maps.extension.style.layers.generated.symbolLayer
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.getSourceAs
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.plugin.animation.flyTo
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.plugin.scalebar.scalebar
import com.ruda.survey.R
import com.ruda.survey.domain.model.SurveyItem
import com.ruda.survey.domain.model.hasMapLocation
import com.ruda.survey.domain.model.initialMapFocus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** One GPU-rendered GeoJSON source shared by the dashboard and full-screen map. */
class SurveyMapController(
    private val view: MapView,
    private val scope: CoroutineScope,
    private val onDetails: (SurveyItem) -> Unit,
    private val onError: () -> Unit
) {
    private val map get() = view.mapboxMap
    private var closed = false
    private var satellite = false
    private var ready = false
    private var centered = false
    private var updateJob: Job? = null
    private var surveys = emptyMap<String, SurveyItem>()
    private var collection = FeatureCollection.fromFeatures(emptyList<Feature>())
    private var focus = emptyList<Point>()
    private var dialog: AlertDialog? = null
    private var reportedError = false
    private val loadingError = map.subscribeMapLoadingError {
        if (!closed && !ready && !reportedError) {
            reportedError = true
            view.post { if (!closed) onError() }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun initialize() {
        view.scalebar.updateSettings { enabled = false }
        map.setCamera(CameraOptions.Builder().center(Point.fromLngLat(74.30, 31.60)).zoom(11.5).build())
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> v.parent?.requestDisallowInterceptTouchEvent(true)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.parent?.requestDisallowInterceptTouchEvent(false)
            }
            false
        }
        view.gestures.addOnMapClickListener { point ->
            if (!closed && ready) selectAt(point)
            true
        }
        loadStyle()
    }

    private fun loadStyle() {
        ready = false
        reportedError = false
        map.loadStyle(if (satellite) Style.SATELLITE_STREETS else Style.MAPBOX_STREETS) loaded@{ style ->
            if (closed) return@loaded
            style.addSource(geoJsonSource(SOURCE) {
                featureCollection(collection)
                cluster(true)
                clusterMaxZoom(8)
                clusterRadius(32)
            })
            val single = Expression.not(Expression.has("point_count"))
            style.addLayer(circleLayer(HALO, SOURCE) {
                filter(single)
                circleColor(Expression.get("color"))
                circleRadius(14.0)
                circleOpacity(0.20)
                circleBlur(0.15)
            })
            style.addLayer(circleLayer(POINTS, SOURCE) {
                filter(single)
                circleColor(Expression.get("color"))
                circleRadius(7.5)
                circleStrokeColor(Color.WHITE)
                circleStrokeWidth(2.0)
            })
            style.addLayer(circleLayer(CLUSTER_HALO, SOURCE) {
                filter(Expression.has("point_count"))
                circleColor("#246C8F")
                circleRadius(25.0)
                circleOpacity(0.18)
            })
            style.addLayer(circleLayer(CLUSTERS, SOURCE) {
                filter(Expression.has("point_count"))
                circleColor("#155B7A")
                circleRadius(18.0)
                circleStrokeColor(Color.WHITE)
                circleStrokeWidth(2.0)
            })
            style.addLayer(symbolLayer(COUNTS, SOURCE) {
                filter(Expression.has("point_count"))
                textField(Expression.toString(Expression.get("point_count")))
                textSize(11.0)
                textColor(Color.WHITE)
                textAllowOverlap(true)
                textIgnorePlacement(true)
            })
            ready = true
            focusOnce()
        }
    }

    fun submit(items: List<SurveyItem>) {
        updateJob?.cancel()
        updateJob = scope.launch {
            val prepared = withContext(Dispatchers.Default) {
                val valid = items.filter { it.hasMapLocation }
                val features = valid.map { item ->
                    Feature.fromGeometry(Point.fromLngLat(item.lng, item.lat)).apply {
                        addStringProperty("survey_id", item.id)
                        addStringProperty("color", item.mapPointColor)
                    }
                }
                Triple(valid.associateBy { it.id }, FeatureCollection.fromFeatures(features),
                    initialMapFocus(valid).map { Point.fromLngLat(it.lng, it.lat) })
            }
            if (closed) return@launch
            surveys = prepared.first
            collection = prepared.second
            focus = prepared.third
            if (ready) {
                map.style?.getSourceAs<GeoJsonSource>(SOURCE)?.featureCollection(collection)
                focusOnce()
            }
        }
    }

    private fun focusOnce() {
        if (!ready || centered || focus.isEmpty() || closed) return
        centered = true
        val padding = 24.0 * view.resources.displayMetrics.density
        map.cameraForCoordinates(focus, CameraOptions.Builder().build(),
            EdgeInsets(padding, padding, padding, padding), 15.5, null) { camera ->
            if (!closed) map.setCamera(camera)
        }
    }

    fun toggleStyle() {
        satellite = !satellite
        loadStyle()
    }

    fun enableLocation() {
        view.location.updateSettings { enabled = true; pulsingEnabled = true }
    }

    fun centerOn(latitude: Double, longitude: Double) {
        if (!closed) map.flyTo(CameraOptions.Builder().center(Point.fromLngLat(longitude, latitude)).zoom(16.0).build())
    }

    private fun selectAt(point: Point) {
        val screen = map.pixelForCoordinate(point)
        val radius = 16.0 * view.resources.displayMetrics.density
        map.queryRenderedFeatures(
            RenderedQueryGeometry(ScreenBox(ScreenCoordinate(screen.x - radius, screen.y - radius),
                ScreenCoordinate(screen.x + radius, screen.y + radius))),
            RenderedQueryOptions(listOf(POINTS, CLUSTERS), null)
        ) { result ->
            if (closed) return@queryRenderedFeatures
            val features = result.value?.map { it.queriedFeature.feature }.orEmpty()
            val candidates = features.mapNotNull { feature ->
                if (feature.hasProperty("survey_id")) surveys[feature.getStringProperty("survey_id")] else null
            }.distinctBy { it.id }
            when {
                candidates.size == 1 -> showSurvey(candidates.first())
                candidates.size > 1 -> {
                    dialog?.dismiss()
                    dialog = MaterialAlertDialogBuilder(view.context)
                        .setTitle("${candidates.size} nearby surveys")
                        .setItems(candidates.map { "#${it.srNo} · ${it.parcelId.ifBlank { it.village }}" }.toTypedArray()) { _, index ->
                            showSurvey(candidates[index])
                        }.setNegativeButton("Close", null).show()
                }
                features.isNotEmpty() -> {
                    val center = features.first().geometry() as? Point ?: point
                    map.flyTo(CameraOptions.Builder().center(center).zoom((map.cameraState.zoom + 2).coerceAtMost(18.0)).build())
                }
            }
        }
    }

    fun showSurvey(item: SurveyItem) {
        if (closed) return
        if (item.hasMapLocation) centerOn(item.lat, item.lng)
        val content = LayoutInflater.from(view.context).inflate(R.layout.item_survey_map_info, null)
        content.findViewById<TextView>(R.id.tvParcelId).text = item.parcelId.ifBlank { "Survey #${item.srNo}" }
        content.findViewById<TextView>(R.id.tvOwnerName).text = item.ownerName.ifBlank { "Unknown owner" }
        content.findViewById<TextView>(R.id.tvVillage).text = item.village.ifBlank { "—" }
        content.findViewById<TextView>(R.id.tvStatus).apply {
            text = if (item.isSurveyed) "Surveyed" else "Pending survey"
            setTextColor(Color.parseColor(item.mapPointColor))
        }
        content.findViewById<TextView>(R.id.tvCoordinates).text = if (item.hasMapLocation)
            String.format(Locale.US, "%.6f, %.6f", item.lat, item.lng) else "Location unavailable"
        dialog?.dismiss()
        dialog = MaterialAlertDialogBuilder(view.context).setTitle("Survey details").setView(content)
            .setPositiveButton("Open survey") { _, _ -> if (!closed) onDetails(item) }
            .setNegativeButton("Close", null).show()
    }

    fun close() {
        closed = true
        loadingError.cancel()
        updateJob?.cancel()
        dialog?.dismiss()
        dialog = null
        surveys = emptyMap()
    }

    private companion object {
        const val SOURCE = "survey-points"
        const val POINTS = "survey-point-centers"
        const val HALO = "survey-point-halos"
        const val CLUSTERS = "survey-clusters"
        const val CLUSTER_HALO = "survey-cluster-halos"
        const val COUNTS = "survey-cluster-counts"
    }
}
