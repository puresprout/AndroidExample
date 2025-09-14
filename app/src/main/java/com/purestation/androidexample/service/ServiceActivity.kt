package com.purestation.androidexample.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.purestation.androidexample.ILogResultCallback
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

/**
 * 화면 진입 시 bind, 화면 종료 시 unbind를 자동 수행하는 헬퍼.
 */
@Composable
fun rememberLogServiceClient(): LogServiceClient {
    val ctx = LocalContext.current
    val client = remember { LogServiceClient(ctx) }

    DisposableEffect(Unit) {
        Log.d(TAG, "DisposableEffect")

        client.bind()

        onDispose {
            Log.d(TAG, "onDispose")

            client.dispose()
        }
    }

    return client
}

@Composable
fun BoundServicePanel(modifier: Modifier = Modifier) {
    val client = rememberLogServiceClient()
    val isBound by client.isBound.collectAsState()

    Column(modifier = modifier) {
        Text("LogService connected: $isBound")

        Button(onClick = {
            Log.d(TAG, "onClick")

            client.sendLog(LogEntry("INFO", "log upload button clicked"))
        }) {
            Text("AIDL LogService로 로그 전송")
        }

        Button(onClick = {
            Log.d(TAG, "onClick with callback")

            client.sendLogWithCallback(LogEntry("INFO", "log upload button with callback clicked"), object : ILogResultCallback.Stub() {
                override fun onError(errorMessage: String?) {
                    Log.d(TAG, "onError - $errorMessage")
                }
                override fun onResult(ok: Boolean) {
                    Log.d(TAG, "onResult - $ok")
                }
            })
        }) {
            Text("AIDL LogService로 로그 전송 with callback")
        }

        Button(onClick = { client.bind() }) {
            Text("LogService 연결")
        }

        Button(onClick = { client.unbind() }) {
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