package com.purestation.androidexample.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.purestation.androidexample.ILogService
import com.purestation.androidexample.ui.theme.AndroidExampleTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "ServiceActivity"

class ServiceActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AndroidExampleTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ServiceScreen(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

fun startCountService(ctx: Context) {
    ctx.startService(Intent(ctx, CountService::class.java))
}

@Composable
fun StartedServicePanel(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(modifier = modifier) {
        Button(
            onClick = { startCountService(ctx) }) {
            Text(
                text = "CountService 즉시 실행",
            )
        }

        Button(
            onClick = {
                Toast.makeText(
                    ctx, "5초 뒤 서비스 실행을 시도합니다. 홈 버튼으로 앱을 백그라운드로 보내세요.", Toast.LENGTH_SHORT
                ).show()

                scope.launch {
                    delay(5000)

                    startCountService(ctx)
                }
            }) {
            Text(
                text = "CountService 5초 뒤 실행",
            )
        }
    }
}

@Composable
fun BoundServicePanel(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current

    var logService by remember { mutableStateOf<ILogService?>(null) }
    var isBound by remember { mutableStateOf(false) }

    val conn = remember {
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                service: IBinder?
            ) {
                Log.d(TAG, "onServiceConnected")

                logService = ILogService.Stub.asInterface(service)
                isBound = true
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                Log.d(TAG, "onServiceDisconnected")

                logService = null
                isBound = false
            }
        }
    }

    fun bind() {
        ctx.bindService(Intent(ctx, LogService::class.java),
            conn, Context.BIND_AUTO_CREATE)
    }

    fun unbind() {
        runCatching { ctx.unbindService(conn) }
            .onFailure(::println)

        // INFO unbind를 했을때 Service.onDestory()는 호출되지만, ServiceConnection.onServiceDisconnected() 콜백이 호출되지 않는다.
        // 따라서 콜백에 의존하지 않고 다음처럼 초기화
        logService = null
        isBound = false
    }

    DisposableEffect(Unit) {
        Log.d(TAG, "DisposableEffect")

        if (!isBound) {
            bind()
        }

        onDispose {
            Log.d(TAG, "onDispose")

            if (isBound) {
                unbind()
            }
        }
    }

    Column(modifier = modifier) {
        Text("LogService connected: $isBound")

        Button(onClick = {
            Log.d(TAG, "onClick")

            logService?.sendLog(LogEntry("INFO", "log upload button clicked"))
        }) {
            Text("AIDL LogService로 로그 전송")
        }

        Button(onClick = {
            if (!isBound) {
                bind()
            }
        }) {
            Text("LogService 연결")
        }

        Button(onClick = {
            if (isBound) {
                unbind()
            }
        }) {
            Text("LogService 연결 해제")
        }
    }
}

@Composable
fun ServiceScreen(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        StartedServicePanel(Modifier.padding(all = 16.dp))
        HorizontalDivider()
        BoundServicePanel(Modifier.padding(all = 16.dp))
    }
}

@Preview(showBackground = true)
@Composable
fun ServiceScreenPreview() {
    AndroidExampleTheme {
        ServiceScreen()
    }
}