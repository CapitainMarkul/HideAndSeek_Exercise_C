package ru.palestra.hide_and_seek_exercise_c.domain.bluetooth

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.github.ivbaranov.rxbluetooth.RxBluetooth
import com.polidea.rxandroidble2.LogConstants
import com.polidea.rxandroidble2.LogConstants.VERBOSE
import com.polidea.rxandroidble2.LogOptions
import com.polidea.rxandroidble2.RxBleClient
import ru.palestra.hide_and_seek_exercise_c.data.ConnectedBleDevice
import ru.palestra.hide_and_seek_exercise_c.domain.bluetooth.device_info.BluetoothDeviceInfo
import ru.palestra.hide_and_seek_exercise_c.domain.bluetooth.device_info.BluetoothDeviceInfoApi
import ru.palestra.hide_and_seek_exercise_c.domain.bluetooth.nearby_connect.BluetoothNearbyConnect
import ru.palestra.hide_and_seek_exercise_c.domain.bluetooth.nearby_connect.BluetoothNearbyConnectApi
import ru.palestra.hide_and_seek_exercise_c.domain.bluetooth.nearby_data_stream.BluetoothDataStream
import ru.palestra.hide_and_seek_exercise_c.domain.bluetooth.nearby_data_stream.BluetoothDataStreamApi
import ru.palestra.hide_and_seek_exercise_c.domain.bluetooth.nearby_scaner.BluetoothNearbyScanner
import ru.palestra.hide_and_seek_exercise_c.domain.bluetooth.nearby_scaner.BluetoothNearbyScannerApi
import timber.log.Timber
import java.util.UUID

/**
 * Объект для инкапсуляции логики работы с Bluetooth LE.
 *
 * Используем формат синглтона, чтобы упростить обработку ConfigChanges.
 * */
object BluetoothManager {

    private const val APP_BLE_SERVER_CHANNEL_ID = "461470e9-0f72-4568-a34f-4acedb35ba8b"
    private const val APP_BLE_CLIENT_CHANNEL_ID = "461470e9-0f72-4568-a34f-4acedb35ba8e"

    private const val ENABLE_BLUETOOTH_REQUEST_CODE = 101

    private var applicationContextOrNull: Context? = null

    /* Идентификатор канала сервера для передачи данных. */
    private val appBleServerChannelId by lazy {
        UUID.fromString(APP_BLE_SERVER_CHANNEL_ID)
    }

    /* Идентификатор канала клиента передачи данных. */
    private val appBleClientChannelId by lazy {
        UUID.fromString(APP_BLE_CLIENT_CHANNEL_ID)
    }

    private val rxBluetoothClassic: RxBluetooth by lazy {
        RxBluetooth(applicationContextOrNull)
    }

    private val rxBluetoothBle: RxBleClient by lazy {
        RxBleClient.updateLogOptions(
            LogOptions.Builder()
                .setLogLevel(VERBOSE)
                .setUuidsLogSetting(LogConstants.UUIDS_FULL)
                .setShouldLogAttributeValues(true)
                .setShouldLogScannedPeripherals(true)
                .build()
        )

        applicationContextOrNull?.let { applicationContext -> RxBleClient.create(applicationContext) }
            ?: throw BluetoothManagerNotInitialized()
    }

    private val bluetoothNearbyConnector: BluetoothNearbyConnectApi by lazy { BluetoothNearbyConnect() }
    private val bluetoothDataStream: BluetoothDataStreamApi by lazy {
        BluetoothDataStream(appBleServerChannelId, appBleClientChannelId)
    }
    private val bluetoothNearbyScanner: BluetoothNearbyScannerApi by lazy {
        applicationContextOrNull?.let { applicationContext ->
            BluetoothNearbyScanner(applicationContext, appBleServerChannelId, appBleClientChannelId, rxBluetoothBle)
        } ?: throw BluetoothManagerNotInitialized()
    }

    private val bluetoothDeviceInfoApi: BluetoothDeviceInfoApi by lazy {
        applicationContextOrNull?.let { BluetoothDeviceInfo(it, rxBluetoothClassic) }
            ?: throw BluetoothManagerNotInitialized()
    }

    private val bluetoothManagerApi: BluetoothManagerApi by lazy {
        object : BluetoothManagerApi,
            BluetoothNearbyScannerApi by bluetoothNearbyScanner,
            BluetoothNearbyConnectApi by bluetoothNearbyConnector,
            BluetoothDeviceInfoApi by bluetoothDeviceInfoApi,
            BluetoothDataStreamApi by bluetoothDataStream {

            override fun isBluetoothEnabled(): Boolean = rxBluetoothClassic.isBluetoothEnabled

            override fun requestToEnableBluetooth(activity: AppCompatActivity) =
                rxBluetoothClassic.enableBluetooth(activity, ENABLE_BLUETOOTH_REQUEST_CODE)

            override fun unsubscribeEverywhereFromDevice(deviceWithNewState: ConnectedBleDevice) {
                bluetoothDataStream.unsubscribeEverywhereFromDevice(deviceWithNewState)
                bluetoothNearbyScanner.unsubscribeEverywhereFromDevice(deviceWithNewState)
                bluetoothNearbyConnector.unsubscribeEverywhereFromDevice(deviceWithNewState)
            }

            override fun onDestroy() {
                bluetoothDataStream.onDestroy()
                bluetoothNearbyScanner.onDestroy()
                bluetoothNearbyConnector.onDestroy()
            }
        }
    }

    /** Метод для инициализации и получения объекта [BluetoothManagerApi]. */
    @Synchronized
    fun getInstance(context: Context): BluetoothManagerApi {
        if (applicationContextOrNull == null) {
            applicationContextOrNull = context.applicationContext

            Timber.plant(Timber.DebugTree())
        }

        return bluetoothManagerApi
    }

    private class BluetoothManagerNotInitialized : Throwable(
        "Before call API, you must call BluetoothManager.getInstance(context: Context)"
    )
}