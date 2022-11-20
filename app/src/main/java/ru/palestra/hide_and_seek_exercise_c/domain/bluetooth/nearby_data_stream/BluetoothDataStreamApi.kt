package ru.palestra.hide_and_seek_exercise_c.domain.bluetooth.nearby_data_stream

import ru.palestra.hide_and_seek_exercise_c.data.ConnectedBleDevice
import ru.palestra.hide_and_seek_exercise_c.data.GameEvent
import ru.palestra.hide_and_seek_exercise_c.domain.bluetooth.destroy.BluetoothDestroyApi

/** Описание объекта, который отвечает за передачу данных между устройствами. */
interface BluetoothDataStreamApi : BluetoothDestroyApi {

    /**
     * Метод для передачи игрового события.
     *
     * @param gameEvent игровое событие.
     * @param connectedBleDevice устройство для передачи события.
     * @param onSendEventSuccessAction действие, если тправка успешна.
     * @param onSendEventFailureAction действие, если отправить событие не удалось.
     * */
    fun sendGameEventToDevice(
        gameEvent: GameEvent,
        connectedBleDevice: ConnectedBleDevice,
        onSendEventSuccessAction: () -> Unit = {},
        onSendEventFailureAction: (Throwable) -> Unit = {}
    )

    /**
     * Метод для подписки на получение игровых событий.
     *
     * @param connectedBleDevice устройство для передачи события.
     * @param onGameEventObtainedSuccessAction действие при получении игрового события.
     * @param onGameEventObtainedFailureAction действие при ошибке получения игрового события.
     * */
    fun observeGameEventFromDevice(
        connectedBleDevice: ConnectedBleDevice,
        onGameEventObtainedSuccessAction: (ConnectedBleDevice, GameEvent) -> Unit,
        onGameEventObtainedFailureAction: (Throwable) -> Unit
    )
}