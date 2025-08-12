package com.purestation.androidexample.flow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.purestation.androidexample.ui.theme.AndroidExampleTheme

class FlowActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AndroidExampleTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    FlowScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun FlowScreen(modifier: Modifier = Modifier) {
    // 재구성시 viewModel 살아남게
//    val viewModel = remember { FlowViewModel() }

    // 화면회전시에도 살아남게
    val viewModel: FlowViewModel = viewModel()

    val count by viewModel.count.collectAsState()

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Button(onClick = { viewModel.startCounting() }) {
            Text("Start")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Count = $count")
    }
}

@Preview(showBackground = true)
@Composable
fun FlowScreenPreview() {
    AndroidExampleTheme {
        FlowScreen()
    }
}