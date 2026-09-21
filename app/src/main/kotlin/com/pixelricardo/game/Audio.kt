package com.pixelricardo.game

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

// Áudio 100% sintetizado: trilha chiptune em loop + efeitos sonoros gerados em PCM,
// sem nenhum asset de mídia no projeto (mesma filosofia do resto do jogo).

private const val SR = 22050

object Sounds {
    const val CATCH = 0
    const val MISS = 1
    const val NEAR = 2
    const val COLLECT = 3
    const val JACKPOT = 4
    const val EVOLVE = 5
    const val QUEST = 6
    const val GIFT = 7
    const val RARE = 8
    const val BUY = 9
    const val EVOLVE_READY = 10
}

object SfxBank {
    @Volatile
    var muted = false

    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
    private val fmt = AudioFormat.Builder()
        .setSampleRate(SR)
        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
        .build()

    private val clips = HashMap<Int, ByteArray>()
    private val tracks = HashMap<Int, AudioTrack>()
    private val comboClips = HashMap<Int, ByteArray>()
    private val comboTracks = HashMap<Int, AudioTrack>()

    private fun buffer(sec: Double): ByteArray = ByteArray((sec * SR * 2).toInt())

    // somador com clamp para evitar clipping
    private fun writeSquare(buf: ByteArray, at: Double, dur: Double, freq: Double, amp: Double) {
        val start = (at * SR).toInt() * 2
        val n = (dur * SR).toInt().coerceAtLeast(1)
        var phase = 0.0
        for (i in 0 until n) {
            val idx = start + i * 2
            if (idx < 0 || idx + 1 >= buf.size) return
            phase += freq / SR
            if (phase >= 1.0) phase -= 1.0
            val s = if (phase < 0.5) amp else -amp
            val cur = ((buf[idx].toInt() and 0xFF) or (buf[idx + 1].toInt() shl 8)).toShort().toInt()
            var v = cur + s * 32767.0
            if (v > 32767.0) v = 32767.0
            if (v < -32768.0) v = -32768.0
            val i16 = v.toInt()
            buf[idx] = (i16 and 0xFF).toByte()
            buf[idx + 1] = ((i16 shr 8) and 0xFF).toByte()
        }
    }

    private fun staticTrack(data: ByteArray): AudioTrack =
        AudioTrack.Builder()
            .setAudioAttributes(attrs)
            .setAudioFormat(fmt)
            .setBufferSizeInBytes(data.size)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

    private fun note(b: ByteArray, at: Double, f: Double, vol: Double = 0.3) {
        writeSquare(b, at, 0.1, f, vol)
    }

    private fun build(id: Int): ByteArray {
        val b = buffer(1.2)
        when (id) {
            Sounds.CATCH -> {
                note(b, 0.0, 659.25); note(b, 0.09, 783.99); writeSquare(b, 0.18, 0.18, 1046.50, 0.32)
            }
            Sounds.MISS -> {
                writeSquare(b, 0.0, 0.18, 174.61, 0.25); writeSquare(b, 0.2, 0.3, 110.0, 0.22)
            }
            Sounds.NEAR -> {
                writeSquare(b, 0.0, 0.1, 160.0, 0.3); writeSquare(b, 0.12, 0.1, 160.0, 0.3)
            }
            Sounds.COLLECT -> {
                writeSquare(b, 0.0, 0.07, 880.0, 0.3); writeSquare(b, 0.07, 0.1, 1318.51, 0.32)
            }
            Sounds.JACKPOT -> {
                note(b, 0.0, 523.25); note(b, 0.12, 659.25); note(b, 0.24, 783.99)
                writeSquare(b, 0.36, 0.26, 1046.50, 0.34); writeSquare(b, 0.36, 0.26, 659.25, 0.18); writeSquare(b, 0.36, 0.26, 783.99, 0.18)
            }
            Sounds.EVOLVE -> {
                note(b, 0.0, 523.25); note(b, 0.1, 659.25); note(b, 0.2, 783.99)
                note(b, 0.3, 1046.50); writeSquare(b, 0.4, 0.3, 2093.0, 0.34)
            }
            Sounds.QUEST -> {
                note(b, 0.0, 523.25); note(b, 0.1, 659.25); note(b, 0.2, 783.99)
                note(b, 0.3, 1046.50); note(b, 0.4, 1567.98); writeSquare(b, 0.5, 0.32, 2093.0, 0.34)
            }
            Sounds.GIFT -> {
                writeSquare(b, 0.0, 0.12, 587.33, 0.3); writeSquare(b, 0.14, 0.18, 1174.66, 0.34); writeSquare(b, 0.34, 0.3, 587.33, 0.2)
            }
            Sounds.RARE -> {
                writeSquare(b, 0.0, 0.06, 880.0, 0.3); writeSquare(b, 0.07, 0.06, 1046.50, 0.3)
                writeSquare(b, 0.14, 0.06, 1318.51, 0.3); writeSquare(b, 0.21, 0.18, 1760.0, 0.34)
            }
            Sounds.BUY -> {
                note(b, 0.0, 523.25); note(b, 0.09, 659.25); writeSquare(b, 0.18, 0.2, 783.99, 0.32)
            }
            Sounds.EVOLVE_READY -> {
                writeSquare(b, 0.0, 0.08, 392.0, 0.3); writeSquare(b, 0.09, 0.16, 523.25, 0.32)
            }
        }
        return b
    }

