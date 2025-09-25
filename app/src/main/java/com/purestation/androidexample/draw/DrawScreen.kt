package com.purestation.androidexample.draw

import android.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/** 버튼/색상/두께 UI + DrawReplayComposable 사용 */
@Composable
fun DrawScreen(modifier: Modifier = Modifier) {
    var strokeWidth by remember { mutableStateOf(6f) }
    var strokeColor by remember { mutableStateOf(Color.BLACK) }
    var viewRef by remember { mutableStateOf<DrawReplayView?>(null) }

    Column(modifier = modifier.fillMaxSize()) {

        // 상단 컨트롤
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = { viewRef?.startReplay() }) { Text("재생") }
            Button(onClick = { viewRef?.stopReplay() }) { Text("정지") }
            OutlinedButton(onClick = { viewRef?.clearAll() }) { Text("모두 지우기") }
        }

        // 선 두께
        Column(Modifier.padding(horizontal = 12.dp)) {
            Text("선 두께: ${"%.1f".format(strokeWidth)} px")
            Slider(
                value = strokeWidth,
                onValueChange = {
                    strokeWidth = it
                    viewRef?.setStrokeWidth(it)
                },
                valueRange = 1f..30f
            )
        }

        // 색상 프리셋
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                Color.BLACK to "검정",
                Color.RED to "빨강",
                Color.BLUE to "파랑",
                Color.GREEN to "초록",
                Color.MAGENTA to "자홍"
            ).forEach { (c, label) ->
                OutlinedButton(onClick = {
                    strokeColor = c
                    viewRef?.setStrokeColor(c)
                }) {
                    Text(label)
                }
            }
        }

        // 그리기 영역
        DrawReplayComposable(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            strokeWidth = strokeWidth,
            strokeColor = strokeColor,
            onReady = { viewRef = it }
        )
    }
}

/** AndroidView만 캡슐화: 외부에서 색/두께를 상태로 넘기고, 뷰 레퍼런스를 콜백으로 받음 */
@Composable
fun DrawReplayComposable(
    modifier: Modifier = Modifier,
    strokeWidth: Float,
    strokeColor: Int,
    onReady: (DrawReplayView) -> Unit = {}
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            DrawReplayView(ctx).apply {
                setStrokeWidth(strokeWidth)
                setStrokeColor(strokeColor)
                onReady(this)
            }
        },
        update = { v ->
            v.setStrokeWidth(strokeWidth)
            v.setStrokeColor(strokeColor)
        }
    )
}
