package ru.palestra.hide_and_seek_exercise_c.presentation.man_looking.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import ru.palestra.hide_and_seek_exercise_c.data.ConnectedBleDevice
import ru.palestra.hide_and_seek_exercise_c.databinding.ItemDeviceInfoBinding

/** Класс, отвечающий за лдогику отображения элементов списка найденных устройств. */
internal class AllFindDevicesAdapter(
    private val context: Context,
    private val onDeviceItemClickListener: (ConnectedBleDevice) -> Unit
) : RecyclerView.Adapter<AllFindDevicesAdapter.ViewHolder>() {

    private val itemList = mutableListOf<ConnectedBleDevice>()

    /** Метод для добавления найденного Bluetooth устройства из списка. */
    fun addItem(item: ConnectedBleDevice) {
        /* Откидываем повторяющиеся устройства. */
        if (itemList.any { it.deviceMac == item.deviceMac }) return

        updateListWithDiffUtil(
            mutableListOf<ConnectedBleDevice>().apply {
                addAll(itemList)
                add(item)
            }
        )
    }

    /** Метод для удаления найденного ранее Bluetooth устройства из списка. */
    fun removeItem(item: ConnectedBleDevice) {
        val indexForRemove = itemList.indexOfFirst { it.deviceMac == item.deviceMac }
        if (indexForRemove != -1) {
            updateListWithDiffUtil(
                mutableListOf<ConnectedBleDevice>().apply {
                    addAll(itemList)
                    removeAt(indexForRemove)
                }
            )
        }
    }

    /** Метод для обновления элемента списка. */
    fun updateItem(item: ConnectedBleDevice) {
        val indexForAddOrUpdate =
            itemList.indexOfFirst { it.deviceMac == item.deviceMac }

        if (indexForAddOrUpdate != -1) {
            itemList[indexForAddOrUpdate] = item
            notifyItemChanged(indexForAddOrUpdate)
        }
    }

    /** Метод для полной очистки списка найденных Bluetooth устройств. */
    fun removeAll() {
        updateListWithDiffUtil(listOf())
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemDeviceInfoBinding.inflate(LayoutInflater.from(context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(itemList.elementAt(position))

    override fun getItemCount(): Int {
        return itemList.size
    }

    private fun updateListWithDiffUtil(newItems: List<ConnectedBleDevice>) {
        val diffCallback = ScanResultDiffUtilCallback(itemList, newItems)
        val diffResult = DiffUtil.calculateDiff(diffCallback)

        itemList.clear()
        itemList.addAll(newItems)

        diffResult.dispatchUpdatesTo(this)
    }

    internal inner class ViewHolder(private val binding: ItemDeviceInfoBinding) :
        RecyclerView.ViewHolder(binding.root) {

        /** Метод для установки данных в ячейку найденны устройств. */
        @SuppressLint("MissingPermission")
        fun bind(connectedBleDevice: ConnectedBleDevice) {
            binding.connectedDevice = connectedBleDevice

            binding.root.setOnClickListener { onDeviceItemClickListener(connectedBleDevice) }

            binding.executePendingBindings()
        }
    }

    private inner class ScanResultDiffUtilCallback(
        private val oldList: List<ConnectedBleDevice>,
        private val newList: List<ConnectedBleDevice>
    ) : DiffUtil.Callback() {

        override fun getOldListSize(): Int = oldList.size
        override fun getNewListSize(): Int = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
            oldList[oldItemPosition].deviceMac == newList[newItemPosition].deviceMac

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
            oldList[oldItemPosition] === newList[newItemPosition]
    }
}