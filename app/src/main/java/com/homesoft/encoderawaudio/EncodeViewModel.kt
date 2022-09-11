package com.homesoft.encoderawaudio

import android.annotation.SuppressLint
import android.app.Application
import android.media.*
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import java.io.File
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future


class EncodeViewModel(app: Application):AndroidViewModel(app) {
    enum class State {STARTING, RUNNING, ERROR, STOPPED}

    val recordStateData: MutableLiveData<State> = MutableLiveData(State.STOPPED)
    val playStateData: MutableLiveData<State> = MutableLiveData(State.STOPPED)
    val mp4File = File(getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_MOVIES), "out.mp4")

    private val recordExecutor = Executors.newSingleThreadExecutor()
    private var mediaPlayer : MediaPlayer? = null
    private var audioRecorderFuture: Future<Int>? = null

    fun getRecordState(): State {
        return recordStateData.value!!
    }

    fun getPlayState(): State {
        return playStateData.value!!
    }

    fun startRecord() {
        val state = getRecordState()
        if (state == State.STOPPED || state == State.ERROR) {
            recordStateData.postValue(State.STARTING)
            audioRecorderFuture = recordExecutor.submit(AudioRecorder())
        }
    }

    fun stopRecord() {
        audioRecorderFuture?.cancel(true)
    }

    fun startPlay() {
        val state = getRecordState()
        if (state == State.STOPPED || state == State.ERROR) {
            val mediaPlayer = MediaPlayer.create(getApplication(), Uri.fromFile(mp4File))

            mediaPlayer.setOnCompletionListener { playStateData.postValue(State.STOPPED) }
            mediaPlayer.setOnErrorListener { _, _, _ ->
                playStateData.postValue(State.ERROR)
                false
            }
            this.mediaPlayer = mediaPlayer
            mediaPlayer.start()
            playStateData.postValue(State.RUNNING)
        }

    }

    fun stopPlay() {
        mediaPlayer?.let {
            it.stop()
            it.release()
            playStateData.postValue(State.STOPPED)
        }
    }

    val recordErrorCode: Int
        get() = audioRecorderFuture?.get() ?: 0
    
    inner class AudioRecorder:MediaCodec.Callback(), Callable<Int> {
        @SuppressLint("MissingPermission")
        private val audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_MASK, FORMAT, BUFFER_SIZE)
        private val mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        private val bufferIndexQueue = ArrayBlockingQueue<Int>(32)
        private lateinit var thread : Thread
        private lateinit var mediaMuxer : MediaMuxer
        private var trackIndex = -1
        private var status = STATUS_INIT_ERROR

        override fun call(): Int {
            thread = Thread.currentThread()
            if (audioRecord.state == AudioRecord.STATE_UNINITIALIZED) {
                return status
            }
            val mediaFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, CHANNELS)
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 160 * 1024)
            try {
                mediaMuxer = MediaMuxer(mp4File.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                mediaFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)

                mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                mediaCodec.setCallback(this)
                mediaCodec.start()

                audioRecord.startRecording()
                status = STATUS_OK
                recordStateData.postValue(State.RUNNING)
                while (!thread.isInterrupted) {
                    val bufferIndex = bufferIndexQueue.take()
                    val byteBuffer = mediaCodec.getInputBuffer(bufferIndex)
                    if (byteBuffer == null) {
                        status = STATUS_NO_ENCODER_INPUT_BUFFER
                        break
                    }
                    val readSize = (BUFFER_SIZE / 2).coerceAtMost(byteBuffer.capacity())

                    val bytes = audioRecord.read(byteBuffer, readSize)
                    if (bytes < 0) {
                        status = bytes
                        break
                    }
                    mediaCodec.queueInputBuffer(bufferIndex, 0, bytes, 0, 0)
                }
            } finally {
                audioRecord.stop()
                audioRecord.release()
                mediaCodec.stop()
                mediaCodec.release()
                mediaMuxer.stop()
                mediaMuxer.release()
            }
            if (status == STATUS_OK) {
                recordStateData.postValue(State.STOPPED)
            } else {
                recordStateData.postValue(State.ERROR)
            }
            return status
        }

        override fun onInputBufferAvailable(p0: MediaCodec, index: Int) {
            bufferIndexQueue.offer(index)
        }

        override fun onOutputBufferAvailable(mediaCodec: MediaCodec, index: Int, bufferInfo: MediaCodec.BufferInfo) {
            val byteBuffer =  mediaCodec.getOutputBuffer(index)
            if (byteBuffer == null) {
                status = STATUS_NO_ENCODER_OUTPUT_BUFFER
                thread.interrupt()
            } else {
                // Herein lies the magic
                // MediaCodec adds the undocumented field "csd-0" which contains the media codec specific data
                // It also adds KEY_AAC_PROFILE and KEY_MAX_BIT_RATE (hidden in AOSP)
                if (trackIndex == -1) {
                    trackIndex = mediaMuxer.addTrack(mediaCodec.outputFormat)
                    mediaMuxer.start()
                }
                mediaMuxer.writeSampleData(trackIndex, byteBuffer, bufferInfo)
                mediaCodec.releaseOutputBuffer(index, false)
            }
        }

        override fun onError(p0: MediaCodec, p1: MediaCodec.CodecException) {
            status = STATUS_ENCODER_ERROR
            thread.interrupt()
        }

        override fun onOutputFormatChanged(p0: MediaCodec, p1: MediaFormat) {
            //Don't care
        }
    }

    companion object {
        // CD Quality Audio
        const val SAMPLE_RATE = 44100
        const val CHANNEL_MASK = AudioFormat.CHANNEL_IN_STEREO
        val CHANNELS = Integer.bitCount(CHANNEL_MASK)
        const val FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val STATUS_OK = 0
        const val STATUS_INIT_ERROR = -0xff
        const val STATUS_NO_ENCODER_INPUT_BUFFER = -0x100
        const val STATUS_NO_ENCODER_OUTPUT_BUFFER = -0x101
        const val STATUS_ENCODER_ERROR = -0x102
        val BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_MASK, FORMAT) * 8
    }

}