package ru.palestra.hide_and_seek_exercise_c.presentation.man_looking.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import ru.palestra.hide_and_seek_exercise_c.data.ConnectedBleDevice
import ru.palestra.hide_and_seek_exercise_c.databinding.ItemPlayersInGameBinding

/** Класс, отвечающий за лдогику отображения элементов списка найденных устройств. */
internal class InGameDevicesAdapter(
    private val context: Context,
    private val onItemsCountChangedAction: (Int) -> Unit,
    private val onBirdSoundPlayClickListener: (ConnectedBleDevice, Boolean) -> Unit
) : RecyclerView.Adapter<InGameDevicesAdapter.ViewHolder>() {

    private val itemList = mutableListOf<ConnectedBleDevice>()

    private var isGameStarted: Boolean = false

    /** Метод для обозначения старта игры. */
    @SuppressLint("NotifyDataSetChanged")
    fun startGame() {
        isGameStarted = true

        /* Обновляем весь список игроков, чтобы отобразить поисковый UI. */
        notifyDataSetChanged()
    }

    /** Метод для добавления найденного Bluetooth устройства из списка. */
    fun addOrUpdateItem(item: ConnectedBleDevice) {
        val indexForAddOrUpdate =
            itemList.indexOfFirst { it.deviceMac == item.deviceMac }

//        val newListItems = mutableListOf<ConnectedBleDevice>().apply { addAll(itemList) }
//        if (indexForAddOrUpdate == -1) {
//            newListItems.add(item)
//        } else {
//            newListItems.add(indexForAddOrUpdate, item)
//            newListItems.removeAt(indexForAddOrUpdate)
//        }
//
//        updateListWithDiffUtil(newListItems)

        if (indexForAddOrUpdate == -1) {
            itemList.add(item)
        }/* else {
            itemList.add(indexForAddOrUpdate, item)
            itemList.removeAt(indexForAddOrUpdate)
        }*/

        notifyDataSetChanged()
//        updateListWithDiffUtil(newListItems)

        onItemsCountChangedAction(itemCount)
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

        onItemsCountChangedAction(itemCount)
    }

    /** Метод для полной очистки списка игроков. */
    fun removeAll() {
        updateListWithDiffUtil(listOf())

        onItemsCountChangedAction(itemCount)
    }

    /** Метод для проверки наличия неспрятавшихся игроков. */
    fun hasNotAllPlayersIsHid(): Boolean = itemList.any { !it.playerWasHide }

    /** Метод для проверки наличия игроков с включенным звуком. */
    fun hasAnyPlayersWithActiveSound(): Boolean = itemList.any { it.birdSoundIsActive }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemPlayersInGameBinding.inflate(LayoutInflater.from(context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(itemList.elementAt(position))

    override fun getItemCount(): Int {
        return itemList.size
    }

    private fun updateListWithDiffUtil(newItems: List<ConnectedBleDevice>) {
        val diffCallback = ConnectedBleDeviceDiffUtilCallback(itemList, newItems)
        val diffResult = DiffUtil.calculateDiff(diffCallback)

        itemList.clear()
        itemList.addAll(newItems)

        diffResult.dispatchUpdatesTo(this)
    }

    internal inner class ViewHolder(private val binding: ItemPlayersInGameBinding) :
        RecyclerView.ViewHolder(binding.root) {

        /** Метод для установки данных в ячейку найденны устройств. */
        @SuppressLint("MissingPermission")
        fun bind(connectedDevice: ConnectedBleDevice) = with(binding) {
            gameStarted = isGameStarted
            connectedBleDevice = connectedDevice
            imgPlayerSoundChecker.isActivated = connectedDevice.birdSoundIsActive

            binding.imgPlayerSoundChecker.setOnClickListener {
                val isBirdPlayValue = !connectedDevice.birdSoundIsActive
                connectedBleDevice = connectedDevice.apply {
                    birdSoundIsActive = isBirdPlayValue
                }

                imgPlayerSoundChecker.isActivated = isBirdPlayValue
                binding.executePendingBindings()

                onBirdSoundPlayClickListener(connectedDevice, !connectedDevice.birdSoundIsActive)
            }

            binding.executePendingBindings()
        }
    }

    private inner class ConnectedBleDeviceDiffUtilCallback(
        private val oldList: List<ConnectedBleDevice>,
        private val newList: List<ConnectedBleDevice>
    ) : DiffUtil.Callback() {

        override fun getOldListSize(): Int = oldList.size
        override fun getNewListSize(): Int = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
            oldList[oldItemPosition].bleScanResult.bleDevice.macAddress ==
                    newList[newItemPosition].bleScanResult.bleDevice.macAddress

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
            oldList[oldItemPosition] == newList[newItemPosition]
    }
}