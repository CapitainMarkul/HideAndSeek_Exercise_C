package ru.palestra.hide_and_seek_exercise_c.presentation.man_hiding

import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.Manifest.permission.BLUETOOTH_ADVERTISE
import android.Manifest.permission.BLUETOOTH_CONNECT
import android.Manifest.permission.BLUETOOTH_SCAN
import android.annotation.SuppressLint
import android.bluetooth.le.AdvertiseCallback
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.polidea.rxandroidble2.RxBleConnection.RxBleConnectionState.DISCONNECTED
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
import ru.palestra.hide_and_seek_exercise_c.databinding.ActivityHidManBinding
import ru.palestra.hide_and_seek_exercise_c.domain.audio.AudioManager
import ru.palestra.hide_and_seek_exercise_c.domain.audio.AudioManagerApi
import ru.palestra.hide_and_seek_exercise_c.domain.bluetooth.BluetoothManager
import ru.palestra.hide_and_seek_exercise_c.domain.bluetooth.BluetoothManagerApi
import ru.palestra.hide_and_seek_exercise_c.domain.location.NearbyLocationManager
import ru.palestra.hide_and_seek_exercise_c.domain.location.NearbyLocationManagerApi
import ru.palestra.hide_and_seek_exercise_c.presentation.common.dialogs.DialogManager
import ru.palestra.hide_and_seek_exercise_c.presentation.common.dialogs.DialogManagerApi
import ru.palestra.hide_and_seek_exercise_c.presentation.common.permissions.PermissionManager
import ru.palestra.hide_and_seek_exercise_c.presentation.common.permissions.PermissionManagerApi
import java.lang.String.format

/** ?????????? ???????????????????? ?????? ????????????, ?????????????? ????????????????. */
class HidingManActivity : AppCompatActivity() {
    companion object {

        /** ?????????? ???????????????? [Intent] ?????? ?????????????? [HidingManActivity]. */
        fun createIntent(context: Context) =
            Intent(context, HidingManActivity::class.java)
    }


    private lateinit var binding: ActivityHidManBinding

    private var bluetoothManagerOrNull: BluetoothManagerApi? = null
    private var connectedGameMasterDeviceOrNull: ConnectedBleDevice? = null

    private var isEndGame = false

    private val fadeAnimation: Animation by lazy {
        AnimationUtils.loadAnimation(this, R.anim.fade_anim)
    }

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding = ActivityHidManBinding.inflate(layoutInflater).also {
            setContentView(it.root)
        }

