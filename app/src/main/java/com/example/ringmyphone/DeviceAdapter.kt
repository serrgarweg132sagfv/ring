package com.example.ringmyphone

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.ringmyphone.databinding.ItemDeviceBinding

class DeviceAdapter(
    private val context: Context,
    private val devices: MutableList<Device> = mutableListOf()
) : RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder>() {

    private val selectedIds = mutableSetOf<String>()

    var onFavoriteToggled: (() -> Unit)? = null

    inner class DeviceViewHolder(val binding: ItemDeviceBinding) : RecyclerView.ViewHolder(binding.root)

    /** دستگاه را بر اساس شناسه یکتا اضافه یا به‌روزرسانی می‌کند، سپس دوباره مرتب می‌کند */
    fun addOrUpdate(device: Device) {
        val existingIndex = devices.indexOfFirst { it.id == device.id }
        if (existingIndex >= 0) {
            devices[existingIndex] = device
        } else {
            devices.add(device)
        }
        resortAndRefresh()
    }

    fun removeByServiceName(serviceName: String) {
        val index = devices.indexOfFirst { it.name == serviceName }
        if (index >= 0) {
            selectedIds.remove(devices[index].id)
            devices.removeAt(index)
            resortAndRefresh()
        }
    }

    private fun resortAndRefresh() {
        devices.sortWith(compareByDescending<Device> { PrefsManager.isFavorite(context, it.id) }.thenBy { it.name })
        notifyDataSetChanged()
    }

    fun getAllDevices(): List<Device> = devices

    fun getSelectedDevices(): List<Device> = devices.filter { selectedIds.contains(it.id) }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = ItemDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DeviceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device = devices[position]
        val b = holder.binding

        b.tvDeviceName.text = device.name
        b.tvDeviceAddress.text = "${device.host}:${device.port}"
        b.tvDeviceInitial.text = device.name.trim().firstOrNull()?.uppercase() ?: "?"

        b.checkboxSelect.setOnCheckedChangeListener(null)
        b.checkboxSelect.isChecked = selectedIds.contains(device.id)
        b.checkboxSelect.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) selectedIds.add(device.id) else selectedIds.remove(device.id)
        }

        val isFav = PrefsManager.isFavorite(context, device.id)
        b.btnFavorite.setImageResource(if (isFav) R.drawable.ic_star_filled else R.drawable.ic_star_outline)
        b.btnFavorite.setOnClickListener {
            PrefsManager.toggleFavorite(context, device.id, device.name)
            resortAndRefresh()
            onFavoriteToggled?.invoke()
        }
    }

    override fun getItemCount(): Int = devices.size
}
