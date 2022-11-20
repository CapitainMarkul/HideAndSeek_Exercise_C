package ru.palestra.hide_and_seek_exercise_c.domain.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.AudioTrack.PLAYSTATE_PLAYING
import android.media.AudioTrack.PLAYSTATE_STOPPED
import android.media.MediaPlayer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import io.reactivex.Emitter
import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.palestra.hide_and_seek_exercise_c.R
import ru.palestra.hide_and_seek_exercise_c.domain.audio.AudioManagerApi.NoiseLevel
import ru.palestra.hide_and_seek_exercise_c.domain.audio.AudioManagerApi.NoiseLevel.Level1
import ru.palestra.hide_and_seek_exercise_c.domain.audio.AudioManagerApi.NoiseLevel.Level10
import ru.palestra.hide_and_seek_exercise_c.domain.audio.AudioManagerApi.NoiseLevel.Level2
import ru.palestra.hide_and_seek_exercise_c.domain.audio.AudioManagerApi.NoiseLevel.Level3
import ru.palestra.hide_and_seek_exercise_c.domain.audio.AudioManagerApi.NoiseLevel.Level4
import ru.palestra.hide_and_seek_exercise_c.domain.audio.AudioManagerApi.NoiseLevel.Level5
import ru.palestra.hide_and_seek_exercise_c.domain.audio.AudioManagerApi.NoiseLevel.Level6
import ru.palestra.hide_and_seek_exercise_c.domain.audio.AudioManagerApi.NoiseLevel.Level7
import ru.palestra.hide_and_seek_exercise_c.domain.audio.AudioManagerApi.NoiseLevel.Level8
import ru.palestra.hide_and_seek_exercise_c.domain.audio.AudioManagerApi.NoiseLevel.Level9
import ru.palestra.hide_and_seek_exercise_c.domain.utils.disposeIfNeeded
import kotlin.math.abs
import kotlin.math.log10


