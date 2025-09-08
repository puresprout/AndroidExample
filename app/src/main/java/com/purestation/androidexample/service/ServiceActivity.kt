package com.purestation.androidexample.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.purestation.androidexample.ui.theme.AndroidExampleTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
fun ServiceScreen(modifier: Modifier = Modifier) {
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

@Preview(showBackground = true)
@Composable
fun ServiceScreenPreview() {
    AndroidExampleTheme {
        ServiceScreen()
    }
}