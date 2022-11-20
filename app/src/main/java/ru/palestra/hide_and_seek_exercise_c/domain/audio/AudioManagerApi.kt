package ru.palestra.hide_and_seek_exercise_c.domain.audio

import androidx.annotation.ColorRes
import ru.palestra.hide_and_seek_exercise_c.R

/** Описание объекта, отвечающего за работу со звуком на устройстве пользователя. */
interface AudioManagerApi {

    /** Уровни громкости окружающего шума. */
    sealed class NoiseLevel(val value: Float, @ColorRes val levelColor: Int) {
        object Level1 : NoiseLevel(1F, R.color.palette_ble_level_1)
        object Level2 : NoiseLevel(2F, R.color.palette_ble_level_2)
        object Level3 : NoiseLevel(3F, R.color.palette_ble_level_3)
        object Level4 : NoiseLevel(4F, R.color.palette_ble_level_4)
        object Level5 : NoiseLevel(5F, R.color.palette_ble_level_5)
        object Level6 : NoiseLevel(6F, R.color.palette_ble_level_6)
        object Level7 : NoiseLevel(7F, R.color.palette_ble_level_7)
        object Level8 : NoiseLevel(8F, R.color.palette_ble_level_8)
        object Level9 : NoiseLevel(9F, R.color.palette_ble_level_9)
        object Level10 : NoiseLevel(10F, R.color.palette_ble_level_10)
    }

    /** Метод предназначен для воспроизведения звука шума птиц. */
    fun playBirdNoiseSound()

    /** Метод предназначен для остановки звука шума птиц. */
    fun stopBirdNoiseSound()

    /**
     * Метод предназначен для начала записи звука и определения его громкости при помощи микрофона устройства.
     *
     * @param onDbLevelObtainedAction действие вычисления уровня громкости.
     * */
    suspend fun startRecordingAndGetNoiseLevel(onDbLevelObtainedAction: (Double?, NoiseLevel?) -> Unit)

    /** Метод предназначен для прекращения записи голоса. */
    fun stopRecording()

    /** Метод предназначен для начала воспроизведения потокового аудио. */
    fun playAudio()

    /** Метод предназначен для прекращения воспроизведения потокового аудио. */
    fun stopAudio()
}