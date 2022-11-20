package ru.palestra.hide_and_seek_exercise_c.presentation.man_looking.view

import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.Manifest.permission.BLUETOOTH_ADVERTISE
import android.Manifest.permission.BLUETOOTH_CONNECT
import android.Manifest.permission.BLUETOOTH_SCAN
import android.Manifest.permission.RECORD_AUDIO
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager.VERTICAL
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_COLLAPSED
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
import com.polidea.rxandroidble2.RxBleConnection.RxBleConnectionState
import kotlinx.coroutines.launch
import ru.palestra.hide_and_seek_exercise_c.R
import ru.palestra.hide_and_seek_exercise_c.data.ConnectedBleDevice
import ru.palestra.hide_and_seek_exercise_c.data.GameEvent
import ru.palestra.hide_and_seek_exercise_c.data.GameEvent.GameBirdPlaySound
import ru.palestra.hide_and_seek_exercise_c.data.GameEvent.GameBirdStopSound
import ru.palestra.hide_and_seek_exercise_c.data.GameEvent.GameInitialize
import ru.palestra.hide_and_seek_exercise_c.data.GameEvent.GamePlayerHided
import ru.palestra.hide_and_seek_exercise_c.data.GameEvent.GamePlayerInviteSuccess
import ru.palestra.hide_and_seek_exercise_c.data.GameEvent.GamePlayerLeave
import ru.palestra.hide_and_seek_exercise_c.data.GameEvent.GamePlayerResetAll
import ru.palestra.hide_and_seek_exercise_c.data.GameEvent.GamePlayerWasFound
import ru.palestra.hide_and_seek_exercise_c.data.GameEvent.GameRejected
import ru.palestra.hide_and_seek_exercise_c.databinding.ActivityLookingManBinding
import ru.palestra.hide_and_seek_exercise_c.domain.audio.AudioManager
import ru.palestra.hide_and_seek_exercise_c.domain.audio.AudioManagerApi
import ru.palestra.hide_and_seek_exercise_c.domain.audio.AudioManagerApi.NoiseLevel
import ru.palestra.hide_and_seek_exercise_c.domain.bluetooth.BluetoothManager
import ru.palestra.hide_and_seek_exercise_c.domain.bluetooth.BluetoothManagerApi
import ru.palestra.hide_and_seek_exercise_c.domain.location.NearbyLocationManager
import ru.palestra.hide_and_seek_exercise_c.domain.location.NearbyLocationManagerApi
import ru.palestra.hide_and_seek_exercise_c.presentation.choose_strategy.ChooseActivity
import ru.palestra.hide_and_seek_exercise_c.presentation.common.dialogs.DialogManager
import ru.palestra.hide_and_seek_exercise_c.presentation.common.dialogs.DialogManagerApi
import ru.palestra.hide_and_seek_exercise_c.presentation.common.permissions.PermissionManager
import ru.palestra.hide_and_seek_exercise_c.presentation.common.permissions.PermissionManagerApi
import ru.palestra.hide_and_seek_exercise_c.presentation.man_looking.adapter.AllFindDevicesAdapter
import ru.palestra.hide_and_seek_exercise_c.presentation.man_looking.adapter.InGameDevicesAdapter
import java.lang.String.format

/** Экран приложения для игрока, который ищет других. */
class LookingManActivity : AppCompatActivity() {

    companion object {

        /** Метод создания [Intent] для запуска [LookingManActivity]. */
        fun createIntent(context: Context) =
            Intent(context, LookingManActivity::class.java)
    }

    private lateinit var binding: ActivityLookingManBinding

    private var bluetoothManagerOrNull: BluetoothManagerApi? = null

    private val permissionManager: PermissionManagerApi<String> by lazy {
        PermissionManager(this)
    }

    private val audioManager: AudioManagerApi by lazy {
        AudioManager(applicationContext, this)
    }

    private val locationManager: NearbyLocationManagerApi by lazy {
        NearbyLocationManager(this)
    }

    private val dialogManager: DialogManagerApi by lazy {
        DialogManager(this)
    }

    private val bottomSheetBehavior: BottomSheetBehavior<LinearLayout> by lazy {
        BottomSheetBehavior.from(binding.bshFindDevices)
    }

