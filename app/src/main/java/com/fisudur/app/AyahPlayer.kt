package com.fisudur.app

import android.media.AudioAttributes
import android.media.MediaPlayer

/** Plays a page's ayat one after another, streamed; tells which ayah is playing and when all are done. */
class AyahPlayer {
    private var player: MediaPlayer? = null
    private var urls: List<String> = emptyList()
    private var index = 0
    private var onIndex: (Int) -> Unit = {}
    private var onFinished: () -> Unit = {}
    private var onError: () -> Unit = {}

    val isPlaying: Boolean get() = player != null

    fun play(urls: List<String>, onIndex: (Int) -> Unit, onFinished: () -> Unit, onError: () -> Unit) {
        stop()
        this.urls = urls
        this.onIndex = onIndex
        this.onFinished = onFinished
        this.onError = onError
        index = 0
        next()
    }

    private fun next() {
        if (index >= urls.size) {
            stop()
            onFinished()
            return
        }
        onIndex(index)
        val mp = MediaPlayer()
        player = mp
        runCatching {
            mp.setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build(),
            )
            mp.setDataSource(urls[index])
            mp.setOnPreparedListener { it.start() }
            mp.setOnCompletionListener {
                it.release()
                if (player === it) {
                    player = null
                    index++
                    next()
                }
            }
            mp.setOnErrorListener { p, _, _ ->
                p.release()
                if (player === p) player = null
                onError()
                true
            }
            mp.prepareAsync()
        }.onFailure {
            mp.release()
            player = null
            onError()
        }
    }

    fun stop() {
        player?.let { p ->
            player = null
            runCatching { p.stop() }
            p.release()
        }
    }
}
