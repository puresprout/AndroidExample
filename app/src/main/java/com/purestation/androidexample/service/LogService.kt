package com.purestation.androidexample.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.purestation.androidexample.ILogService
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class LogService : Service() {
    companion object {
        private const val TAG = "LogService"
        private val MEDIA_TYPE_JSON = "application/json; charset=utf-8".toMediaType()
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val channel = Channel<LogEntry>(Channel.UNLIMITED)

    private val binder = object : ILogService.Stub() {
        override fun sendLog(entry: LogEntry?) {
            entry?.let {
                channel.trySend(it)
            }
        }
    }

    private val client = OkHttpClient.Builder().build()
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    private val adapter = moshi.adapter(LogEntry::class.java)

    override fun onCreate() {
        Log.d(TAG, "onCreate")

        scope.launch {
            for (entry in channel) {
                // INFO 로그를 서버로 업로드하기 전 사전 작업 처리가 매우 길다고 가정하자.
                delay(1000)

                runCatching {
                    uploadToServer(entry)
                }.onFailure { t -> Log.e(TAG, t.message, t) }
            }
        }
    }

    fun uploadToServer(entry: LogEntry) {
        val json = adapter.toJson(entry)

        val request = Request.Builder()
            .url("https://httpbin.org/post")
            .post(json.toRequestBody(MEDIA_TYPE_JSON))
            .build()

        client.newCall(request).execute().use { response ->
            println(response.body!!.string())

            if (!response.isSuccessful) throw IOException("Unexpected code $response")
        }

        Log.i(TAG, "Upload log: $entry")
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")

        channel.close()
        scope.cancel()
    }
}