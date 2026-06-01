package com.example.parkingmate.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.parkingmate.R
import com.example.parkingmate.data.ParkingSession
import com.example.parkingmate.data.SessionWithVehicle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Adapter del RecyclerView che gestisce la visualizzazione delle sessioni di parcheggio.
// Riceve la lista delle sessioni e le callback per apertura dettagli, terminazione e cancellazione di una sessione.
class ParkingAdapter(
    private var parkings: List<SessionWithVehicle>,
    private val onCardClick: (SessionWithVehicle) -> Unit,
    private val onEndClick: (ParkingSession) -> Unit,
    private val onDeleteClick: (ParkingSession) -> Unit
) : RecyclerView.Adapter<ParkingAdapter.ParkingViewHolder>() {

    // Formato utilizzato per mostrare date e orari all'interno delle card.
    private val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())

    // ViewHolder che mantiene i riferimenti agli elementi grafici della card
    // per evitare ricerche ripetute tramite findViewById.
    class ParkingViewHolder(view: View) : RecyclerView.ViewHolder(view) {

        val tvTitle: TextView = view.findViewById(R.id.tvDetailTitle)
        val tvVehicle: TextView = view.findViewById(R.id.tvParkingVehicle)
        val tvType: TextView = view.findViewById(R.id.tvParkingType)
        val tvStartTime: TextView = view.findViewById(R.id.tvStartTime)
        val tvEndTime: TextView = view.findViewById(R.id.tvEndTime)
        val btnEndParking: Button = view.findViewById(R.id.btnEndParking)
        val btnDeleteParking: ImageButton = view.findViewById(R.id.btnDeleteParking)
    }

    // Crea una nuova istanza della card partendo dal layout XML item_parking_session.
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ParkingViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_parking_session, parent, false)
        return ParkingViewHolder(view)
    }

    // Associa i dati della sessione corrente agli elementi grafici della card.
    override fun onBindViewHolder(holder: ParkingViewHolder, position: Int) {
        // Recupera la sessione e il veicolo corrispondenti alla posizione corrente.
        val item = parkings[position]
        val session = item.session
        val vehicle = item.vehicle

        holder.tvTitle.text = "${session.locationName}"
        holder.tvVehicle.text = "${vehicle.name} (${vehicle.type})"
        // Mostra il costo solo se maggiore di zero.
        val costFormatted = if (session.cost > 0) {
            String.format(java.util.Locale.getDefault(), " (%.2f €)", session.cost)
        } else {
            ""
        }

        // Traduce il tipo di tariffa per la visualizzazione all'utente.
        val typeTranslated = if (session.type == "Free") "Gratis" else session.type
        holder.tvType.text = "Tariffa: $typeTranslated$costFormatted"

        val startDate = Date(session.startTime)
        holder.tvStartTime.text = "Inizio:\n${dateFormat.format(startDate)}"

        // Gestione grafica delle sessioni ancora attive.
        if (session.isActive) {
            holder.tvEndTime.text = "Stato:\nIn corso..."
            holder.tvEndTime.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
            holder.btnEndParking.visibility = View.VISIBLE
            holder.btnDeleteParking.visibility = View.GONE
            holder.btnEndParking.setOnClickListener { onEndClick(session) }
        // Gestione grafica delle sessioni terminate.
        } else {
            holder.btnEndParking.visibility = View.GONE
            holder.btnDeleteParking.visibility = View.VISIBLE
            holder.tvEndTime.setTextColor(android.graphics.Color.parseColor("#888888"))

            val endDate = session.endTime?.let { Date(it) }
            if (endDate != null) holder.tvEndTime.text = "Fine:\n${dateFormat.format(endDate)}"

            holder.btnDeleteParking.setOnClickListener { onDeleteClick(session) }
        }

        // Apertura dei dettagli della sessione al click sulla card.
        holder.itemView.setOnClickListener {
            onCardClick(item)
        }
    }

    override fun getItemCount() = parkings.size

    fun updateData(newList: List<SessionWithVehicle>) {
        this.parkings = newList
        notifyDataSetChanged()
    }
}