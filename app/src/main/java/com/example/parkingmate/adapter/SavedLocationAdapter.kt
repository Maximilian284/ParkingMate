package com.example.parkingmate.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.parkingmate.R
import com.example.parkingmate.data.SavedLocation

class SavedLocationAdapter(
    private var locations: List<SavedLocation>,
    private val onItemClick: (SavedLocation) -> Unit,
    private val onEditClick: (SavedLocation) -> Unit
) : RecyclerView.Adapter<SavedLocationAdapter.LocationViewHolder>() {

    class LocationViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvLocationName)
        val tvType: TextView = view.findViewById(R.id.tvLocationType)
        val btnEdit: ImageButton = view.findViewById(R.id.btnEditLocation)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LocationViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_saved_location, parent, false)
        return LocationViewHolder(view)
    }

    override fun onBindViewHolder(holder: LocationViewHolder, position: Int) {
        val location = locations[position]
        holder.tvName.text = location.name
        holder.tvType.text = "Tariffa: ${location.defaultType}"

        holder.itemView.setOnClickListener { onItemClick(location) }
        holder.btnEdit.setOnClickListener { onEditClick(location) }
    }

    override fun getItemCount() = locations.size

    fun updateData(newList: List<SavedLocation>) {
        this.locations = newList
        notifyDataSetChanged()
    }
}