package ru.palestra.hide_and_seek_exercise_c.domain.bluetooth.nearby_connect

import com.polidea.rxandroidble2.RxBleConnection.RxBleConnectionState
import ru.palestra.hide_and_seek_exercise_c.data.ConnectedBleDevice
import ru.palestra.hide_and_seek_exercise_c.domain.bluetooth.destroy.BluetoothDestroyApi

/** Описание объекта, отвечающего за соединение устройств между собой по Bluetooth. */
interface BluetoothNearbyConnectApi : BluetoothDestroyApi {

    /** Список подключенных игроков. */
    val connectedBlePlayers: MutableList<ConnectedBleDevice>

    /**
     * Метод предназначен для попытки установки соединения с найденным устройством
     * посредством Bluetooth соединения.
     *
     * @param deviceForConnect устройство, с которым пытаемся установить соединение.
     * @param onConnectionSuccessAction действие, если соединение было успешно установлено.
     * @param onConnectionFailedAction действие, если соединение установить не удалось.
     * */
    fun tryConnectToDevice(
        deviceForConnect: ConnectedBleDevice,
        onConnectionSuccessAction: (ConnectedBleDevice) -> Unit,
        onConnectionFailedAction: (Throwable) -> Unit
    )

    /**
     * Метод предназначен для подписки на события обновления уровня Rssi одключенного устройства.
     *
     * @param connectedBleDevice устройство, с которым установено соединение.
     * @param onObtainActualRssiSuccessAction действие, если Rssi было успешно получено.
     * @param onObtainActualRssiFailedAction действие, если Rssi получить не удалось.
     * */
    fun observeDeviceRssiLevel(
        connectedBleDevice: ConnectedBleDevice,
        onObtainActualRssiSuccessAction: (ConnectedBleDevice) -> Unit,
        onObtainActualRssiFailedAction: (Throwable) -> Unit
    )

    /**
     * Метод предназначен для подписки на события статуса соединения с устройством.
     *
     * @param connectedBleDevice устройство, с которым установено соединение.
     * @param onConnectWasChangedAction действие, если изменился статус соединения.
     * */
    fun observeDeviceConnectionState(
        connectedBleDevice: ConnectedBleDevice,
        onConnectWasChangedAction: (ConnectedBleDevice, RxBleConnectionState) -> Unit
    )
}