    private val allFindDevicesAdapter: AllFindDevicesAdapter by lazy {
        AllFindDevicesAdapter(this) { findBleDevice ->
            bluetoothManagerOrNull?.let { bluetoothManager ->
                if (bluetoothManager.connectedBlePlayers.map { it.deviceMac }.contains(findBleDevice.deviceMac)) {
                    /* Попытка подключиться к тому же самому устройству. */
                    showToastMessage(R.string.already_invited)
                    return@AllFindDevicesAdapter
                }

                /* Обновляем состояние игрового UI (Отправлен инвайт в игру). */
                updateInviteUiIfNeeded(findBleDevice, true)

                /* Показываем UI, что пытаемся добавить в игру. */
                bluetoothManager.tryConnectToDevice(
                    deviceForConnect = findBleDevice,
                    onConnectionSuccessAction = { connectedBleDevice ->
                        /* 1. Подписываемся на события о статусе подключения клиента. */
                        observeDeviceConnectionState(bluetoothManager, connectedBleDevice)

                        /* 2. Подписываемся на входящие игровые события. */
                        observeGameEventFromPlayer(bluetoothManager, connectedBleDevice)

                        /* 3. Подписываемся на изменение уровня Rssi устройства. */
                        observeDeviceRssiLevel(bluetoothManager, connectedBleDevice)
                    },
                    onConnectionFailedAction = {
                        /* Откатываем состояние игрового UI. */
                        updateInviteUiIfNeeded(findBleDevice, false)

                        showToastMessage(
                            format(
                                getString(R.string.dialog_error_players_connect_failure),
                                findBleDevice.deviceName
                            )
                        )
                    }
                )
            }
        }
    }