        onBackPressedDispatcher.addCallback(object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                connectedGameMasterDeviceOrNull?.let { connectedDevice ->
                    bluetoothManagerOrNull?.let { bluetoothManager ->
                        dialogManager.showSimpleInfoDialog(
                            textResId = R.string.dialog_info_game_question_rejected,
                            onPositiveAction = {
                                /* ???????????????????? ?????????????? ??????????????????, ?????? ??????????????????????. */
                                bluetoothManager.sendGameEventToDevice(
                                    GamePlayerLeave,
                                    connectedBleDevice = connectedDevice
                                ) {
                                    /* Ignore. */
                                }

                                /* ???????????????????????? ???? ???????? ?????????????? ??????????????. */
                                bluetoothManager.unsubscribeEverywhereFromDevice(connectedDevice)

                                /* ?????????????????? ???????? ???????????????? ?????? ???????????? ??????????????????. */
                                bluetoothManager.stopBleAdvertisingForMe()

                                onBackPressedDispatcher.onBackPressed()
                            }
                        )
                    }
                } ?: onBackPressedDispatcher.onBackPressed()
            }
        })
    }

    override fun onStart() {
        super.onStart()

        val neededPermissions = permissionManager.let {
            it.getBluetoothPermissionsForCurrentAndroidDevice() + it.getGpsPermissionsForCurrentAndroidDevice()
        }

        /* ?????? 0 - ???????????????? ?????????????? ???????? ?????????????????????? ????????????????????. */
        permissionManager.checkMultiplePermissions(
            neededPermissions, ::handleCheckMultiplyPermissions
        )
    }

    override fun onDestroy() {
        bluetoothManagerOrNull?.onDestroy()
        super.onDestroy()
    }

    private fun handleCheckMultiplyPermissions(result: Map<String, Boolean>) {
        if (bluetoothManagerOrNull == null) {
            if (result.all { it.value }) {
                /* ?????? ?????????????????? ???????????????? - ???????????????????? ????????????. */
                continueInitialize()
            } else {
                /* ???????????????????? ???????????????????????? - ???????????? ???????????????????????? ?? ????????????. */
                dialogManager.showOnboardingInfoDialog { requestRequiredPermissions(result) }
            }
        }
    }

    private fun continueInitialize() {
        /* ?????? 1. ?????????????????????????? BluetoothAdapter'a. */
        initializeBluetoothManagerIfNeeded()

        bluetoothManagerOrNull?.let {
            /* ?????? 2. ???????????????????????????? View ????????????????????. */
            initializeViews(it)

            /* ?????? 3. ???????????????????????????? ???????????????? ???? Bluetooth ??????????????. */
            observeAppStateEvents(it)
        }
    }

    private fun initializeViews(bluetoothManager: BluetoothManagerApi) {
        /* ?????????????????????????? ?????? ???????????????????? ????????????????????????. */
        binding.txtMyDeviceStateTitle.text = format(
            getString(R.string.my_device_state_title_text),
            bluetoothManager.getCurrentDeviceNameOrDefault()
        )

        /* ???????????????????? ?????????????? ???????????? "?????????????????? ???????????????? ?????????????? ?? ?????????? ????????????????????". */
        binding.txtDeviceStateConnectToMe.setOnClickListener {
            bluetoothManager.requestToEnableBluetooth(this)
        }

        /* ???????????????????? ?????????????? ???????????? "?????????????????? ?????????????????? ????????????????". */
        binding.includeStartUi.btnTryStartInviteAccess.setOnClickListener { view ->
            bluetoothManager.runIfHardwareAvailable {
                /* ?????????????????? ?????????????????? UI. */
                updateSearchUiState(currentSearchState = view.isActivated)

                if (view.isActivated) {
                    /* ?????????????? ???????????????? ?????????????????????? ???? ??????????????. */
                    bluetoothManager.startGattServer(
                        isClientMode = true,
                        onServerStartSuccessAction = {
                            /* ?????????????? ??????????????, ?????? ???????????? ????????????. */
                            bluetoothManager.startBleAdvertisingForMe(
                                isServerMode = false,
                                onStartBleAdvertisingSuccessAction = { },
                                onStartBleAdvertisingFailureAction = { errorCode ->
                                    if (errorCode == AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE) {
                                        /* ???????????? ???? ????????????. */
                                        dialogManager.showSimpleErrorDialog(R.string.dialog_error_device_name_to_large)

                                        return@startBleAdvertisingForMe
                                    }

                                    view.isActivated = false

                                    /* ?????????????????? ?????????????????? UI. */
                                    updateSearchUiState(currentSearchState = view.isActivated)

                                    /* ???????????? ???? ????????????. */
                                    dialogManager.showSimpleErrorDialog(R.string.dialog_error_player_not_visible)
                                }
                            )
                        },
                        onDeviceConnectedAction = { findDevice ->
                            /* ?????? ?????????? ???????????? ?? ???????????????? ???????????? ???? ??????????????????????. */
                            bluetoothManager.tryConnectToDevice(
                                deviceForConnect = findDevice,
                                onConnectionSuccessAction = { connectedDevice ->
                                    /* ???????????????????? ????????????. */
                                    connectedGameMasterDeviceOrNull = connectedDevice

                                    /* ???? ???????? ?????????????? ???????????????????????? ?? ??????????????, ?????????????? ?????? ??????????. */
                                    updateSearchUiState(
                                        currentSearchState = true,
                                        inviteHasObtained = true
                                    )

                                    /* ???????????????? ?????????????????????????? ?????????????? ??????????????. */
                                    bluetoothManager.observeGameEventFromDevice(
                                        connectedBleDevice = connectedDevice,
                                        onGameEventObtainedSuccessAction = ::handleGameEvent,
                                        onGameEventObtainedFailureAction = {
                                            Unit
                                        }
                                    )

                                    /* 1. ?????????????????????????? ???? ?????????????? ?? ?????????????? ?????????????????????? ??????????????. */
                                    observeDeviceConnectionState(bluetoothManager, connectedDevice)

                                    Handler(Looper.getMainLooper()).postDelayed({
                                        /* ???????????????????? ??????????????, ?????? ?????????????? ??????????????????????. */
                                        bluetoothManager.sendGameEventToDevice(
                                            gameEvent = GamePlayerInviteSuccess,
                                            connectedBleDevice = connectedDevice
                                        ) {
                                            /* FIXME: ???????????????? UI ?? ???????????? ???????????? */
                                        }
                                    }, 2000) /* FIXME: ???????????????? - ???? ?????????????? ((( */
                                },
                                onConnectionFailedAction = {
                                    view.isActivated = false

                                    it.printStackTrace()
                                }
                            )
                        }
                    )
                } else {
                    disconnectFromServerIdNeeded()
                    bluetoothManager.stopBleAdvertisingForMe()
                    bluetoothManager.stopGattServer()
                }
            }
        }

        /* ???????????????????? ?????????????? ???????????? "?? ??????????????????". */
        binding.includeStartUi.btnIAmHide.setOnClickListener { view ->
            connectedGameMasterDeviceOrNull?.let { connectedBleDevice ->
//                view.isVisible = false

                /* ???????????? ?????????????????? ?????? ????????????????????????. */
                binding.includeStartUi.txtTryStartInviteAccessTitle.setText(
                    R.string.player_waiting_game_invite_waiting_all_hide
                )

                /* ???????????????????? ?????????????? ?????????????? ????????. */
                bluetoothManager.sendGameEventToDevice(
                    GamePlayerHided,
                    connectedBleDevice = connectedBleDevice
                ) {
                    view.isVisible = true

                    /* ?????????????????????????????? ?????????????????? ?????? ????????????????????????. */
                    binding.includeStartUi.txtTryStartInviteAccessTitle.setText(
                        R.string.player_waiting_game_invite_obtained
                    )

                    showToastMessage(R.string.dialog_error_send_event_failure)
                }
            }
        }

        /* ???????????????????? ?????????????? ???????????? "???????? ??????????". */
        binding.includeGameUi.btnFoundMe.setOnClickListener {
            connectedGameMasterDeviceOrNull?.let { connectedBleDevice ->
                /* ???????????????????? ?????????????? ?????????????? ????????. */
                bluetoothManager.sendGameEventToDevice(
                    GamePlayerWasFound,
                    connectedBleDevice = connectedBleDevice
                ) {
                    showToastMessage(R.string.dialog_error_send_event_failure)
                }
            }
        }
    }

    private fun observeDeviceConnectionState(
        bluetoothManager: BluetoothManagerApi,
        connectedBleDevice: ConnectedBleDevice
    ) {
        bluetoothManager.observeDeviceConnectionState(
            connectedBleDevice = connectedBleDevice,
            onConnectWasChangedAction = { deviceWithNewState, rxBleConnectionState ->
                if (rxBleConnectionState == DISCONNECTED) {
                    if (!isEndGame) {
                        bluetoothManager.unsubscribeEverywhereFromDevice(deviceWithNewState)

                        dialogManager.showSimpleErrorDialog(
                            getString(R.string.dialog_info_game_was_rejected)
                        )
                    }
                }
            }
        )
    }

    private fun showStartUi() {
        binding.includeGameUi.root.isVisible = false

        binding.includeStartUi.let {
            it.root.isVisible = true

            updateSearchUiState(false)
        }
    }

    private fun showGameUi() {
        binding.includeStartUi.root.isVisible = false
        updateSearchUiState(false)

        binding.includeGameUi.let {
            it.root.isVisible = true

            it.birdAlarmIsPlayingGameState = false
        }
    }

    @SuppressLint("MissingPermission")
    private fun observeAppStateEvents(bluetoothManager: BluetoothManagerApi) {
        /* ???????????? ???? ???????????????????? ?????????????????????? ?? ???????????? ??????????????????????. */
        bluetoothManager.observeBluetoothState(
            onBluetoothChangeAvailableStateAction = { bluetoothEnable ->
                updateConnectToMeButtonState(bluetoothEnable)

                if (!bluetoothEnable) {
                    showDisabledBluetoothInfoDialog()
                    bluetoothManager.stopBleAdvertisingForMe()
                }
            }
        )
    }

    private fun initializeBluetoothManagerIfNeeded() {
        bluetoothManagerOrNull = BluetoothManager.getInstance(this)
    }

    private fun requestRequiredPermissions(result: Map<String, Boolean>) {
        permissionManager.requestMultiplePermissions(result.keys.toTypedArray()) { permissions ->
            permissions.entries.forEach { permissionMap ->
                when (permissionMap.key) {
                    BLUETOOTH_SCAN, BLUETOOTH_CONNECT, BLUETOOTH_ADVERTISE -> {
                        /* ???????? ???????????????????????? ???? ?????????????????????? ???????????? ???????????????????? - ?????????????????? ?? ?????????????????? ??????????????????. */
                        dialogManager.showBluetoothPermissionDeniedInfoDialog {
                            permissionManager.requestToEnablePermissions(this)
                        }

                        return@requestMultiplePermissions
                    }

                    ACCESS_FINE_LOCATION -> {
                        /* ???????? ???????????????????????? ???? ?????????????????????? ???????????? ???????????????????? - ?????????????????? ?? ?????????????????? ??????????????????. */
                        dialogManager.showGpsPermissionDeniedInfoDialog {
                            locationManager.requestToEnableGpsLocation()
                        }
                        return@requestMultiplePermissions
                    }
                }
            }
        }
    }

    private fun handleGameEvent(connectedBleDevice: ConnectedBleDevice, event: GameEvent) {
        when (event) {
            GameInitialize -> showGameUi()
            GameBirdPlaySound -> updateBirdPlaySoundState(true)
            GameBirdStopSound -> updateBirdPlaySoundState(false)
            GameRejected -> {
                if (!isEndGame) {
                    dialogManager.showSimpleInfoDialog(R.string.dialog_info_game_was_rejected) {
                        showStartUi()
                    }
                }
            }
            GamePlayerResetAll -> {
                isEndGame = true
                dialogManager.showSimpleInfoDialog(R.string.dialog_info_player_game_over) {
                    /* ???????????????????????? ???? ?????????????? ????????. */
                    bluetoothManagerOrNull?.unsubscribeEverywhereFromDevice(connectedBleDevice)
                    bluetoothManagerOrNull?.stopScanNearbyPlayers()
                    bluetoothManagerOrNull?.stopGattServer()
                    bluetoothManagerOrNull?.stopBleAdvertisingForMe()

                    showToastMessage(R.string.dialog_info_finish_game)

                    finishAffinity()
                }
            }

            /* Ignore others. */
            else -> Unit
        }
    }

    private fun updateSearchUiState(
        currentSearchState: Boolean,
        inviteHasObtained: Boolean = false
    ) = with(binding.includeStartUi) {
        btnTryStartInviteAccess.isActivated = !currentSearchState

        /* ?????????????????????????? ?????????????????? ????????????. */
        btnIAmHide.isVisible = inviteHasObtained
        btnTryStartInviteAccess.isVisible = !inviteHasObtained

        /* ?????????????????????????? ?????????? ????????????????. */
        txtTryStartInviteAccessTitle.setText(
            if (inviteHasObtained) R.string.player_waiting_game_invite_obtained
            else if (btnTryStartInviteAccess.isActivated) R.string.player_waiting_game_invite_in_progress
            else R.string.player_waiting_game_invite_waiting
        )

        /* ?????????????????????????? ?????????? ????????????. */
        btnTryStartInviteAccess.setText(
            if (btnTryStartInviteAccess.isActivated) R.string.player_waiting_game_stop_search_server
            else R.string.player_waiting_game_start_search_server
        )

        /* ???????????????? ???????????????? ????????????. */
        txtTryStartInviteAccessTitle.let {
            if (inviteHasObtained || btnTryStartInviteAccess.isActivated) it.startAnimation(fadeAnimation) else it.clearAnimation()
        }
    }

    private fun updateBirdPlaySoundState(isEnable: Boolean) = with(binding) {
        birdAlarmIsPlayingGameState = isEnable

        if (isEnable) audioManager.playBirdNoiseSound() else audioManager.stopBirdNoiseSound()
    }

    private fun updateConnectToMeButtonState(isSuccess: Boolean) {
        val textRes =
            if (isSuccess) R.string.my_device_state_connect_to_me_success
            else R.string.my_device_state_connect_to_me_failed

        binding.txtDeviceStateConnectToMe.changeState(text = getString(textRes), enabled = !isSuccess)
    }

    private fun TextView.changeState(text: String, enabled: Boolean, visible: Boolean = true) =
        with(this) {
            setText(text)
            isEnabled = enabled
            isVisible = visible
        }

    private fun showDisabledBluetoothInfoDialog() {
        dialogManager.showDisabledBluetoothInfoDialog {
            bluetoothManagerOrNull?.requestToEnableBluetooth(this)
        }
    }

    private fun showDisabledGpsInfoDialog() {
        dialogManager.showDisabledGpsInfoDialog {
            locationManager.requestToEnableGpsLocation()
        }
    }

    private fun disconnectFromServerIdNeeded() {
        connectedGameMasterDeviceOrNull?.let { connectedDevice ->
            bluetoothManagerOrNull?.unsubscribeEverywhereFromDevice(connectedDevice)
        }

        connectedGameMasterDeviceOrNull = null
    }

    private fun showToastMessage(@StringRes messageRes: Int, duration: Int = Toast.LENGTH_SHORT) =
        showToastMessage(getString(messageRes), duration)

    private fun showToastMessage(messageText: String?, duration: Int = Toast.LENGTH_SHORT) =
        Toast.makeText(this, messageText, duration).show()

    private inline fun BluetoothManagerApi.runIfHardwareAvailable(crossinline block: () -> Unit) {
        if (!isBluetoothEnabled()) showDisabledBluetoothInfoDialog()
        else if (!locationManager.isGpsLocationEnabled()) showDisabledGpsInfoDialog()
        else block()
    }
}