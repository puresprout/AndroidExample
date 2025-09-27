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

@Composable
fun EditorScreen() {
    // RichEditorView 참조를 저장하는 상태값
    var viewRef by remember { mutableStateOf<RichEditorView?>(null) }

    // ---------------- 이미지 선택 런처 ----------------
    // ActivityResultContracts.GetMultipleContents() → 여러 장 이미지 선택 가능
    val pickImagesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewRef?.addImages(uris)   // 선택된 이미지들을 RichEditorView에 추가
        }
    }

    // ---------------- 동영상 선택 런처 ----------------
    // ActivityResultContracts.GetContent() → 단일 파일 선택
    val pickVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewRef?.addVideo(it) } // 선택된 동영상 추가
    }

    // ---------------- 유튜브 다이얼로그 상태 ----------------
    var showYoutubeDialog by remember { mutableStateOf(false) }
    var youtubeUrl by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        // RichEditorView를 Compose에서 직접 사용 가능하도록 래핑
        RichEditorComposable(
            onRequestPickImages = { pickImagesLauncher.launch("image/*") }, // 이미지 파일 선택
            onRequestPickVideo = { pickVideoLauncher.launch("video/*") },   // 동영상 파일 선택
            onRequestYoutube = { showYoutubeDialog = true },                // 유튜브 다이얼로그 열기
            onViewReady = { view -> viewRef = view }                        // 뷰 초기화 시점에 참조 저장
        )
    }

    // ---------------- 유튜브 URL 입력 다이얼로그 ----------------
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
                        // 입력한 URL이 비어있지 않으면 RichEditorView에 추가
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
 * RichEditorView를 AndroidView로 감싸서 Compose 환경에서 사용 가능하게 만드는 Composable
 * - Compose 쪽에서 파일 선택 런처나 다이얼로그를 호출하기 위해 콜백 연결
 */
@Composable
fun RichEditorComposable(
    onRequestPickImages: () -> Unit,   // 이미지 선택 요청 콜백
    onRequestPickVideo: () -> Unit,   // 동영상 선택 요청 콜백
    onRequestYoutube: () -> Unit,     // 유튜브 다이얼로그 요청 콜백
    onViewReady: (RichEditorView) -> Unit // RichEditorView 생성 완료 시점 콜백
) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            // RichEditorView 생성
            RichEditorView(context).apply {
                // 외부 선택기(이미지, 동영상, 유튜브) 호출할 수 있도록 연결
                setExternalPickers(
                    onPickImages = onRequestPickImages,
                    onPickVideo = onRequestPickVideo,
                    onPickYoutube = onRequestYoutube
                )
                // Compose 쪽에서 RichEditorView 참조할 수 있도록 전달
                onViewReady(this)
            }
        }
    )
}
