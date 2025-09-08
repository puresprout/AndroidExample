package com.purestation.androidexample.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "CountService"

class CountService : Service() {
    val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onBind(intent: Intent): IBinder {
        TODO("Return the communication channel to the service.")
    }

    override fun onCreate() {
        Log.d(TAG, "onCreate()")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand(startId=$startId)")

        serviceScope.launch {
            Log.d(TAG, "starting work $startId")

            delay(15_000)

            Log.d(TAG, "completed work $startId")

            stopSelf(startId)
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy()")

        serviceScope.cancel()
    }
}