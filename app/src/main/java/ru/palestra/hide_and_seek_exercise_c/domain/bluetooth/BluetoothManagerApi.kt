package ru.palestra.hide_and_seek_exercise_c.domain.bluetooth

import androidx.appcompat.app.AppCompatActivity
import ru.palestra.hide_and_seek_exercise_c.data.ConnectedBleDevice
import ru.palestra.hide_and_seek_exercise_c.domain.bluetooth.destroy.BluetoothDestroyApi
import ru.palestra.hide_and_seek_exercise_c.domain.bluetooth.device_info.BluetoothDeviceInfoApi
import ru.palestra.hide_and_seek_exercise_c.domain.bluetooth.nearby_connect.BluetoothNearbyConnectApi
import ru.palestra.hide_and_seek_exercise_c.domain.bluetooth.nearby_data_stream.BluetoothDataStreamApi
import ru.palestra.hide_and_seek_exercise_c.domain.bluetooth.nearby_scaner.BluetoothNearbyScannerApi

/** Описание Api объекта, предоставляющего доступ к работе с Bluetooth. */
interface BluetoothManagerApi :
    BluetoothNearbyScannerApi,
    BluetoothNearbyConnectApi,
    BluetoothDestroyApi,
    BluetoothDeviceInfoApi,
    BluetoothDataStreamApi {

    /** Метод просит пользователя активировать Bluetooth. */
    fun requestToEnableBluetooth(activity: AppCompatActivity)

    /** Метод проверяет, включен ли у пользователя в данный момент Bluetooth. */
    fun isBluetoothEnabled(): Boolean
}