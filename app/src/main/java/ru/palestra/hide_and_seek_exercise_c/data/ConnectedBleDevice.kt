package ru.palestra.hide_and_seek_exercise_c.data

import android.bluetooth.BluetoothDevice
import com.polidea.rxandroidble2.RxBleConnection
import com.polidea.rxandroidble2.scan.ScanResult
import ru.palestra.hide_and_seek_exercise_c.data.ConnectionPowerLevel.Level1
import ru.palestra.hide_and_seek_exercise_c.data.ConnectionPowerLevel.Level10
import ru.palestra.hide_and_seek_exercise_c.data.ConnectionPowerLevel.Level2
import ru.palestra.hide_and_seek_exercise_c.data.ConnectionPowerLevel.Level3
import ru.palestra.hide_and_seek_exercise_c.data.ConnectionPowerLevel.Level4
import ru.palestra.hide_and_seek_exercise_c.data.ConnectionPowerLevel.Level5
import ru.palestra.hide_and_seek_exercise_c.data.ConnectionPowerLevel.Level6
import ru.palestra.hide_and_seek_exercise_c.data.ConnectionPowerLevel.Level7
import ru.palestra.hide_and_seek_exercise_c.data.ConnectionPowerLevel.Level8
import ru.palestra.hide_and_seek_exercise_c.data.ConnectionPowerLevel.Level9
import java.util.LinkedList
import java.util.Queue

/**
 * Контейнер для устройства с которым установлена связь.
 *
 * @param bleScanResult [ScanResult]
 * @param bleConnectionOrNull [BluetoothDevice]
 * @param bleConnectionOrNull [RxBleConnection]
 * @param birdSoundIsActive активено ли воспроизведение звука птицы на утсройстве.
 * @param playerWasHide готово ли устройство к игре.
 * */
