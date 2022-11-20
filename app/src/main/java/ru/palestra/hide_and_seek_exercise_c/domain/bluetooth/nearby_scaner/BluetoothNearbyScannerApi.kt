package ru.palestra.hide_and_seek_exercise_c.domain.bluetooth.nearby_scaner

import ru.palestra.hide_and_seek_exercise_c.data.ConnectedBleDevice
import ru.palestra.hide_and_seek_exercise_c.domain.bluetooth.destroy.BluetoothDestroyApi

/** Описание объекта, предназначенного для поиска девайсов поблизости текущего устройства. */
interface BluetoothNearbyScannerApi : BluetoothDestroyApi {

    /**
     * Метод используется для запуска сервера подключения Gatt.
     *
     * @param isClientMode намерение сервера или клиента.
     * @param onServerStartSuccessAction действие вызовется при запуске Gatt сервера.
     * @param onDeviceConnectedAction действие вызовется при обнаружении девайса.
     * */
    fun startGattServer(
        isClientMode: Boolean,
        onServerStartSuccessAction: () -> Unit,
        onDeviceConnectedAction: (ConnectedBleDevice) -> Unit
    )

    /**
     * Метод используется для остановки сервера подключения Gatt.
     * */
    fun stopGattServer()

    /**
     * Метод используется для запуска процесса обнаружения своего устройства с использованием Ble.
     *
     * @param isServerMode намерение сервера или клиента.
     * @param onStartBleAdvertisingSuccessAction действие вызовется при успешном обнаружении себя.
     * @param onStartBleAdvertisingFailureAction действие вызовется при неудаче обнаружения себя.
     * @param includeDeviceName включать ли имя устройства в рассылку пакетов.
     * */
    fun startBleAdvertisingForMe(
        isServerMode: Boolean,
        onStartBleAdvertisingSuccessAction: () -> Unit,
        onStartBleAdvertisingFailureAction: (Int) -> Unit,
        includeDeviceName: Boolean = true
    )

    /**
     * Метод используется для остановки процесса обнаружения своего устройства с использованием Ble.
     * */
    fun stopBleAdvertisingForMe()

    /**
     * Метод используется для запуска процесса поиска девайсов вокруг текущего устройства.
     *
     * @param isClientMode намерение сервера или клиента.
     * @param onDeviceFindAction действие вызовется при нахождении устройства доступного для подключения.
     * @param onDeviceLossAction действие вызовется при потери устройства доступного для подключения.
     * @param onDiscoveryFailedAction действие вызовется при возникновении ошибки.
     * */
    fun startScanNearbyPlayers(
        isClientMode: Boolean,
        onDeviceFindAction: (ConnectedBleDevice) -> Unit,
        onDeviceLossAction: (ConnectedBleDevice) -> Unit,
        onDiscoveryFailedAction: (Throwable) -> Unit
    )

    /** Метод используется для остановки процесса поиска девайсов вокруг текущего устройства. */
    fun stopScanNearbyPlayers()
}