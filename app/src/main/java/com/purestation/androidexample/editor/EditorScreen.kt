package com.purestation.androidexample.editor

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.purestation.androidexample.ui.theme.AndroidExampleTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun EditorScreen(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current // Compose 환경에서 현재 Context 획득 (갤러리/카메라 호출에 필요)
    var editor by remember { mutableStateOf<RichEditorView?>(null) } // 실제 RichEditorView를 Compose와 연결
    var lastPhotoUri by remember { mutableStateOf<Uri?>(null) } // 마지막 촬영한 사진의 임시 Uri 저장
    var importText by remember { mutableStateOf("") } // 유저 입력: 유튜브 링크나 HTML
    var exportText by remember { mutableStateOf("") } // 에디터 내용을 HTML로 직렬화한 결과

    // 여러 장의 이미지를 갤러리에서 선택하는 런처
    val pickImages = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris != null && uris.isNotEmpty()) editor?.addImages(uris)
    }

    // 단일 동영상 선택 런처
    val pickVideo = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { editor?.addVideo(it) }
    }

    // 카메라로 사진을 찍고 저장하는 런처
    val takePicture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok ->
        // 촬영 성공 시, 미리 만들어둔 Uri(lastPhotoUri)에 저장된 사진을 에디터에 추가
        if (ok) lastPhotoUri?.let { editor?.addImages(listOf(it)) }
    }

    // 카메라 권한 요청 런처
    val requestCamera = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            // 권한이 허용되면 새로운 Uri 생성 → takePicture 런처 실행
            val uri = createImageUri(ctx)
            lastPhotoUri = uri
            takePicture.launch(uri)
        } else {
            // 권한 거부 시 토스트 메시지 표시
            Toast.makeText(ctx, "카메라 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
    }

    Column(Modifier.fillMaxSize()) {
        // 상단 툴바: 텍스트 포맷팅 & Undo/Redo
        Row(Modifier.fillMaxWidth().padding(8.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = { editor?.toggleBold() }) { Text("B") }
            Button(onClick = { editor?.toggleItalic() }) { Text("I") }
            Button(onClick = { editor?.toggleUnderline() }) { Text("U") }
            Button(onClick = { editor?.toggleH1() }) { Text("H1") }
            Button(onClick = { editor?.cycleImageGridColumns() }) { Text("그리드") }
            Button(onClick = { editor?.undo() }) { Text("Undo") }
            Button(onClick = { editor?.redo() }) { Text("Redo") }
        }

        // 이미지, 동영상, 유튜브 링크, 카메라 버튼
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = {
                // MIME 타입 지정하여 갤러리 이미지 선택
                pickImages.launch(arrayOf("image/*"))
            }) { Text("이미지 추가") }

            Button(onClick = {
                pickVideo.launch(arrayOf("video/*"))
            }) { Text("동영상 추가") }

            Button(onClick = {
                val youTubeUrl = importText.trim()
                if (youTubeUrl.isNotBlank()) editor?.addYouTube(youTubeUrl)
            }) { Text("유튜브 추가") }

            Button(onClick = {
                // API 23 이상에서 카메라 권한이 없으면 요청
                if (Build.VERSION.SDK_INT >= 23 &&
                    ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                    requestCamera.launch(Manifest.permission.CAMERA)
                } else {
                    // 권한이 이미 있으면 바로 사진 촬영
                    val uri = createImageUri(ctx)
                    lastPhotoUri = uri
                    takePicture.launch(uri)
                }
            }) { Text("카메라") }
        }

        // HTML 또는 유튜브 URL 입력 영역
        Row(Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                value = importText,
                onValueChange = { importText = it },
                label = { Text("유튜브 URL 또는 HTML 입력") },
                modifier = Modifier.weight(1f)
            )
            Button(onClick = {
                val txt = importText.trim()
                // HTML 태그 문자열이면 fromHtml 로 역직렬화
                if (txt.startsWith("<")) {
                    editor?.fromHtml(txt)
                }
                // http로 시작하면 유튜브 URL로 처리
                else if (txt.startsWith("http")) {
                    editor?.addYouTube(txt)
                }
            }) { Text("가져오기") }
        }

        // 에디터 내용을 HTML로 내보내기
        Row(Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = {
                exportText = editor?.toHtml().orEmpty()
            }) { Text("HTML 내보내기") }
        }

        // 내보낸 HTML 표시 영역
        OutlinedTextField(
            value = exportText,
            onValueChange = { exportText = it },
            label = { Text("내보낸 HTML") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            minLines = 2
        )

        // 실제 RichEditorView를 Compose에 포함
        AndroidView(
            modifier = Modifier.padding(8.dp).weight(1f).fillMaxWidth(),
            factory = { context ->
                RichEditorView(context).also { editor = it }
            },
            update = { v -> editor = v }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun EditorScreenPreview() {
    AndroidExampleTheme {
        EditorScreen()
    }
}

/**
 * MediaStore에 이미지를 저장할 빈 Uri를 만들어 반환하는 함수.
 * 카메라 촬영 시 해당 Uri에 사진이 기록됨.
 */
fun createImageUri(context: Context): Uri {
    // 파일명: "IMG_yyyyMMdd_HHmmss.jpg"
    val name = "IMG_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val cv = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "$name.jpg") // 표시 이름
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")   // MIME 타입
        // Android 10(Q) 이상에서는 상대 경로 지정 가능 (갤러리에서 "Pictures/RichEditor"에 보이게 됨)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/RichEditor")
        }
    }

    // API 29(Q)이상: getContentUri(VOLUME_EXTERNAL_PRIMARY)
    // 이하: EXTERNAL_CONTENT_URI
    val images = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    } else {
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }

    // ContentResolver.insert 로 실제 Uri 생성
    return context.contentResolver.insert(images, cv)
        ?: error("이미지 URI 생성 실패")
}
