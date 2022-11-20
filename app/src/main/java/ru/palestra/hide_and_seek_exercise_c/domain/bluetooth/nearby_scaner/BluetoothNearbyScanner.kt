package ru.palestra.hide_and_seek_exercise_c.domain.bluetooth.nearby_scaner

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattCharacteristic.PROPERTY_INDICATE
import android.bluetooth.BluetoothGattCharacteristic.PROPERTY_NOTIFY
import android.bluetooth.BluetoothGattCharacteristic.PROPERTY_READ
import android.bluetooth.BluetoothGattCharacteristic.PROPERTY_WRITE
import android.bluetooth.BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.ParcelUuid
import androidx.appcompat.app.AppCompatActivity
import com.polidea.rxandroidble2.RxBleClient
import com.polidea.rxandroidble2.scan.IsConnectable
import com.polidea.rxandroidble2.scan.ScanCallbackType
import com.polidea.rxandroidble2.scan.ScanFilter
import com.polidea.rxandroidble2.scan.ScanResult
import com.polidea.rxandroidble2.scan.ScanSettings
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import ru.palestra.hide_and_seek_exercise_c.data.ConnectedBleDevice
import ru.palestra.hide_and_seek_exercise_c.domain.utils.disposeIfNeeded
import java.util.UUID

typealias BluetoothManagerNative = android.bluetooth.BluetoothManager