/** Объект, отвечающий за работу со звуком на устройстве пользователя. */
@SuppressLint("MissingPermission")
internal class AudioManager(
    private val applicationContext: Context,
    private var lifecycleOwner: LifecycleOwner?
) : AudioManagerApi, LifecycleEventObserver {

    companion object {

        private const val NOISE_DB_VALUE_LEVEL_STEP = 5.0
        private const val NOISE_DB_VALUE_LEVEL_MIN = 15.0

        /* 15 */
        private const val NOISE_DB_VALUE_LEVEL_1 = NOISE_DB_VALUE_LEVEL_MIN

        /* 25 */
        private const val NOISE_DB_VALUE_LEVEL_2 =
            NOISE_DB_VALUE_LEVEL_1 + (NOISE_DB_VALUE_LEVEL_STEP * 2)

        /* 35 */
        private const val NOISE_DB_VALUE_LEVEL_3 =
            NOISE_DB_VALUE_LEVEL_2 + (NOISE_DB_VALUE_LEVEL_STEP * 2)

        /* 40 */
        private const val NOISE_DB_VALUE_LEVEL_4 =
            NOISE_DB_VALUE_LEVEL_3 + (NOISE_DB_VALUE_LEVEL_STEP)

        /* 45 */
        private const val NOISE_DB_VALUE_LEVEL_5 =
            NOISE_DB_VALUE_LEVEL_4 + (NOISE_DB_VALUE_LEVEL_STEP)

        /* 50 */
        private const val NOISE_DB_VALUE_LEVEL_6 =
            NOISE_DB_VALUE_LEVEL_5 + (NOISE_DB_VALUE_LEVEL_STEP)

        /* 55 */
        private const val NOISE_DB_VALUE_LEVEL_7 =
            NOISE_DB_VALUE_LEVEL_6 + (NOISE_DB_VALUE_LEVEL_STEP)

        /* 60 */
        private const val NOISE_DB_VALUE_LEVEL_8 =
            NOISE_DB_VALUE_LEVEL_7 + (NOISE_DB_VALUE_LEVEL_STEP)

        /* 65 */
        private const val NOISE_DB_VALUE_LEVEL_9 =
            NOISE_DB_VALUE_LEVEL_8 + (NOISE_DB_VALUE_LEVEL_STEP)

        /* 70 */
        private const val NOISE_DB_VALUE_LEVEL_10 =
            NOISE_DB_VALUE_LEVEL_9 + (NOISE_DB_VALUE_LEVEL_STEP)

        private const val REFERENCE: Double = 0.00002

        private const val DEFAULT_SAMPLING_RATE_IN_HZ = 44100
        private const val DEFAULT_CHANNEL_IN_MONO_CONFIG: Int = AudioFormat.CHANNEL_IN_MONO
        private const val DEFAULT_CHANNEL_OUT_MONO_CONFIG: Int = AudioFormat.CHANNEL_OUT_MONO
        private const val DEFAULT_AUDIO_ENCODING_FORMAT: Int = AudioFormat.ENCODING_PCM_16BIT
        private const val DEFAULT_STREAM_TYPE: Int = AudioManager.STREAM_MUSIC
        private const val DEFAULT_TRANSFER_TYPE: Int = AudioTrack.MODE_STREAM

        private const val BUFFER_SIZE_IN_SECOND = 2

        /** Размер буфера, который используется для чтения/записи. */
        val MIN_BUFFER_SIZE by lazy {
            AudioTrack.getMinBufferSize(
                DEFAULT_SAMPLING_RATE_IN_HZ,
                DEFAULT_CHANNEL_OUT_MONO_CONFIG,
                DEFAULT_AUDIO_ENCODING_FORMAT
            )
        }
    }

    init {
        lifecycleOwner?.lifecycle?.addObserver(this)
    }

    private val doubleBufferSize by lazy { MIN_BUFFER_SIZE * BUFFER_SIZE_IN_SECOND }

    /* Флаг активации записи звука с микрофона устройства. */
    private var isRecording = false
    private var writeBuffer: ShortArray = ShortArray(doubleBufferSize)
    private var emitterReadStream: Emitter<ByteArray>? = null

    private val observableReadStream: Observable<ByteArray> by lazy {
        Observable.create { emitterReadStream = it }
            .observeOn(Schedulers.computation())
    }

    private var audioStreamHandlerDisposable =
        observableReadStream.subscribe { audioStreamChunk ->
            audioPlayer.write(audioStreamChunk, 0, audioStreamChunk.size)
        }

    private var birdNoiseSoundPlayer: MediaPlayer? = null

    private var audioRecorder: AudioRecord? = null

    private val audioPlayer: AudioTrack by lazy {
        AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setLegacyStreamType(DEFAULT_STREAM_TYPE)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(DEFAULT_AUDIO_ENCODING_FORMAT)
                    .setChannelMask(DEFAULT_CHANNEL_OUT_MONO_CONFIG)
                    .setSampleRate(DEFAULT_SAMPLING_RATE_IN_HZ)
                    .build()
            )
            .setBufferSizeInBytes(doubleBufferSize)
            .setTransferMode(DEFAULT_TRANSFER_TYPE)
            .build()
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        if (event == Lifecycle.Event.ON_DESTROY) {
            stopAudio()

            if (isRecording) {
                stopRecording()
            }

            audioRecorder?.release()
            audioPlayer.release()
            birdNoiseSoundPlayer?.release()

            audioStreamHandlerDisposable?.disposeIfNeeded()

            lifecycleOwner?.lifecycle?.removeObserver(this)
            lifecycleOwner = null
        }
    }

    override fun playBirdNoiseSound() {
        birdNoiseSoundPlayer = MediaPlayer.create(applicationContext, R.raw.bird_noise).apply {
            isLooping = true
        }
        birdNoiseSoundPlayer?.start()
    }

    override fun stopBirdNoiseSound() {
        birdNoiseSoundPlayer?.stop()
    }

    override suspend fun startRecordingAndGetNoiseLevel(
        onDbLevelObtainedAction: (Double?, NoiseLevel?) -> Unit
    ) {
        isRecording = true
        writeBuffer = ShortArray(doubleBufferSize)

        if (audioRecorder == null) {
            audioRecorder = AudioRecord.Builder()
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(DEFAULT_AUDIO_ENCODING_FORMAT)
                        .setSampleRate(DEFAULT_SAMPLING_RATE_IN_HZ)
                        .setChannelMask(DEFAULT_CHANNEL_IN_MONO_CONFIG)
                        .build()
                )
                .setBufferSizeInBytes(doubleBufferSize)
                .build()

        }

        audioRecorder?.startRecording()

        withContext(Dispatchers.IO) {
            while (isRecording) {
                try {
                    audioRecorder?.read(writeBuffer, 0, doubleBufferSize)
                    val rawDbValue = getNoiseDbLevel(writeBuffer)

                    withContext(Dispatchers.Main) {
                        onDbLevelObtainedAction(rawDbValue, convertDbToNoiseLevel(rawDbValue))
                    }
                } catch (e: Exception) {
                    stopRecording()
                }
            }
        }
    }

    override fun stopRecording() {
        isRecording = false

        /* Затыкаем дыры в тонущем корабле... */
        try {
            audioRecorder?.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun playAudio() {
        if (audioPlayer.playState == PLAYSTATE_STOPPED) {
            audioPlayer.play()
        }
    }

    override fun stopAudio() {
        if (audioPlayer.playState == PLAYSTATE_PLAYING) {
            audioPlayer.stop()
        }
    }

    private fun convertDbToNoiseLevel(dbValue: Double?): NoiseLevel? {
        if (dbValue == null) return null

        return when {
            dbValue <= NOISE_DB_VALUE_LEVEL_1 -> Level1
            dbValue <= NOISE_DB_VALUE_LEVEL_2 -> Level2
            dbValue <= NOISE_DB_VALUE_LEVEL_3 -> Level3
            dbValue <= NOISE_DB_VALUE_LEVEL_4 -> Level4
            dbValue <= NOISE_DB_VALUE_LEVEL_5 -> Level5
            dbValue <= NOISE_DB_VALUE_LEVEL_6 -> Level6
            dbValue <= NOISE_DB_VALUE_LEVEL_7 -> Level7
            dbValue <= NOISE_DB_VALUE_LEVEL_8 -> Level8
            dbValue <= NOISE_DB_VALUE_LEVEL_9 -> Level9
            dbValue <= NOISE_DB_VALUE_LEVEL_10 -> Level10

            else -> Level10
        }
    }

    private fun getNoiseDbLevel(rawNoiseData: ShortArray): Double? {
        var average = 0.0
        for (s in rawNoiseData) {
            if (s > 0) average += abs(s.toInt()).toDouble()
            else doubleBufferSize - 1
        }

        val x = average / doubleBufferSize
        return if (x == 0.0) null else {
            val db = 20 * log10((x / 51805.5336) / REFERENCE)
            if (db > 0) db else null
        }
    }
}