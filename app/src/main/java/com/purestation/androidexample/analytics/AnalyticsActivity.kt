package com.purestation.androidexample.analytics

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.purestation.androidexample.ui.theme.AndroidExampleTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AnalyticsActivity : ComponentActivity() {
    // 화면회전시 ViewModel 매번 새로 생성. (ViewModel에 @HiltViewModel이 없는 생태일때)
//    @Inject lateinit var viewModel: AnalyticsViewModel

    // 화면회전시 ViewModel 유지. (ViewModel에 @HiltViewModel이 있는 생태일때)
    // ViewModel이 의존하는 의존성이 없다면 @HiltViewModel이 없어도 됨
    // 의존하는 의존성이 있는데 @HiltViewModel이 없으면 다음 오류 발생
    // java.lang.RuntimeException: Cannot create an instance of class com.purestation.androidexample.analytics.AnalyticsViewModel
    private val viewModel: AnalyticsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            AndroidExampleTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AnalyticsScreen(modifier = Modifier.padding(innerPadding))

//                    Greeting(
//                        name = "Android",
//                        modifier = Modifier.padding(innerPadding)
//                    )
                }
            }
        }
    }

    @Composable
    fun AnalyticsScreen(modifier: Modifier = Modifier) {
        Button(onClick = {
            println("AnalyticsActivity - $this@AnalyticsActivity")

            viewModel.analytics()
        }, modifier = modifier.padding(16.dp)) {
            Text("AnalyticsService.analyticsMethods()")
        }
    }
}


//@Composable
//fun Greeting(name: String, modifier: Modifier = Modifier) {
//    Text(
//        text = "Hello $name!",
//        modifier = modifier
//    )
//}
//
//@Preview(showBackground = true)
//@Composable
//fun GreetingPreview() {
//    AndroidExampleTheme {
//        Greeting("Android")
//    }
//}