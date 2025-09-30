package com.purestation.androidexample.analytics

import android.content.Intent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun AnalyticsWithDIScreen() {
    val ctx = LocalContext.current

    Text("자동으로 액티비티를 띄웁니다.", modifier = Modifier.padding(16.dp))

    LaunchedEffect(Unit) {
        ctx.startActivity(Intent(ctx, AnalyticsActivity::class.java))
    }
}

