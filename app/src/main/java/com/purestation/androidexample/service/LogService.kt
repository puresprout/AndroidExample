package com.purestation.androidexample.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.purestation.androidexample.ILogService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LogService : Service() {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val binder = object : ILogService.Stub() {
        override fun basicTypes(
            anInt: Int,
            aLong: Long,
            aBoolean: Boolean,
            aFloat: Float,
            aDouble: Double,
            aString: String?
        ) {
            TODO("Not yet implemented")
        }

        override fun sendLog(entry: LogEntry?) {
            scope.launch { println("sendLog $entry") }
        }
    }

    override fun onBind(intent: Intent): IBinder = binder
}