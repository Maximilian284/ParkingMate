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

class ParkingAdapter(
    private var parkings: List<SessionWithVehicle>,
    private val onCardClick: (SessionWithVehicle) -> Unit,
    private val onEndClick: (ParkingSession) -> Unit,
    private val onDeleteClick: (ParkingSession) -> Unit // <--- 4° Parametro
) : RecyclerView.Adapter<ParkingAdapter.ParkingViewHolder>() {

    private val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())

    class ParkingViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvVehicle: TextView = view.findViewById(R.id.tvParkingVehicle)
        val tvType: TextView = view.findViewById(R.id.tvParkingType)
        val tvStartTime: TextView = view.findViewById(R.id.tvStartTime)
        val tvEndTime: TextView = view.findViewById(R.id.tvEndTime)
        val btnEndParking: Button = view.findViewById(R.id.btnEndParking)
        val btnDeleteParking: ImageButton = view.findViewById(R.id.btnDeleteParking)
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
            holder.btnDeleteParking.visibility = View.GONE
            holder.btnEndParking.setOnClickListener { onEndClick(session) }
        } else {
            holder.btnEndParking.visibility = View.GONE
            holder.btnDeleteParking.visibility = View.VISIBLE

            // Colore grigio leggibile sia in chiaro che in scuro
            holder.tvEndTime.setTextColor(android.graphics.Color.parseColor("#888888"))

            val endDate = session.endTime?.let { Date(it) }
            if (endDate != null) holder.tvEndTime.text = "Fine:\n${dateFormat.format(endDate)}"

            holder.btnDeleteParking.setOnClickListener { onDeleteClick(session) }
        }

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