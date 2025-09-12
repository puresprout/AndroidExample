package com.purestation.androidexample.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.purestation.androidexample.ILogService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * LogService 바인딩/해제 + 로그 전송을 캡슐화한 SDK.
 *
 * 사용법:
 * val client = LogServiceClient(context)
 * client.bind()
 * client.sendLog(LogEntry(...))
 * client.unbind()
 *
 * Compose에서 remember와 DisposableEffect로 생명주기에 맞춰 bind/unbind 가능.
 */
class LogServiceClient(context: Context) {
    companion object {
        private const val TAG = "LogServiceClient"
    }

    private val ctx = context.applicationContext

    private var logService: ILogService? = null
    private val _isBound = MutableStateFlow(false)
    val isBound = _isBound.asStateFlow()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(
            name: ComponentName?,
            service: IBinder?
        ) {
            Log.d(TAG, "onServiceConnected")

            logService = ILogService.Stub.asInterface(service)
            _isBound.value = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "onServiceDisconnected")

            logService = null
            _isBound.value = false
        }
    }

    /** 서비스 바인딩 */
    fun bind() {
        if (_isBound.value) return

        val r = ctx.bindService(
            Intent(ctx, LogService::class.java),
            connection, Context.BIND_AUTO_CREATE
        )

        if (!r) {
            Log.e(TAG, "bindService failed")
        }
    }

    /**
     * 서비스 언바인드
     *
     * 참고: unbind 시 Service.onDestroy()는 호출될 수 있으나
     * ServiceConnection.onServiceDisconnected()는 보장되지 않으므로,
     * 여기서 직접 상태 초기화.
     */
    fun unbind() {
        if (!_isBound.value) return

        runCatching { ctx.unbindService(connection) }
            .onFailure { Log.e(TAG, it.toString(), it) }

        logService = null
        _isBound.value = false
    }

    /** 로그 전송. 성공/실패 여부 반환 */
    fun sendLog(entry: LogEntry): Boolean {
        val service = logService ?: return false

        return runCatching {
            service.sendLog(entry)
            true
        }.onFailure {
            Log.e(TAG, it.toString(), it)
        }.getOrDefault(false)
    }

    /** 명시적 정리(옵션): 화면/스코프 종료 시 호출 */
    fun dispose() {
        if (_isBound.value) {
            unbind()
        }
    }
}