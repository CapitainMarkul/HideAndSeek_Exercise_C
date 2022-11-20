package ru.palestra.hide_and_seek_exercise_c.domain.bluetooth.destroy

import ru.palestra.hide_and_seek_exercise_c.data.ConnectedBleDevice

/** Описание объекта, который должен осводождать занятые ресурсы. */
interface BluetoothDestroyApi {

    /** Метод для освобождения занятых ресурсов. */
    fun onDestroy()

    /** Метод для автоматической отписки от всех событий для указанного устройства. */
    fun unsubscribeEverywhereFromDevice(deviceWithNewState: ConnectedBleDevice)
}