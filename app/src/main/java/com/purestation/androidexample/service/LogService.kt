package com.purestation.androidexample.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.purestation.androidexample.ILogService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LogService : Service() {
    companion object {
        private const val TAG = "LogService"
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

    override fun onCreate() {
        Log.d(TAG, "onCreate")

        scope.launch {
            for (entry in channel) {
                // INFO 로그를 서버로 업로드하기 전 사전 작업 처리가 매우 길다고 가정하자.
                delay(3000)

                runCatching { Log.i(TAG, "Upload log: $entry") }
                    .onFailure { t -> Log.e(TAG, t.message, t) }
            }
        }
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")

        channel.close()
        scope.cancel()
    }
}