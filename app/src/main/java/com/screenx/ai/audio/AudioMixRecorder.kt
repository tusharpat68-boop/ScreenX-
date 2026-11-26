package com.screenx.ai.audio

import android.annotation.SuppressLint
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
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioCaptureException(message: String) : Exception(message)

/**
 * Captures internal (playback) audio and/or the microphone, mixes them to stereo PCM,
 * encodes AAC and writes an audio-only MP4 file. The video engine later merges this file
 * with the screen video.
 *
 * Internal audio only contains apps that allow capture. Calls, protected and some streaming
 * apps stay silent; that is an Android rule, not something this app can bypass.
 */
@RequiresApi(Build.VERSION_CODES.Q)
class AudioMixRecorder(
    private val projection: MediaProjection,
    private val useInternal: Boolean,
    private val useMic: Boolean,
    private val bitrate: Int,
    private val outputFile: File
) {
    @Volatile private var running = false
    @Volatile private var paused = false
    @Volatile private var aborted = false
    @Volatile private var failed = false

    /** SystemClock.elapsedRealtimeNanos() when capture started, used for A/V sync. */
    @Volatile var firstSampleElapsedNanos: Long = 0L
        private set

    private var internalRecord: AudioRecord? = null
    private var micRecord: AudioRecord? = null
    private var encoder: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var muxerStarted = false
    private var trackIndex = -1
    @Volatile private var sampleCount = 0
    private var eosSeen = false
    private var thread: Thread? = null
    private val info = MediaCodec.BufferInfo()

    /** Creates the capture objects and the encoder without starting capture. */
    @Throws(AudioCaptureException::class)
    fun prepare() {
        try {
            if (useInternal) internalRecord = buildInternal()
            if (useMic) micRecord = buildMic()
            encoder = createEncoder()
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        } catch (e: Exception) {
            releaseAll()
            throw AudioCaptureException(e.message ?: "Audio setup failed")
        }
    }

    /** Starts capture. Call right after the video recorder starts. */
    @Throws(AudioCaptureException::class)
    fun start() {
        val ir = internalRecord
        val mr = micRecord
        try {
            ir?.startRecording()
            mr?.startRecording()
        } catch (e: IllegalStateException) {
            abort()
            throw AudioCaptureException(e.message ?: "Audio capture could not start")
        }
        val ok = (ir == null || ir.recordingState == AudioRecord.RECORDSTATE_RECORDING) &&
            (mr == null || mr.recordingState == AudioRecord.RECORDSTATE_RECORDING)
        if (!ok) {
            abort()
            throw AudioCaptureException("Audio capture is not available")
        }
        firstSampleElapsedNanos = SystemClock.elapsedRealtimeNanos()
        running = true
        thread = Thread({ loop() }, "ScreenX-Audio").also { it.start() }
    }

    fun pause() {
        paused = true
    }

    fun resume() {
        paused = false
    }

    /** Asks the capture thread to finish. Returns immediately. */
    fun requestStop() {
        running = false
    }

    /** Waits for the thread to flush the encoder. True if a usable audio file was produced. */
    fun awaitFinish(): Boolean {
        thread?.join(5000)
        return !failed && !aborted && sampleCount > 0
    }

    /** Stops everything and deletes the audio file. */
    fun abort() {
        aborted = true
        running = false
        thread?.join(2000)
        releaseAll()
        outputFile.delete()
    }

    // ---------------------------------------------------------------- capture loop

    private fun loop() {
        val internalBuf = ShortArray(CHUNK_FRAMES * 2)
        val micBuf = ShortArray(CHUNK_FRAMES)
        val pcm = ByteBuffer.allocate(CHUNK_FRAMES * 4).order(ByteOrder.LITTLE_ENDIAN)
        var framesWritten = 0L
        var capturing = true
        try {
            while (running) {
                if (paused) {
                    // Stop capture while paused so no stale audio is buffered.
                    if (capturing) {
                        internalRecord?.let { runCatching { it.stop() } }
                        micRecord?.let { runCatching { it.stop() } }
                        capturing = false
                    }
                    Thread.sleep(20)
                    continue
                }
                if (!capturing) {
                    internalRecord?.startRecording()
                    micRecord?.startRecording()
                    capturing = true
                }

                val ir = internalRecord
                val mr = micRecord
                if (ir != null && !readFull(ir, internalBuf, CHUNK_FRAMES * 2)) { failed = true; break }
                if (mr != null && !readFull(mr, micBuf, CHUNK_FRAMES)) { failed = true; break }
                if (paused || !running) continue

                pcm.clear()
                for (i in 0 until CHUNK_FRAMES) {
                    var l = 0
                    var r = 0
                    if (ir != null) {
                        l += internalBuf[2 * i].toInt()
                        r += internalBuf[2 * i + 1].toInt()
                    }
                    if (mr != null) {
                        val m = micBuf[i].toInt()
                        l += m
                        r += m
                    }
                    pcm.putShort(l.coerceIn(-32768, 32767).toShort())
                    pcm.putShort(r.coerceIn(-32768, 32767).toShort())
                }
                val ptsUs = framesWritten * 1_000_000L / SAMPLE_RATE
                feed(pcm.array(), CHUNK_FRAMES * 4, ptsUs, false)
                framesWritten += CHUNK_FRAMES
                drain(0)
            }

            if (!aborted && !failed) {
                val ptsUs = framesWritten * 1_000_000L / SAMPLE_RATE
                feed(ByteArray(0), 0, ptsUs, true)
                var tries = 0
                while (!eosSeen && tries < 200) {
                    drain(10_000)
                    tries++
                }
            }
        } catch (t: Throwable) {
            failed = true
        } finally {
            releaseAll()
        }
    }

    /** Blocking read of exactly [size] samples. False on a read error. */
    private fun readFull(rec: AudioRecord, buf: ShortArray, size: Int): Boolean {
        var off = 0
        while (off < size && running && !paused) {
            val n = rec.read(buf, off, size - off)
            if (n < 0) return false
            if (n == 0) Thread.sleep(1)
            off += n
        }
        return true
    }

    // ---------------------------------------------------------------- encoder

    private fun feed(data: ByteArray, len: Int, ptsUs: Long, eos: Boolean) {
        val enc = encoder ?: return
        var index = -1
        var attempts = 0
        while (index < 0) {
            index = enc.dequeueInputBuffer(10_000)
            if (index < 0) {
                drain(0)
                if (++attempts > 100) throw IllegalStateException("Audio encoder stalled")
            }
        }
        val buffer = enc.getInputBuffer(index) ?: throw IllegalStateException("No input buffer")
        buffer.clear()
        if (len > 0) buffer.put(data, 0, len)
        enc.queueInputBuffer(
            index, 0, len, ptsUs,
            if (eos) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
        )
    }

    private fun drain(timeoutUs: Long) {
        val enc = encoder ?: return
        val mux = muxer ?: return
        while (true) {
            val index = enc.dequeueOutputBuffer(info, timeoutUs)
            if (index == MediaCodec.INFO_TRY_AGAIN_LATER) return
            if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                trackIndex = mux.addTrack(enc.outputFormat)
                mux.start()
                muxerStarted = true
            } else if (index >= 0) {
                val out = enc.getOutputBuffer(index)
                val isConfig = (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                if (out != null && info.size > 0 && !isConfig && muxerStarted) {
                    out.position(info.offset)
                    out.limit(info.offset + info.size)
                    mux.writeSampleData(trackIndex, out, info)
                    sampleCount++
                }
                enc.releaseOutputBuffer(index, false)
                if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    eosSeen = true
                    return
                }
            }
        }
    }

    private fun createEncoder(): MediaCodec {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, 2).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }
        val enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        enc.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        enc.start()
        return enc
    }

    // ---------------------------------------------------------------- audio sources

    private fun buildInternal(): AudioRecord {
        val capture = AudioPlaybackCaptureConfiguration.Builder(projection)
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
        if (minBuf <= 0) throw AudioCaptureException("Internal audio is not supported")
        val rec = AudioRecord.Builder()
            .setAudioFormat(format)
            .setBufferSizeInBytes(maxOf(minBuf, CHUNK_FRAMES * 4 * 4))
            .setAudioPlaybackCaptureConfig(capture)
            .build()
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            throw AudioCaptureException("Internal audio did not initialize")
        }
        return rec
    }

    @SuppressLint("MissingPermission") // RECORD_AUDIO is checked by the caller before this runs
    private fun buildMic(): AudioRecord {
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) throw AudioCaptureException("Microphone is not supported")
        val rec = AudioRecord(
            MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuf, CHUNK_FRAMES * 2 * 4)
        )
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            throw AudioCaptureException("Microphone did not initialize")
        }
        return rec
    }

    @Synchronized
    private fun releaseAll() {
        internalRecord?.let {
            runCatching { it.stop() }
            runCatching { it.release() }
        }
        internalRecord = null
        micRecord?.let {
            runCatching { it.stop() }
            runCatching { it.release() }
        }
        micRecord = null
        encoder?.let {
            runCatching { it.stop() }
            runCatching { it.release() }
        }
        encoder = null
        muxer?.let {
            if (muxerStarted && sampleCount > 0) runCatching { it.stop() }
            runCatching { it.release() }
        }
        muxer = null
    }

    companion object {
        const val SAMPLE_RATE = 44100
        private const val CHUNK_FRAMES = 1024
    }
}
