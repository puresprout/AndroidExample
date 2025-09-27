package com.purestation.androidexample.editor

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * RichEditorView를 감싸는 Composable
 * - rememberLauncherForActivityResult 사용
 * - var viewRef by remember 사용
 * - 유튜브 URL 입력용 간단 대화상자 포함
 */
@Composable
fun EditorScreen() {
    var viewRef by remember { mutableStateOf<RichEditorView?>(null) }

    // 이미지 멀티 선택 런처
    val pickImagesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewRef?.addImages(uris)
        }
    }

    // 동영상 단일 선택 런처
    val pickVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewRef?.addVideo(it) }
    }

    var showYoutubeDialog by remember { mutableStateOf(false) }
    var youtubeUrl by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        // RichEditorView를 Compose에서 사용
        RichEditorComposable(
            onRequestPickImages = { pickImagesLauncher.launch("image/*") },
            onRequestPickVideo = { pickVideoLauncher.launch("video/*") },
            onRequestYoutube = { showYoutubeDialog = true },
            onViewReady = { view -> viewRef = view }
        )
    }

    if (showYoutubeDialog) {
        AlertDialog(
            onDismissRequest = { showYoutubeDialog = false },
            title = { Text("유튜브 링크 추가") },
            text = {
                OutlinedTextField(
                    value = youtubeUrl,
                    onValueChange = { youtubeUrl = it },
                    placeholder = { Text("https://www.youtube.com/watch?v=...") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (youtubeUrl.isNotBlank()) viewRef?.addYoutube(youtubeUrl)
                        youtubeUrl = ""
                        showYoutubeDialog = false
                    }
                ) { Text("추가") }
            },
            dismissButton = {
                TextButton(onClick = {
                    youtubeUrl = ""
                    showYoutubeDialog = false
                }) { Text("취소") }
            }
        )
    }
}

/**
 * AndroidView로 RichEditorView를 래핑
 */
@Composable
fun RichEditorComposable(
    onRequestPickImages: () -> Unit,
    onRequestPickVideo: () -> Unit,
    onRequestYoutube: () -> Unit,
    onViewReady: (RichEditorView) -> Unit
) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            RichEditorView(context).apply {
                // Compose 쪽 런처/다이얼로그를 호출하기 위해 콜백을 연결
                setExternalPickers(
                    onPickImages = onRequestPickImages,
                    onPickVideo = onRequestPickVideo,
                    onPickYoutube = onRequestYoutube
                )
                onViewReady(this)
            }
        }
    )
}