data class ConnectedBleDevice(
    val bleScanResult: ScanResult,
    var bleConnectionOrNull: RxBleConnection? = null,
    var birdSoundIsActive: Boolean = false,
    var playerWasHide: Boolean = false
) {

    private val rssiAccuracyValues: Queue<Int> = LinkedList()

    /** Нативный клиент [BluetoothDevice]. */
    val nativeBleDevice: BluetoothDevice
        get() = bleScanResult.bleDevice.bluetoothDevice

    /** Отправлено ли приглашение в игру для текущего устройства. */
    var deviceIsInvited = false

    /** Уровень мощности передачи устройства. */
    val txPower: Int
        get() = bleScanResult.scanRecord.txPowerLevel

    /** Текущий уровень силы сигнала устройства. */
    var lastRssiValue: Int = bleScanResult.rssi
        private set

    /** Текущий уровень силы сигнала устройства. */
    val lastRssiLevelTest: String
        get() = lastRssiValue.toString()

    /** Название устройства. */
    val deviceName: String
        get() = bleScanResult.bleDevice.name ?: UNKNOWN_DEVICE_NAME

    /** MAC-адрес устройства. */
    val deviceMac: String
        get() = bleScanResult.bleDevice.macAddress

    /** @SelfDocumented */
    val isRssiPowerLevel1
        get() = connectionPower == Level1

    /** @SelfDocumented */
    val isRssiPowerLevel2
        get() = connectionPower == Level2

    /** @SelfDocumented */
    val isRssiPowerLevel3
        get() = connectionPower == Level3

    /** @SelfDocumented */
    val isRssiPowerLevel4
        get() = connectionPower == Level4

    /** @SelfDocumented */
    val isRssiPowerLevel5
        get() = connectionPower == Level5

    /** @SelfDocumented */
    val isRssiPowerLevel6
        get() = connectionPower == Level6

    /** @SelfDocumented */
    val isRssiPowerLevel7
        get() = connectionPower == Level7

    /** @SelfDocumented */
    val isRssiPowerLevel8
        get() = connectionPower == Level8

    /** @SelfDocumented */
    val isRssiPowerLevel9
        get() = connectionPower == Level9

    /** @SelfDocumented */
    val isRssiPowerLevel10
        get() = connectionPower == Level10

    /** Метод для обновления уровня силы соглана от устройства. */
    fun updateDeviceRssiLevel(actualRssiLevel: Int) {
        lastRssiValue = actualRssiLevel
    }


    companion object {
        const val UNKNOWN_DEVICE_NAME = "Unknown player"

        private const val RSSI_VALUE_LEVEL_STEP = 5.0
        private const val RSSI_VALUE_LEVEL_MIN = -110.0

        /* -110 */
        private const val RSSI_VALUE_LEVEL_1 = RSSI_VALUE_LEVEL_MIN

        /* -100 */
        private const val RSSI_VALUE_LEVEL_2 =
            RSSI_VALUE_LEVEL_1 + (RSSI_VALUE_LEVEL_STEP * 2)

        /* -90 */
        private const val RSSI_VALUE_LEVEL_3 =
            RSSI_VALUE_LEVEL_2 + (RSSI_VALUE_LEVEL_STEP * 2)

        /* -80 */
        private const val RSSI_VALUE_LEVEL_4 =
            RSSI_VALUE_LEVEL_3 + (RSSI_VALUE_LEVEL_STEP * 2)

        /* -70 */
        private const val RSSI_VALUE_LEVEL_5 =
            RSSI_VALUE_LEVEL_4 + (RSSI_VALUE_LEVEL_STEP)

        /* -65 */
        private const val RSSI_VALUE_LEVEL_6 =
            RSSI_VALUE_LEVEL_5 + (RSSI_VALUE_LEVEL_STEP)

        /* -60 */
        private const val RSSI_VALUE_LEVEL_7 =
            RSSI_VALUE_LEVEL_6 + (RSSI_VALUE_LEVEL_STEP)

        /* -55 */
        private const val RSSI_VALUE_LEVEL_8 =
            RSSI_VALUE_LEVEL_7 + (RSSI_VALUE_LEVEL_STEP)

        /* -50 */
        private const val RSSI_VALUE_LEVEL_9 =
            RSSI_VALUE_LEVEL_8 + (RSSI_VALUE_LEVEL_STEP)

        /* -45 */
        private const val RSSI_VALUE_LEVEL_10 =
            RSSI_VALUE_LEVEL_9 + (RSSI_VALUE_LEVEL_STEP)
    }

    private val connectionPower: ConnectionPowerLevel
        get() = when {
            getAccuracyRssiValue(lastRssiValue) <= RSSI_VALUE_LEVEL_1 -> Level1
            getAccuracyRssiValue(lastRssiValue) <= RSSI_VALUE_LEVEL_2 -> Level2
            getAccuracyRssiValue(lastRssiValue) <= RSSI_VALUE_LEVEL_3 -> Level3
            getAccuracyRssiValue(lastRssiValue) <= RSSI_VALUE_LEVEL_4 -> Level4
            getAccuracyRssiValue(lastRssiValue) <= RSSI_VALUE_LEVEL_5 -> Level5
            getAccuracyRssiValue(lastRssiValue) <= RSSI_VALUE_LEVEL_6 -> Level6
            getAccuracyRssiValue(lastRssiValue) <= RSSI_VALUE_LEVEL_7 -> Level7
            getAccuracyRssiValue(lastRssiValue) <= RSSI_VALUE_LEVEL_8 -> Level8
            getAccuracyRssiValue(lastRssiValue) <= RSSI_VALUE_LEVEL_9 -> Level9
            getAccuracyRssiValue(lastRssiValue) <= RSSI_VALUE_LEVEL_10 -> Level10

            else -> Level10
        }

    private fun getAccuracyRssiValue(currentRssiLevel: Int): Int {
        if (rssiAccuracyValues.size > 5) {
            rssiAccuracyValues.remove()
            rssiAccuracyValues.add(currentRssiLevel)
        } else {
            rssiAccuracyValues.add(currentRssiLevel)
        }

        return rssiAccuracyValues.sum() / rssiAccuracyValues.size
    }
}