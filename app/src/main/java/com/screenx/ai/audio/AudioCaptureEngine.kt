package com.screenx.ai.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Build
import android.os.SystemClock
import androidx.annotation.RequiresApi
import com.screenx.ai.domain.AudioSource
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max

class AudioCaptureException(message: String) : Exception(message)

/**
 * Captures microphone and/or internal (playback) audio, mixes it to stereo 44.1 kHz PCM,
 * encodes AAC and writes an audio-only MP4 to [outputFile].
 *
 * Timestamps come from the number of PCM frames, so audio never drifts and pausing simply
 * skips frames (matching MediaRecorder.pause() for the video).
 *
 * Internal audio needs Android 10+ and only captures apps that allow it.
 */
class AudioCaptureEngine(
    @Suppress("unused") private val context: Context,
    private val projection: MediaProjection,
    private val source: AudioSource,
    private val bitrateKbps: Int,
    private val outputFile: File
) {
    /** Called when capture stops unexpectedly. The video keeps recording. */
    var onError: ((String) -> Unit)? = null

    @Volatile
    var startedAtNanos: Long = 0L
        private set

    @Volatile private var running = false
    @Volatile private var paused = false
    @Volatile private var produced = false

    private var micRecord: AudioRecord? = null
    private var internalRecord: AudioRecord? = null
    private var encoder: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var muxerStarted = false
    private var trackIndex = -1
    private var thread: Thread? = null
    private var framesFed = 0L

    /** Creates recorders and the encoder. Throws [AudioCaptureException] if anything is unavailable. */
    fun prepare() {
        try {
            if (source.usesMic) micRecord = createMic()
            if (source.usesInternal) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    throw AudioCaptureException("Internal audio needs Android 10 or newer")
                }
                internalRecord = createInternal()
            }
            val format = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, CHANNELS
            ).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, bitrateKbps * 1000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
            }
            val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            encoder = codec
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        } catch (e: AudioCaptureException) {
            release()
            throw e
        } catch (e: Exception) {
            release()
            throw AudioCaptureException(e.message ?: e.javaClass.simpleName)
        }
    }

    /** Starts capturing. Call right after the video recorder has started. */
    fun begin() {
        try {
            micRecord?.startRecording()
            internalRecord?.startRecording()
        } catch (e: IllegalStateException) {
            throw AudioCaptureException(e.message ?: "Cannot start audio capture")
        }
        val notRecording = listOfNotNull(micRecord, internalRecord)
            .any { it.recordingState != AudioRecord.RECORDSTATE_RECORDING }
        if (notRecording) {
            throw AudioCaptureException("Audio input is busy (another app may be using it)")
        }
        startedAtNanos = System.nanoTime()
        running = true
        thread = Thread({ captureLoop() }, "ScreenX-Audio").also { it.start() }
    }

    fun pause() {
        paused = true
    }

    fun resume() {
        paused = false
    }

    /** Finishes the audio file. Returns true if it contains audio. */
    fun stop(): Boolean {
        running = false
        runCatching { micRecord?.stop() }
        runCatching { internalRecord?.stop() }
        runCatching { thread?.join(3000) }
        thread = null

        var ok = false
        try {
            val codec = encoder
            if (codec != null) {
                val info = MediaCodec.BufferInfo()
                val idx = codec.dequeueInputBuffer(100_000)
                if (idx >= 0) {
                    codec.queueInputBuffer(idx, 0, 0, ptsUs(), MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                }
                drain(codec, info, endOfStream = true)
            }
            if (muxerStarted) {
                muxer?.stop()
                ok = produced
            }
        } catch (e: Exception) {
            ok = false
        } finally {
            release()
        }
        return ok
    }

    /** Stops everything and deletes the audio file. */
    fun abort() {
        running = false
        runCatching { micRecord?.stop() }
        runCatching { internalRecord?.stop() }
        runCatching { thread?.join(3000) }
        thread = null
        release()
        runCatching { outputFile.delete() }
    }

    // ---- capture thread ----

    private fun captureLoop() {
        val mic = micRecord
        val internal = internalRecord
        val micBuf = ShortArray(FRAME_SAMPLES)
        val internalBuf = ShortArray(FRAME_SAMPLES * 2)
        val mixed = ShortArray(FRAME_SAMPLES * 2)
        val bytes = ByteBuffer.allocate(FRAME_SAMPLES * 4).order(ByteOrder.LITTLE_ENDIAN)
        val info = MediaCodec.BufferInfo()
        val codec = encoder ?: return

        try {
            while (running) {
                // Always read, even when paused, so the recorders' buffers stay fresh.
                if (mic != null && !readFully(mic, micBuf)) {
                    if (running) throw IOException("Microphone read failed") else break
                }
                if (internal != null && !readFully(internal, internalBuf)) {
                    if (running) throw IOException("Internal audio read failed") else break
                }
                if (paused) continue

                mix(mic != null, internal != null, micBuf, internalBuf, mixed)

                bytes.clear()
                bytes.asShortBuffer().put(mixed)
                bytes.position(0)
                bytes.limit(FRAME_SAMPLES * 4)

                drain(codec, info, endOfStream = false)
                var idx = codec.dequeueInputBuffer(20_000)
                if (idx < 0) {
                    drain(codec, info, endOfStream = false)
                    idx = codec.dequeueInputBuffer(20_000)
                }
                if (idx >= 0) {
                    val input = codec.getInputBuffer(idx)
                    if (input != null) {
                        input.clear()
                        input.put(bytes)
                        codec.queueInputBuffer(idx, 0, FRAME_SAMPLES * 4, ptsUs(), 0)
                    }
                }
                framesFed += FRAME_SAMPLES // keep the timeline exact even if a frame was dropped
            }
        } catch (e: Exception) {
            running = false
            onError?.invoke(e.message ?: e.javaClass.simpleName)
        }
    }

    private fun mix(
        hasMic: Boolean,
        hasInternal: Boolean,
        micBuf: ShortArray,
        internalBuf: ShortArray,
        out: ShortArray
    ) {
        for (i in 0 until FRAME_SAMPLES) {
            var l: Int
            var r: Int
            if (hasMic && hasInternal) {
                val m = micBuf[i].toInt()
                l = internalBuf[2 * i] + m
                r = internalBuf[2 * i + 1] + m
            } else if (hasMic) {
                l = micBuf[i].toInt()
                r = l
            } else {
                l = internalBuf[2 * i].toInt()
                r = internalBuf[2 * i + 1].toInt()
            }
            out[2 * i] = l.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            out[2 * i + 1] = r.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    private fun readFully(record: AudioRecord, buffer: ShortArray): Boolean {
        var offset = 0
        while (offset < buffer.size && running) {
            val n = record.read(buffer, offset, buffer.size - offset, AudioRecord.READ_BLOCKING)
            if (n < 0) return false
            offset += n
        }
        return offset == buffer.size
    }

    private fun ptsUs(): Long = framesFed * 1_000_000L / SAMPLE_RATE

    private fun drain(codec: MediaCodec, info: MediaCodec.BufferInfo, endOfStream: Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 2000
        while (true) {
            val idx = codec.dequeueOutputBuffer(info, if (endOfStream) 10_000L else 0L)
            when {
                idx == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream || SystemClock.elapsedRealtime() > deadline) return
                }
                idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val m = muxer
                    if (m != null && !muxerStarted) {
                        trackIndex = m.addTrack(codec.outputFormat)
                        m.start()
                        muxerStarted = true
                    }
                }
                idx >= 0 -> {
                    val buf = codec.getOutputBuffer(idx)
                    val isConfig = (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                    if (buf != null && info.size > 0 && !isConfig && muxerStarted) {
                        buf.position(info.offset)
                        buf.limit(info.offset + info.size)
                        muxer?.writeSampleData(trackIndex, buf, info)
                        produced = true
                    }
                    codec.releaseOutputBuffer(idx, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return
                }
            }
        }
    }

    // ---- recorders ----

    @SuppressLint("MissingPermission") // RECORD_AUDIO is verified before the service starts
    private fun createMic(): AudioRecord {
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build()
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val record = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.MIC)
            .setAudioFormat(format)
            .setBufferSizeInBytes(max(minBuf * 2, 16384))
            .build()
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            throw AudioCaptureException("Microphone is unavailable")
        }
        return record
    }

    @SuppressLint("MissingPermission") // RECORD_AUDIO is verified before the service starts
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun createInternal(): AudioRecord {
        val config = AudioPlaybackCaptureConfiguration.Builder(projection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
            .build()
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT
        )
        val record = AudioRecord.Builder()
            .setAudioFormat(format)
            .setBufferSizeInBytes(max(minBuf * 2, 32768))
            .setAudioPlaybackCaptureConfig(config)
            .build()
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            throw AudioCaptureException("Internal audio capture is unavailable")
        }
        return record
    }

    private fun release() {
        runCatching { micRecord?.release() }
        runCatching { internalRecord?.release() }
        micRecord = null
        internalRecord = null
        runCatching { encoder?.stop() }
        runCatching { encoder?.release() }
        encoder = null
        runCatching { muxer?.release() }
        muxer = null
    }

    companion object {
        private const val SAMPLE_RATE = 44100
        private const val CHANNELS = 2
        private const val FRAME_SAMPLES = 1024 // one AAC frame
    }
}
