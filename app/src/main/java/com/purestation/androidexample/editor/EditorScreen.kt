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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun EditorScreen(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var editor by remember { mutableStateOf<RichEditorView?>(null) }
    var lastPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var importText by remember { mutableStateOf("") }
    var exportText by remember { mutableStateOf("") }

    val pickImages = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris != null && uris.isNotEmpty()) editor?.addImages(uris)
    }

    val pickVideo = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { editor?.addVideo(it) }
    }

    val takePicture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok ->
        if (ok) lastPhotoUri?.let { editor?.addImages(listOf(it)) }
    }

    val requestCamera = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = createImageUri(ctx)
            lastPhotoUri = uri
            takePicture.launch(uri)
        } else {
            Toast.makeText(ctx, "카메라 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = { editor?.toggleBold() }) { Text("B") }
            Button(onClick = { editor?.toggleItalic() }) { Text("I") }
            Button(onClick = { editor?.toggleUnderline() }) { Text("U") }
            Button(onClick = { editor?.toggleH1() }) { Text("H1") }
            Button(onClick = { editor?.cycleImageGridColumns() }) { Text("그리드") }
            Button(onClick = { editor?.undo() }) { Text("Undo") }
            Button(onClick = { editor?.redo() }) { Text("Redo") }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = {
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
                if (Build.VERSION.SDK_INT >= 23 &&
                    ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                    requestCamera.launch(Manifest.permission.CAMERA)
                } else {
                    val uri = createImageUri(ctx)
                    lastPhotoUri = uri
                    takePicture.launch(uri)
                }
            }) { Text("카메라") }
        }

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
                if (txt.startsWith("<")) {
                    editor?.fromHtml(txt)
                } else if (txt.startsWith("http")) {
                    editor?.addYouTube(txt)
                }
            }) { Text("가져오기") }
        }

        Row(Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = {
                exportText = editor?.toHtml().orEmpty()
            }) { Text("HTML 내보내기") }
        }

        OutlinedTextField(
            value = exportText,
            onValueChange = { exportText = it },
            label = { Text("내보낸 HTML") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            minLines = 2
        )

        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = { context ->
                RichEditorView(context).also { editor = it }
            },
            update = { v -> editor = v }
        )
    }
}

/**
 * Composable 밖에서도 호출 가능한 일반 함수 버전.
 * 필요 컨텍스트는 호출부(Compose)에서 LocalContext.current로 받아 전달하세요.
 */
fun createImageUri(context: Context): Uri {
    val name = "IMG_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val cv = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "$name.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        // 선택: 갤러리 앱에서 보관 위치를 깔끔하게 하려면 RELATIVE_PATH 지정
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/RichEditor")
        }
    }
    val images = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    } else {
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }
    return context.contentResolver.insert(images, cv)
        ?: error("이미지 URI 생성 실패")
}