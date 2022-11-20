package ru.palestra.hide_and_seek_exercise_c.domain.bluetooth.nearby_connect

import com.polidea.rxandroidble2.RxBleConnection.RxBleConnectionState
import io.reactivex.Emitter
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import ru.palestra.hide_and_seek_exercise_c.data.ConnectedBleDevice
import ru.palestra.hide_and_seek_exercise_c.domain.utils.disposeIfNeeded
import java.util.concurrent.TimeUnit

/** Объект, отвечающего за соединение устройств между собой по Bluetooth. */
internal class BluetoothNearbyConnect(
    private val manualDisposables: MutableMap<String, CompositeDisposable> = mutableMapOf()
) : BluetoothNearbyConnectApi {

    private companion object {
        private const val INTERVAL_REQUEST_ACTUAL_RSSI_LEVEL = 1000L
    }

    private var socketOpenDisposable: Disposable? = null
    private var connectToDeviceDisposable: Disposable? = null
    private var boundStateChangesDisposable: Disposable? = null

    private var emitterConnectedBlePlayers: Emitter<ConnectedBleDevice>? = null
    private var disposableConnectedBlePlayers: Disposable? = null
    private val observableConnectedBlePlayers =
        Observable.create { emitterConnectedBlePlayers = it }
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSubscribe {
                connectedBlePlayers.forEach {
                    emitterConnectedBlePlayers?.onNext(it)
                }
            }

    override val connectedBlePlayers by lazy { mutableListOf<ConnectedBleDevice>() }

    override fun tryConnectToDevice(
        deviceForConnect: ConnectedBleDevice,
        onConnectionSuccessAction: (ConnectedBleDevice) -> Unit,
        onConnectionFailedAction: (Throwable) -> Unit
    ) {
        saveDisposableForDevice(
            deviceForConnect.deviceMac,
            deviceForConnect.bleScanResult.bleDevice.establishConnection(false)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ rxBleConnection ->
                    deviceForConnect.apply {
                        bleConnectionOrNull = rxBleConnection

                        val connectedItemIndex = connectedBlePlayers.indexOfFirst {
                            it.deviceMac == this.deviceMac
                        }

                        if (connectedItemIndex == -1) {
                            connectedBlePlayers.add(this)
                        }

                        emitterConnectedBlePlayers?.onNext(this)

                        onConnectionSuccessAction(this)
                    }
                }, { error -> onConnectionFailedAction(error) })
        )
    }

    override fun observeDeviceRssiLevel(
        connectedBleDevice: ConnectedBleDevice,
        onObtainActualRssiSuccessAction: (ConnectedBleDevice) -> Unit,
        onObtainActualRssiFailedAction: (Throwable) -> Unit
    ) {
        saveDisposableForDevice(
            connectedBleDevice.deviceMac,
            Observable
                .interval(INTERVAL_REQUEST_ACTUAL_RSSI_LEVEL, TimeUnit.MILLISECONDS)
                .flatMap {
                    connectedBleDevice.bleConnectionOrNull?.readRssi()?.toObservable()
                        ?: Observable.just(connectedBleDevice.lastRssiValue)
                }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { actualRssiLevel ->
                        onObtainActualRssiSuccessAction(connectedBleDevice.apply { updateDeviceRssiLevel(actualRssiLevel) })
                    }, { error ->
                        onObtainActualRssiFailedAction(error)
                    })
        )
    }

    override fun observeDeviceConnectionState(
        connectedBleDevice: ConnectedBleDevice,
        onConnectWasChangedAction: (ConnectedBleDevice, RxBleConnectionState) -> Unit
    ) {
        saveDisposableForDevice(
            connectedBleDevice.deviceMac,
            connectedBleDevice.bleScanResult.bleDevice.observeConnectionStateChanges()
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ connectionState ->
                    if (connectionState == RxBleConnectionState.DISCONNECTED) {
                        /* Проверяем кто отключился. */
                        removePlayerIfNeeded(connectedBleDevice)
                    }

                    onConnectWasChangedAction(connectedBleDevice, connectionState)
                }, {
                    removePlayerIfNeeded(connectedBleDevice)
                    onConnectWasChangedAction(
                        connectedBleDevice, RxBleConnectionState.DISCONNECTED
                    )
                })
        )
    }

    override fun unsubscribeEverywhereFromDevice(deviceWithNewState: ConnectedBleDevice) {
        disposeEverywhereForDevice(deviceWithNewState.deviceMac)
    }

    override fun onDestroy() {
        socketOpenDisposable?.disposeIfNeeded()
        connectToDeviceDisposable?.disposeIfNeeded()
        boundStateChangesDisposable?.disposeIfNeeded()
        disposableConnectedBlePlayers?.disposeIfNeeded()

        manualDisposables.forEach { it.value.disposeIfNeeded() }
    }

    private fun removePlayerIfNeeded(playerForRemove: ConnectedBleDevice) {
        /* Проверяем кто отключился. */
        val connectedItemIndex = connectedBlePlayers.indexOfFirst {
            it.deviceMac == playerForRemove.deviceMac
        }

        if (connectedItemIndex != -1) {
            connectedBlePlayers.removeAt(connectedItemIndex)
        }
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