/** Реализация объекта, предназначенного для поиска девайсов поблизости текущего устройства. */
internal class BluetoothNearbyScanner(
    private val applicationContext: Context,
    appBleServerServiceId: UUID,
    appBleClientServiceId: UUID,
    private val rxBluetoothBle: RxBleClient,
    private val compositeDisposable: CompositeDisposable = CompositeDisposable()
) : BluetoothNearbyScannerApi {

    companion object {
        val APP_BLE_CLIENT_CHANNEL_ID = UUID.fromString("461470e9-0f72-4568-a34f-4acedb35ba8a")
        val APP_BLE_WRITE_READ_CHARACTER_ID = UUID.fromString("461470e9-0f72-4568-a34f-4acedb35cb8a")

        var globalServerChar: BluetoothGattServer? = null
        var globalMyDeviceCharacteristic: BluetoothGattCharacteristic? = null
    }

    private var observeScanBleDevicesDisposable: Disposable? = null
    private var observeServerStartDisposable: Disposable? = null

    private val appBleServerChannelId: ParcelUuid by lazy {
        ParcelUuid(appBleServerServiceId)
    }

    private val appBleClientChannelId: ParcelUuid by lazy {
        ParcelUuid(appBleClientServiceId)
    }

    private val bluetoothAdapter: BluetoothAdapter?
        get() = BluetoothAdapter.getDefaultAdapter()

    private val bluetoothBleAdvertiser: BluetoothLeAdvertiser?
        get() = bluetoothAdapter?.bluetoothLeAdvertiser

    @SuppressLint("MissingPermission")
    private val observableGattConnections: (Boolean) -> Observable<BluetoothDevice> = { isClientMode ->
        Observable.create<BluetoothDevice> {
            val btManager =
                applicationContext.getSystemService(AppCompatActivity.BLUETOOTH_SERVICE) as BluetoothManagerNative

            globalServerChar = btManager.openGattServer(applicationContext, object : BluetoothGattServerCallback() {
                override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
                    super.onConnectionStateChange(device, status, newState)

                    if (isClientMode) {
                        it.onNext(device)
                    }
                }

                override fun onServiceAdded(status: Int, service: BluetoothGattService?) {
                    super.onServiceAdded(status, service)

                    val char = try {
                        service?.getCharacteristic(APP_BLE_WRITE_READ_CHARACTER_ID)
                    } catch (e: Exception) {
                        null
                    }

                    globalMyDeviceCharacteristic = char
                }
            }).apply {
                val service = BluetoothGattService(
                    APP_BLE_CLIENT_CHANNEL_ID,
                    BluetoothGattService.SERVICE_TYPE_PRIMARY
                )
                val serialData = BluetoothGattCharacteristic(
                    APP_BLE_WRITE_READ_CHARACTER_ID,
                    PROPERTY_NOTIFY or PROPERTY_INDICATE or PROPERTY_WRITE or PROPERTY_WRITE_NO_RESPONSE or PROPERTY_READ,
                    0/*PERMISSION_WRITE or PERMISSION_READ*/
                )

                service.addCharacteristic(serialData)

                addService(service)
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun startGattServer(
        isClientMode: Boolean,
        onServerStartSuccessAction: () -> Unit,
        onDeviceConnectedAction: (ConnectedBleDevice) -> Unit
    ) {
        observeServerStartDisposable = observableGattConnections.invoke(isClientMode)
            .map { nativeBluetoothDevice ->
                ConnectedBleDevice(
                    bleScanResult = ScanResult(
                        rxBluetoothBle.getBleDevice(nativeBluetoothDevice.address),
                        -1,
                        1L,
                        ScanCallbackType.CALLBACK_TYPE_FIRST_MATCH,
                        null,
                        IsConnectable.CONNECTABLE
                    )
                )
            }
            .subscribeOn(Schedulers.single())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSubscribe { onServerStartSuccessAction() }
            .subscribe({
                onDeviceConnectedAction(it)
            }, {
                Unit
            })
    }

    override fun stopGattServer() {
        observeServerStartDisposable?.disposeIfNeeded()
    }

    @SuppressLint("MissingPermission")
    override fun startBleAdvertisingForMe(
        isServerMode: Boolean,
        onStartBleAdvertisingSuccessAction: () -> Unit,
        onStartBleAdvertisingFailureAction: (Int) -> Unit,
        includeDeviceName: Boolean
    ) {
        if (!bluetoothAdapter?.isMultipleAdvertisementSupported!!) return

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .build()

        val advertiseData = AdvertiseData.Builder()
            .setIncludeDeviceName(includeDeviceName)
            .addServiceUuid(if (isServerMode) appBleServerChannelId else appBleClientChannelId)
            .build()

        bluetoothBleAdvertiser?.startAdvertising(
            settings,
            advertiseData,
            advertiseData,
            object : AdvertiseCallback() {
                override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                    super.onStartSuccess(settingsInEffect)

                    onStartBleAdvertisingSuccessAction()
                }

                override fun onStartFailure(errorCode: Int) {
                    super.onStartFailure(errorCode)

                    if (errorCode == ADVERTISE_FAILED_DATA_TOO_LARGE) {
                        onStartBleAdvertisingFailureAction(errorCode)

                        /* Имя устройства слишком длинное - удаляем его из рассылки. */
                        startBleAdvertisingForMe(
                            isServerMode,
                            onStartBleAdvertisingSuccessAction,
                            onStartBleAdvertisingFailureAction,
                            false
                        )

                        return
                    }

                    onStartBleAdvertisingFailureAction(errorCode)
                }
            })
    }

    @SuppressLint("MissingPermission")
    override fun stopBleAdvertisingForMe() {
        bluetoothBleAdvertiser?.stopAdvertising(object : AdvertiseCallback() {})
    }

    @SuppressLint("MissingPermission")
    override fun startScanNearbyPlayers(
        isClientMode: Boolean,
        onDeviceFindAction: (ConnectedBleDevice) -> Unit,
        onDeviceLossAction: (ConnectedBleDevice) -> Unit,
        onDiscoveryFailedAction: (Throwable) -> Unit
    ) {
        stopScanNearbyPlayers()

        observeScanBleDevicesDisposable = rxBluetoothBle.scanBleDevices(
            ScanSettings.Builder()
                .setLegacy(true)
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .build(),
            ScanFilter.Builder()
                .setServiceUuid(if (isClientMode) appBleServerChannelId else appBleClientChannelId)
                .build()
        )
            .subscribeOn(Schedulers.computation())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { findItem ->
                    if (findItem.callbackType == ScanCallbackType.CALLBACK_TYPE_MATCH_LOST) {
                        onDeviceLossAction(ConnectedBleDevice(bleScanResult = findItem))
                    } else onDeviceFindAction(ConnectedBleDevice(bleScanResult = findItem))
                },
                { error -> onDiscoveryFailedAction(error) }
            )

        observeScanBleDevicesDisposable?.let { compositeDisposable.add(it) }
    }

    override fun stopScanNearbyPlayers() {
        observeServerStartDisposable?.disposeIfNeeded()
        observeScanBleDevicesDisposable?.disposeIfNeeded()
    }

    override fun onDestroy() {
        compositeDisposable.dispose()
    }

    override fun unsubscribeEverywhereFromDevice(deviceWithNewState: ConnectedBleDevice) {
        /* Nothing */
    }
}