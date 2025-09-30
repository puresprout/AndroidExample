package com.purestation.androidexample.analytics

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import javax.inject.Inject

@AndroidEntryPoint
class AnalyticsActivity : ComponentActivity() {
    @Inject lateinit var analytics: AnalyticsService

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
            analytics.analyticsMethods()
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