    private val inGameDevicesAdapter: InGameDevicesAdapter by lazy {
        InGameDevicesAdapter(
            context = this,
            onItemsCountChangedAction = { playersCount ->
                binding.includeSearchManUi.playerListIsEmpty = playersCount <= 0
            },
            onBirdSoundPlayClickListener = { connectedBleDevice, isBirdPlayValue ->
                /* Необходимо запустить голос птиц на устройстве игрока. */
                bluetoothManagerOrNull?.let { bluetoothManager ->
                    /* Обновляем состояние игрового UI. */
                    updateSoundUiIfNeeded()

                    bluetoothManager.sendGameEventToDevice(
                        gameEvent = if (isBirdPlayValue) GameBirdStopSound else GameBirdPlaySound,
                        connectedBleDevice = connectedBleDevice
                    ) {
                        /* Откатываем состояние игрового UI. */
                        updateSoundUiIfNeeded()

                        showToastMessage(R.string.dialog_error_player_not_sing)
                    }
                }
            })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding = ActivityLookingManBinding.inflate(layoutInflater).also {
            setContentView(it.root)

            /* Изначально список игроков - пустой. */
            it.includeSearchManUi.playerListIsEmpty = inGameDevicesAdapter.itemCount <= 0

            /* Изначально UI громкости звука - выключен. */
            it.includeSearchManUi.enableSoundRecordUi = false
        }

        onBackPressedDispatcher.addCallback(object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (bottomSheetBehavior.state == STATE_EXPANDED) {
                    updateBottomSheetState(STATE_COLLAPSED)
                } else if (inGameDevicesAdapter.itemCount > 0) {
                    dialogManager.showSimpleQuestionsDialog(
                        textResId = R.string.dialog_info_game_question_rejected,
                        onPositiveAction = {
                            bluetoothManagerOrNull?.let { bluetoothManager ->
                                bluetoothManager.connectedBlePlayers.forEach { playerDevice ->
                                    /* Рассылаем всем игрокам сообщение, что игра была прервана. */
                                    bluetoothManager.sendGameEventToDevice(
                                        GameRejected,
                                        connectedBleDevice = playerDevice
                                    ) {
                                        /* Ignore. */
                                    }

                                    /* Отписываемся от всех игроков. */
                                    bluetoothManager.unsubscribeEverywhereFromDevice(playerDevice)
                                }
                            }

                            this.isEnabled = false
                            onBackPressedDispatcher.onBackPressed()
                        })
                } else {
                    this.isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    override fun onStart() {
        super.onStart()

        val neededPermissions = permissionManager.let {
            it.getBluetoothPermissionsForCurrentAndroidDevice() + it.getGpsPermissionsForCurrentAndroidDevice()
        }

        /* Шаг 0 - проверим наличие всех необходимых пермишенов. */
        permissionManager.checkMultiplePermissions(
            neededPermissions, ::handleCheckMultiplyPermissions
        )
    }

    override fun onStop() {
        stopVoiceRecord()
        super.onStop()
    }

    override fun onDestroy() {
        bluetoothManagerOrNull?.onDestroy()
        super.onDestroy()
    }

    private fun continueInitialize() {
        /* Шаг 1. Инициализация BluetoothAdapter'a. */
        initializeBluetoothManagerIfNeeded()

        bluetoothManagerOrNull?.let {
            /* Шаг 2. Инициализируем View компоненты. */
            initializeViews(it)

            /* Шаг 3. Инициализируем подписки на Bluetooth события. */
            observeAppStateEvents(it)
        }
    }

    private fun initializeBluetoothManagerIfNeeded() {
        bluetoothManagerOrNull = BluetoothManager.getInstance(this)
    }

    private fun changeScanNearbyPlayersState(scanEnabled: Boolean) {
        binding.pbSearchState.isVisible = scanEnabled
    }

    private fun initializeViews(bluetoothManager: BluetoothManagerApi) {
        binding.txtMyDeviceStateTitle.text = format(
            getString(R.string.my_device_state_title_text),
            bluetoothManager.getCurrentDeviceNameOrDefault()
        )

        /* Настраиваем отображение списка найденных девайсов. */
        with(binding.rvFindDevices) {
            adapter = allFindDevicesAdapter
            layoutManager = LinearLayoutManager(this@LookingManActivity, VERTICAL, false)
            addItemDecoration(DividerItemDecoration(this@LookingManActivity, VERTICAL))
        }

        /* Настраиваем отображение списка девайсов в игре. */
        with(binding.includeSearchManUi.rvPlayersInGame) {
            adapter = inGameDevicesAdapter
            layoutManager = LinearLayoutManager(this@LookingManActivity, VERTICAL, false)
            addItemDecoration(DividerItemDecoration(this@LookingManActivity, VERTICAL))
        }

        /* Обработчик нажатия кнопки "Начать поиск". */
        binding.btnStartSearch.setOnClickListener {
            bluetoothManager.runIfHardwareAvailable {
                allFindDevicesAdapter.removeAll()

                updateBottomSheetState(STATE_EXPANDED)
                changeScanNearbyPlayersState(true)

                if (!binding.btnStartSearch.isActivated) {
                    /* Начинаем поиск игроков. */
                    bluetoothManager.startScanNearbyPlayers(
                        isClientMode = false,
                        onDeviceFindAction = { findItem ->
                            allFindDevicesAdapter.addItem(findItem)
                        },
                        onDeviceLossAction = { lossItem ->
                            allFindDevicesAdapter.removeItem(lossItem)
                        },
                        onDiscoveryFailedAction = {
                            binding.btnStartSearch.isActivated = false
                            showToastMessage(it.message, Toast.LENGTH_LONG)
                        }
                    )

                    bluetoothManager.startGattServer(
                        isClientMode = false,
                        onServerStartSuccessAction = {
                            /* Сами сообщаем другим устройствам, что мы - сервер. */
                            bluetoothManager.startBleAdvertisingForMe(
                                isServerMode = true,
                                onStartBleAdvertisingSuccessAction = {
                                    //TODO("СЕРВЕР УСПЕШНО СТАРТАНУЛ ")
                                },
                                onStartBleAdvertisingFailureAction = {
                                    binding.btnStartSearch.isActivated = false
                                    //TODO("СЕРВЕР НЕ СМОГУТ ОБНАРУЖИТЬ")
                                }
                            )
                        },
                        onDeviceConnectedAction = {
                            /* Ignored */
                        }
                    )
                }

                binding.btnStartSearch.isActivated = true
            }
        }

        /* Обработчик нажатия кнопки "Остановить поиск". */
        binding.btnStopSearch.setOnClickListener {
            bluetoothManager.stopBleAdvertisingForMe()
            bluetoothManager.stopScanNearbyPlayers()

            changeScanNearbyPlayersState(false)
        }

        /* Обработчик нажатия кнопки "Исправить проблему доступа к моему устройству". */
        binding.txtDeviceStateConnectToMe.setOnClickListener {
            bluetoothManager.requestToEnableBluetooth(this)
        }

        /* Обработчики кнопки "Начать игру". */
        binding.includeSearchManUi.btnStartGame.setOnClickListener {
            if (inGameDevicesAdapter.itemCount == 0) {
                dialogManager.showSimpleErrorDialog(R.string.dialog_error_players_is_empty)
            } else if (inGameDevicesAdapter.hasNotAllPlayersIsHid()) {
                dialogManager.showSimpleErrorDialog(R.string.dialog_error_not_all_players_hid)
            } else {
                bottomSheetBehavior.isDraggable = false
                binding.btnStartSearch.isEnabled = false

                /* Скрываем кнопку "Начать игру". */
                binding.includeSearchManUi.btnStartGame.isVisible = false

                /* Прекращаем искать новые подключения. */
                binding.btnStopSearch.performClick()

                /* ообщаем всем игрокам, что игра началась. */
                sendEventForAllPlayers(bluetoothManager, GameInitialize)

                /* Отображаем поисковый UI для всех игроков. */
                inGameDevicesAdapter.startGame()
            }
        }
    }

    private fun sendEventForAllPlayers(bluetoothManager: BluetoothManagerApi, event: GameEvent) {
        bluetoothManager.connectedBlePlayers.forEach { connectedBleDevice ->
            bluetoothManager.sendGameEventToDevice(event, connectedBleDevice)
        }
    }

    private fun observeDeviceConnectionState(
        bluetoothManager: BluetoothManagerApi,
        connectedBleDevice: ConnectedBleDevice
    ) {
        bluetoothManager.observeDeviceConnectionState(
            connectedBleDevice = connectedBleDevice,
            onConnectWasChangedAction = { deviceWithNewState, rxBleConnectionState ->
                if (rxBleConnectionState == RxBleConnectionState.DISCONNECTED) {
                    bluetoothManager.unsubscribeEverywhereFromDevice(deviceWithNewState)

                    dialogManager.showSimpleErrorDialog(
                        format(getString(R.string.player_disconnected), deviceWithNewState.deviceName)
                    )

                    inGameDevicesAdapter.removeItem(deviceWithNewState)
                }
            }
        )
    }

    private fun observeGameEventFromPlayer(
        bluetoothManager: BluetoothManagerApi,
        connectedBleDevice: ConnectedBleDevice
    ) {
        bluetoothManager.observeGameEventFromDevice(
            connectedBleDevice = connectedBleDevice,
            onGameEventObtainedSuccessAction = ::handleGameEvent
        ) {
            it.printStackTrace()
            //TODO("Ошибка при чтении события, Удаляем пользвоателя???")
        }
    }

    private fun observeDeviceRssiLevel(bluetoothManager: BluetoothManagerApi, connectedBleDevice: ConnectedBleDevice) {
        bluetoothManager.observeDeviceRssiLevel(
            connectedBleDevice = connectedBleDevice,
            onObtainActualRssiSuccessAction = { updateRssiDevice ->
                inGameDevicesAdapter.addOrUpdateItem(updateRssiDevice)
            },
            onObtainActualRssiFailedAction = {
                /* Nothing. */
            }
        )
    }

    private fun handleGameEvent(connectedBleDevice: ConnectedBleDevice, event: GameEvent) {
        when (event) {
            GamePlayerInviteSuccess -> {
                /* Если игрок принял приглашение, добавлем его в список игроков. */
                inGameDevicesAdapter.addOrUpdateItem(connectedBleDevice.apply { playerWasHide = false })
            }
            GamePlayerHided -> {
                /* Если игрок спрятался, отмечаем его в списке. */
                inGameDevicesAdapter.addOrUpdateItem(connectedBleDevice.apply { playerWasHide = true })
            }
            GamePlayerWasFound -> {
                /* Сбрасываем состояние игрока. */
                bluetoothManagerOrNull?.sendGameEventToDevice(GamePlayerResetAll, connectedBleDevice)

                removePlayerAndCheckGameState(connectedBleDevice, true)
            }
            GamePlayerLeave -> removePlayerAndCheckGameState(connectedBleDevice, false)

            /* Ignore others. */
            else -> Unit
        }
    }

    private fun removePlayerAndCheckGameState(connectedBleDevice: ConnectedBleDevice, wasFound: Boolean) {
        /* Если игрока нашли, то удаляем и отписываемся от него. */
        inGameDevicesAdapter.removeItem(connectedBleDevice)

        showToastMessage(
            format(
                getString(if (wasFound) R.string.dialog_info_player_was_found else R.string.dialog_info_player_was_leave),
                connectedBleDevice.deviceName
            )
        )

        if (inGameDevicesAdapter.itemCount == 0) {
            dialogManager.showSimpleInfoDialog(R.string.dialog_info_looking_man_win) {
                bluetoothManagerOrNull?.connectedBlePlayers?.forEach {
                    bluetoothManagerOrNull?.unsubscribeEverywhereFromDevice(it)
                }

                bluetoothManagerOrNull?.stopBleAdvertisingForMe()
                bluetoothManagerOrNull?.stopGattServer()
                bluetoothManagerOrNull?.stopScanNearbyPlayers()

                showToastMessage(R.string.dialog_info_finish_game)

                finishAffinity()
            }
        } else {
            bluetoothManagerOrNull?.unsubscribeEverywhereFromDevice(connectedBleDevice)
        }
    }

    private fun updateInviteUiIfNeeded(connectedBleDevice: ConnectedBleDevice, isInvited: Boolean) {
        /* Обновляем список. */
        allFindDevicesAdapter.updateItem(connectedBleDevice.apply { deviceIsInvited = isInvited })
    }

    private fun updateSoundUiIfNeeded() {
        if (inGameDevicesAdapter.hasAnyPlayersWithActiveSound()) {
            /* Если есть игроки с включенным звуком. */
            enableSoundRecordFindUiIfNeeded()
        } else {
            /* Если нет игроков с включенным звуком. */
            disableSoundRecordFindUi()
        }
    }

    private fun enableSoundRecordFindUiIfNeeded() {
        if (binding.includeSearchManUi.enableSoundRecordUi == false) {
            binding.includeSearchManUi.enableSoundRecordUi = true

            handleRecordAction()
        }
    }

    private fun disableSoundRecordFindUi() {
        binding.includeSearchManUi.enableSoundRecordUi = false
        stopVoiceRecord()
    }

    private fun handleRecordAction(): Boolean {
        /* Нужно проверить наличие разрешений на запись голоса. */
        var permissionGranted = false
        permissionManager.checkVoiceRecordPermissionIfNeeded(
            onPermissionGranted = {
                /* Разрешения получены, разрешаем запись голоса.  */
                permissionGranted = true

                /* Начинаем запись голоса. */
                lifecycleScope.launch {
                    audioManager.startRecordingAndGetNoiseLevel(::handleNoiseLevel)
                }
            },
            onPermissionDenied = {
                /* Разрешения не были получены, объявняем почему они нам необходимы.  */
                dialogManager.showRecordAudioPermissionDeniedInfoDialog {
                    permissionManager.requestMultiplePermissions(arrayOf(RECORD_AUDIO)) {
                        if (it[RECORD_AUDIO] == true) {
                            /* Начинаем запись голоса. */
                            lifecycleScope.launch {
                                audioManager.startRecordingAndGetNoiseLevel(::handleNoiseLevel)
                            }

                            return@requestMultiplePermissions
                        }
                    }
                }
                return@checkVoiceRecordPermissionIfNeeded
            },
            onPermissionDeniedPermanent = {
                /*
                    Разрешения не были получены и пользователь отказался навсегда.
                    Открываем системные настройки, т.к. дальше работать не можем.
                */
                dialogManager.showRecordAudioPermissionPermanentDeniedInfoDialog {
                    permissionManager.requestToEnablePermissions(this@LookingManActivity)
                }
                return@checkVoiceRecordPermissionIfNeeded
            }
        )

        if (!permissionGranted) {
            /* Блокируем дальнейшую работу кнопки. */
            return false
        }

        return true
    }

    private fun handleNoiseLevel(rawDbValueOrNull: Double?, noiseLevelOrNull: NoiseLevel?) {
        noiseLevelOrNull?.let { noiseLevel ->
            binding.includeSearchManUi.nearbySoundProgress.run {
                setProgressWithAnimation(noiseLevel.value)
                setBackgroundProgressColor(getColor(noiseLevel.levelColor))
                setBackgroundProgressColor(getColor(noiseLevel.levelColor))

                rawDbValueOrNull?.let { rawDbValue ->
                    binding.includeSearchManUi.noiseLevelProgressBar.progress = rawDbValue.toInt()
                }
            }
        }
    }

    private fun observeAppStateEvents(bluetoothManager: BluetoothManagerApi) {
        /* Следим за проблемами подключения к другим устройствам. */
        bluetoothManager.observeBluetoothState(
            onBluetoothChangeAvailableStateAction = { bluetoothEnable ->
                updateConnectToMeButtonState(bluetoothEnable)

                if (!bluetoothEnable) {
                    showDisabledBluetoothInfoDialog()
                }
            }
        )
    }

    private fun handleCheckMultiplyPermissions(result: Map<String, Boolean>) {
        if (bluetoothManagerOrNull == null) {
            if (result.all { it.value }) {
                /* Все пермишены получены - продолжаем работу. */
                continueInitialize()
            } else {
                /* Разрашений недостаточно - просим пользователя о помощи. */
                dialogManager.showOnboardingInfoDialog { requestRequiredPermissions(result) }
            }
        }
    }

    private fun requestRequiredPermissions(result: Map<String, Boolean>) {
        permissionManager.requestMultiplePermissions(result.keys.toTypedArray()) { permissions ->
            permissions.entries.forEach { permissionMap ->
                when (permissionMap.key) {
                    BLUETOOTH_SCAN, BLUETOOTH_CONNECT, BLUETOOTH_ADVERTISE -> {
                        /* Если пользователь не предоставил данное разрешение - объявняем и открываем настройки. */
                        dialogManager.showBluetoothPermissionDeniedInfoDialog {
                            permissionManager.requestToEnablePermissions(this)
                        }

                        return@requestMultiplePermissions
                    }

                    ACCESS_FINE_LOCATION -> {
                        /* Если пользователь не предоставил данное разрешение - объявняем и открываем настройки. */
                        dialogManager.showGpsPermissionDeniedInfoDialog {
                            locationManager.requestToEnableGpsLocation()
                        }
                        return@requestMultiplePermissions
                    }
                }
            }
        }
    }

    private fun updateBottomSheetState(state: Int) {
        if (bottomSheetBehavior.state != state) {
            bottomSheetBehavior.state = state
        }
    }

    private fun showDisabledGpsInfoDialog() {
        dialogManager.showDisabledGpsInfoDialog {
            locationManager.requestToEnableGpsLocation()
        }
    }

    private fun showDisabledBluetoothInfoDialog() {
        dialogManager.showDisabledBluetoothInfoDialog {
            bluetoothManagerOrNull?.requestToEnableBluetooth(this)
        }
    }

    private fun updateConnectToMeButtonState(isSuccess: Boolean) {
        val textRes =
            if (isSuccess) R.string.my_device_state_connect_to_me_success
            else R.string.my_device_state_connect_to_me_failed

        binding.txtDeviceStateConnectToMe.changeState(text = getString(textRes), enabled = !isSuccess)
    }

    private fun stopVoiceRecord() {
        permissionManager.checkVoiceRecordPermissionIfNeeded(
            onPermissionGranted = {
                audioManager.stopRecording()
            }
        )
    }

    private fun showToastMessage(@StringRes messageRes: Int, duration: Int = Toast.LENGTH_SHORT) =
        showToastMessage(getString(messageRes), duration)

    private fun showToastMessage(messageText: String?, duration: Int = Toast.LENGTH_SHORT) =
        Toast.makeText(this, messageText, duration).show()

    private fun TextView.changeState(text: String, enabled: Boolean, visible: Boolean = true) =
        with(this) {
            setText(text)
            isEnabled = enabled
            isVisible = visible
        }

    private inline fun BluetoothManagerApi.runIfHardwareAvailable(crossinline block: () -> Unit) {
        if (!isBluetoothEnabled()) showDisabledBluetoothInfoDialog()
        else if (!locationManager.isGpsLocationEnabled()) showDisabledGpsInfoDialog()
        else block()
    }
}