    private fun playTrack(t: AudioTrack, vol: Float = 0.9f) {
        t.stop()
        t.reloadStaticData()
        t.setVolume(vol)
        t.play()
    }

    fun play(id: Int, vol: Float = 0.9f) {
        if (muted) return
        try {
            val data = clips.getOrPut(id) { build(id) }
            val t = tracks.getOrPut(id) { staticTrack(data).apply { write(data, 0, data.size, AudioTrack.WRITE_BLOCKING) } }
            playTrack(t, vol)
        } catch (e: Exception) {
        }
    }

    fun combo(level: Int) {
        if (muted) return
        try {
            val data = comboClips.getOrPut(level) {
                val b = buffer(0.6)
                val base = when {
                    level >= 5 -> 1046.50
                    level == 4 -> 880.0
                    level == 3 -> 783.99
                    else -> 659.25
                }
                writeSquare(b, 0.0, 0.08, base, 0.3)
                writeSquare(b, 0.09, 0.08, base * 1.25, 0.3)
                writeSquare(b, 0.18, 0.18, base * 1.5, 0.34)
                b
            }
            val t = comboTracks.getOrPut(level) { staticTrack(data).apply { write(data, 0, data.size, AudioTrack.WRITE_BLOCKING) } }
            playTrack(t, 0.9f)
        } catch (e: Exception) {
        }
    }

    fun release() {
        val rel = { map: Map<Int, AudioTrack> ->
            for ((_, t) in map) t.release()
        }
        rel(tracks)
        rel(comboTracks)
        tracks.clear()
        comboTracks.clear()
    }
}

class MusicBox {
    @Volatile
    private var running = false
    private var thread: Thread? = null
    private val musicData: ByteArray by lazy { buildLoop() }

    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
    private val fmt = AudioFormat.Builder()
        .setSampleRate(SR)
        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
        .build()

    fun start() {
        if (running) return
        running = true
        thread = Thread { runLoop() }.apply {
            name = "pixelricardo-music"
            start()
        }
    }

    fun stop() {
        running = false
        thread?.join(400)
        thread = null
    }

    private fun buffer(sec: Double): ByteArray = ByteArray((sec * SR * 2).toInt())

    private fun writeSquare(buf: ByteArray, at: Double, dur: Double, freq: Double, amp: Double) {
        val start = (at * SR).toInt() * 2
        val n = (dur * SR).toInt().coerceAtLeast(1)
        var phase = 0.0
        for (i in 0 until n) {
            val idx = start + i * 2
            if (idx < 0 || idx + 1 >= buf.size) return
            phase += freq / SR
            if (phase >= 1.0) phase -= 1.0
            val s = if (phase < 0.5) amp else -amp
            val cur = ((buf[idx].toInt() and 0xFF) or (buf[idx + 1].toInt() shl 8)).toShort().toInt()
            var v = cur + s * 32767.0
            if (v > 32767.0) v = 32767.0
            if (v < -32768.0) v = -32768.0
            val i16 = v.toInt()
            buf[idx] = (i16 and 0xFF).toByte()
            buf[idx + 1] = ((i16 shr 8) and 0xFF).toByte()
        }
    }

    private fun buildLoop(): ByteArray {
        val beat = 0.35
        val total = 32 * beat
        val buf = buffer(total)
        // progressão C - G - A - F, alegre e saltitante
        val mel = doubleArrayOf(
            659.25, 783.99, 880.0, 783.99, 659.25, 783.99, 880.0, 783.99,
            523.25, 587.33, 659.25, 783.99, 659.25, 587.33, 523.25, 0.0,
            659.25, 783.99, 880.0, 783.99, 1046.50, 880.0, 783.99, 659.25,
            587.33, 659.25, 587.33, 523.25, 392.0, 440.0, 493.88, 523.25,
        )
        val bass = doubleArrayOf(130.81, 196.0, 220.0, 174.61)
        for (i in mel.indices) {
            val t = i * beat
            val m = mel[i]
            if (m > 0.0) {
                writeSquare(buf, t, beat * 0.9, m, 0.16)
                writeSquare(buf, t, beat * 0.9, m / 2.0, 0.1)
            }
            if (i % 2 == 0) {
                writeSquare(buf, t, beat * 0.9, bass[i / 8 % 4], 0.22)
            }
            writeSquare(buf, t + beat * 0.5, beat * 0.12, 6200.0, 0.04)
        }
        return buf
    }

    private fun runLoop() {
        try {
            val buf = musicData
            val t = AudioTrack.Builder()
                .setAudioAttributes(attrs)
                .setAudioFormat(fmt)
                .setBufferSizeInBytes(buf.size)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            t.play()
            while (running) {
                if (t.write(buf, 0, buf.size, AudioTrack.WRITE_BLOCKING) <= 0) break
            }
            t.release()
        } catch (e: Exception) {
            running = false
        }
    }
}