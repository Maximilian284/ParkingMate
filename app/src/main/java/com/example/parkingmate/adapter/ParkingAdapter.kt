package com.example.parkingmate.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.parkingmate.R
import com.example.parkingmate.data.ParkingSession
import com.example.parkingmate.data.SessionWithVehicle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ParkingAdapter(
    private var parkings: List<SessionWithVehicle>,
    private val onCardClick: (ParkingSession) -> Unit, // <--- Nuovo click sulla card!
    private val onEndClick: (ParkingSession) -> Unit
) : RecyclerView.Adapter<ParkingAdapter.ParkingViewHolder>() {

    private val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())

    class ParkingViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvVehicle: TextView = view.findViewById(R.id.tvParkingVehicle)
        val tvType: TextView = view.findViewById(R.id.tvParkingType)
        val tvStartTime: TextView = view.findViewById(R.id.tvStartTime)
        val tvEndTime: TextView = view.findViewById(R.id.tvEndTime)
        val btnEndParking: Button = view.findViewById(R.id.btnEndParking)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ParkingViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_parking_session, parent, false)
        return ParkingViewHolder(view)
    }

    override fun onBindViewHolder(holder: ParkingViewHolder, position: Int) {
        val item = parkings[position]
        val session = item.session
        val vehicle = item.vehicle

        holder.tvVehicle.text = "${vehicle.name} (${vehicle.type})"
        holder.tvType.text = "Tariffa: ${session.type}"

        val startDate = Date(session.startTime)
        holder.tvStartTime.text = "Inizio:\n${dateFormat.format(startDate)}"

        if (session.isActive) {
            holder.tvEndTime.text = "Stato:\nIn corso..."
            holder.tvEndTime.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
            holder.btnEndParking.visibility = View.VISIBLE
            holder.btnEndParking.setOnClickListener { onEndClick(session) }
        } else {
            holder.btnEndParking.visibility = View.GONE
            holder.tvEndTime.setTextColor(android.graphics.Color.BLACK)
            val endDate = session.endTime?.let { Date(it) }
            if (endDate != null) holder.tvEndTime.text = "Fine:\n${dateFormat.format(endDate)}"
        }

        // --- AZIONE CLICK SULL'INTERA CARD ---
        holder.itemView.setOnClickListener {
            onCardClick(session)
        }
    }

    override fun getItemCount() = parkings.size

    fun updateData(newList: List<SessionWithVehicle>) {
        this.parkings = newList
        notifyDataSetChanged()
    }
}