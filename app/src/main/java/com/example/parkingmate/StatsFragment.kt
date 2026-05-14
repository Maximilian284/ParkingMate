package com.example.parkingmate

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import com.google.android.material.button.MaterialButton
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.parkingmate.data.AppDatabase
import com.example.parkingmate.data.SessionWithVehicle
import com.example.parkingmate.viewmodel.ParkMateViewModel
import com.example.parkingmate.viewmodel.ParkMateViewModelFactory
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.TileOverlay
import com.google.android.gms.maps.model.TileOverlayOptions
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.maps.android.heatmaps.HeatmapTileProvider
import kotlinx.coroutines.launch

class StatsFragment : Fragment(R.layout.fragment_stats) {

    private val viewModel: ParkMateViewModel by activityViewModels {
        val db = AppDatabase.getDatabase(requireContext().applicationContext)
        ParkMateViewModelFactory(requireActivity().application, db.appDao())
    }

    private var isMapMode = false
    private var currentFilterDays = -1 // -1 = Sempre

    private var allParkings: List<SessionWithVehicle> = emptyList()

    private lateinit var barChart: BarChart
    private lateinit var mapView: MapView
    private lateinit var btnFilter: MaterialButton
    private lateinit var btnToggle: MaterialButton

    private var googleMap: GoogleMap? = null
    private var heatmapOverlay: TileOverlay? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        barChart = view.findViewById(R.id.barChartStats)
        mapView = view.findViewById(R.id.mapViewStats)
        btnFilter = view.findViewById(R.id.btnFilterStats)
        btnToggle = view.findViewById(R.id.btnToggleView)

        setupButtons()
        setupChart()

        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { map ->
            googleMap = map
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(44.4949, 11.3426), 12f))
            processData()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.activeParkings.collect { active ->
                        updateAllParkings(active, viewModel.historyParkings.value)
                    }
                }
                launch {
                    viewModel.historyParkings.collect { history ->
                        updateAllParkings(viewModel.activeParkings.value, history)
                    }
                }
            }
        }
    }

    private fun setupButtons() {
        btnToggle.setOnClickListener {
            isMapMode = !isMapMode

            // Cambia l'icona e il testo del bottone dinamicamente
            btnToggle.text = if (isMapMode) "Grafico Costi" else "Mappa Termica"
            btnToggle.setIconResource(if (isMapMode) android.R.drawable.ic_menu_sort_by_size else android.R.drawable.ic_dialog_map)

            updateVisibility()
        }

        btnFilter.setOnClickListener {
            val options = arrayOf("Sempre", "Ultimi 7 giorni", "Ultimi 30 giorni")
            val currentIndex = when (currentFilterDays) {
                7 -> 1
                30 -> 2
                else -> 0
            }

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Filtra per periodo")
                .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                    currentFilterDays = when (which) {
                        1 -> 7
                        2 -> 30
                        else -> -1
                    }
                    btnFilter.text = "Filtro: ${options[which]}"
                    processData()
                    dialog.dismiss()
                }
                .show()
        }
    }

    private fun setupChart() {
        barChart.description.isEnabled = false
        barChart.setDrawGridBackground(false)
        barChart.setDrawBarShadow(false)
        barChart.animateY(1000)

        // --- SCRITTE BIANCHE E STILE DARK ---
        barChart.setBackgroundColor(Color.parseColor("#121212")) // Sfondo nero
        barChart.legend.textColor = Color.WHITE // Legenda bianca
        barChart.legend.textSize = 14f

        val xAxis = barChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.granularity = 1f
        xAxis.textColor = Color.WHITE // Testo asse X bianco
        xAxis.textSize = 12f

        val leftAxis = barChart.axisLeft
        leftAxis.axisMinimum = 0f
        leftAxis.textColor = Color.WHITE // Testo asse Y bianco
        leftAxis.textSize = 12f
        leftAxis.gridColor = Color.DKGRAY // Griglia grigio scuro per non confondere

        barChart.axisRight.isEnabled = false // Nascondiamo l'asse di destra
    }

    private fun updateAllParkings(active: List<SessionWithVehicle>, history: List<SessionWithVehicle>) {
        allParkings = active + history
        processData()
    }

    private fun updateVisibility() {
        if (isMapMode) {
            barChart.visibility = View.GONE
            mapView.visibility = View.VISIBLE
        } else {
            mapView.visibility = View.GONE
            barChart.visibility = View.VISIBLE
            barChart.animateY(800)
        }
    }

    private fun processData() {
        val currentTime = System.currentTimeMillis()
        val filteredList = if (currentFilterDays > 0) {
            val cutoffTime = currentTime - (currentFilterDays * 24 * 60 * 60 * 1000L)
            allParkings.filter { it.session.startTime >= cutoffTime }
        } else {
            allParkings
        }

        updateBarChart(filteredList)
        updateHeatMap(filteredList)
    }

    private fun updateBarChart(list: List<SessionWithVehicle>) {
        val costByCategory = mutableMapOf<String, Float>()

        for (item in list) {
            val session = item.session
            val type = item.vehicle.type

            var finalCost = 0.0
            if (session.type == "All'ora" || session.type == "Hourly") {
                val endTime = session.endTime ?: System.currentTimeMillis()
                val elapsedHours = (endTime - session.startTime) / (1000.0 * 60.0 * 60.0)
                var calculated = session.initialCost + (session.cost * elapsedHours)
                if (session.maxCost > 0 && calculated > session.maxCost) calculated = session.maxCost
                finalCost = calculated
            } else {
                finalCost = session.cost
            }

            costByCategory[type] = (costByCategory[type] ?: 0f) + finalCost.toFloat()
        }

        val entries = ArrayList<BarEntry>()
        val labels = ArrayList<String>()

        var index = 0f
        for ((category, cost) in costByCategory) {
            entries.add(BarEntry(index, cost))
            labels.add(category)
            index++
        }

        if (entries.isEmpty()) {
            barChart.clear()
            return
        }

        val dataSet = BarDataSet(entries, "Totale Speso (€)")
        dataSet.colors = ColorTemplate.MATERIAL_COLORS.toList()
        dataSet.valueTextColor = Color.WHITE // Valori sopra le colonne in bianco
        dataSet.valueTextSize = 14f

        val barData = BarData(dataSet)
        barData.barWidth = 0.5f

        barChart.data = barData
        barChart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        barChart.invalidate()
    }

    private fun updateHeatMap(list: List<SessionWithVehicle>) {
        val map = googleMap ?: return

        heatmapOverlay?.remove()

        val latLngs = list.map { LatLng(it.session.latitude, it.session.longitude) }

        if (latLngs.isNotEmpty()) {
            val provider = HeatmapTileProvider.Builder()
                .data(latLngs)
                .radius(50) // Raggio della macchia di calore aumentato per visibilità
                .build()
            heatmapOverlay = map.addTileOverlay(TileOverlayOptions().tileProvider(provider))
        }
    }

    // --- Metodi Lifecycle ---
    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { super.onPause(); mapView.onPause() }
    override fun onStop() { super.onStop(); mapView.onStop() }
    override fun onDestroyView() { super.onDestroyView(); mapView.onDestroy(); googleMap = null }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
}