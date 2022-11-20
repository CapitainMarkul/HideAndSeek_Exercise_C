package ru.palestra.hide_and_seek_exercise_c.domain.bluetooth.nearby_data_stream

import android.annotation.SuppressLint
import com.polidea.rxandroidble2.NotificationSetupMode
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import ru.palestra.hide_and_seek_exercise_c.data.ConnectedBleDevice
import ru.palestra.hide_and_seek_exercise_c.data.GameEvent
import ru.palestra.hide_and_seek_exercise_c.domain.bluetooth.nearby_scaner.BluetoothNearbyScanner
import ru.palestra.hide_and_seek_exercise_c.domain.bluetooth.nearby_scaner.BluetoothNearbyScanner.Companion.APP_BLE_WRITE_READ_CHARACTER_ID
import ru.palestra.hide_and_seek_exercise_c.domain.utils.disposeIfNeeded
import java.util.UUID

/** Объект, который отвечает за передачу данных между устройствами. */
internal class BluetoothDataStream(
    private val appBleServerChannelId: UUID,
    private val appBleClientChannelId: UUID,
    private val manualDisposables: MutableMap<String, CompositeDisposable> = mutableMapOf()
) : BluetoothDataStreamApi {

    @SuppressLint("MissingPermission")
    override fun sendGameEventToDevice(
        gameEvent: GameEvent,
        connectedBleDevice: ConnectedBleDevice,
        onSendEventSuccessAction: () -> Unit,
        onSendEventFailureAction: (Throwable) -> Unit
    ) {
        try {
            BluetoothNearbyScanner.globalServerChar?.notifyCharacteristicChanged(
                connectedBleDevice.nativeBleDevice,
                BluetoothNearbyScanner.globalMyDeviceCharacteristic.apply {
                    this?.value = gameEvent.eventSymbol.toByteArray()
                },
                false
            )

            onSendEventSuccessAction()
        } catch (e: Exception) {
            onSendEventFailureAction(e)
        }
    }

    override fun observeGameEventFromDevice(
        connectedBleDevice: ConnectedBleDevice,
        onGameEventObtainedSuccessAction: (ConnectedBleDevice, GameEvent) -> Unit,
        onGameEventObtainedFailureAction: (Throwable) -> Unit
    ) {
        connectedBleDevice.bleConnectionOrNull?.let { bleConnection ->
            saveDisposableForDevice(connectedBleDevice.deviceMac, bleConnection
                .setupNotification(APP_BLE_WRITE_READ_CHARACTER_ID, NotificationSetupMode.COMPAT)
                ?.switchMap { observable -> observable }
                ?.map { GameEvent.getEventFromSymbol(String(it)) }
                ?.subscribeOn(Schedulers.io())
                ?.observeOn(AndroidSchedulers.mainThread())
                ?.subscribe({ gameEvent ->
                    onGameEventObtainedSuccessAction(connectedBleDevice, gameEvent)
                }, { error ->
                    onGameEventObtainedFailureAction(error)
                })
            )
        } ?: onGameEventObtainedFailureAction(Exception("Connection not found!"))
    }

    override fun onDestroy() {
        manualDisposables.forEach { it.value.disposeIfNeeded() }
    }

    override fun unsubscribeEverywhereFromDevice(deviceWithNewState: ConnectedBleDevice) {
        disposeEverywhereForDevice(deviceWithNewState.deviceMac)
    }

    private fun saveDisposableForDevice(deviceMac: String, disposable: Disposable?) {
        if (disposable == null) return

        val compositeDisposableForDevice = manualDisposables[deviceMac]
        if (compositeDisposableForDevice != null) compositeDisposableForDevice.add(disposable)
        else manualDisposables[deviceMac] = CompositeDisposable().apply { add(disposable) }
    }

    private fun disposeEverywhereForDevice(deviceMac: String) {
        manualDisposables[deviceMac]?.disposeIfNeeded()
    }
}