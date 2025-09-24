package com.purestation.androidexample

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.purestation.androidexample.flow.FlowActivity
import com.purestation.androidexample.gestures.GesturesActivity
import com.purestation.androidexample.service.ServiceActivity
import com.purestation.androidexample.ui.theme.AndroidExampleTheme
import com.purestation.common.ListItem

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AndroidExampleTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ListScreen(modifier = Modifier.padding(innerPadding))

//                    Greeting(
//                        name = "Android",
//                        modifier = Modifier.padding(innerPadding)
//                    )
                }
            }
        }
    }
}

@Composable
fun ListScreen(modifier: Modifier = Modifier) {
    val items = listOf(
        ListItem("Flow 예제", FlowActivity::class.java),
        ListItem("Service 예제", ServiceActivity::class.java),
        ListItem("Gestures 예제", GesturesActivity::class.java)
    )

    val context = LocalContext.current

    LazyColumn(modifier = modifier) {
        itemsIndexed(items) { index, item ->
            Text(text = item.title,
                modifier = Modifier.fillMaxWidth().padding(16.dp).clickable {
                    context.startActivity(Intent(context, item.activity))
                })

            if (index < items.lastIndex) {
                HorizontalDivider()
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ListScreenPreview() {
    AndroidExampleTheme {
        ListScreen()
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