package com.purestation.androidexample.gestures

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.purestation.androidexample.R

@Composable
fun GesturesHomeScreen(onClick: (GesturesDestination) -> Unit) {
    LazyColumn {
        itemsIndexed(itemList) { index, screen ->
            Text(text = screen.title,
                modifier = Modifier.fillMaxWidth().padding(16.dp).clickable {
                    onClick(screen)
                })

            if (index < itemList.lastIndex) {
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun TransformImageScreen(modifier: Modifier = Modifier) {
    TransformImageComposable(
//        imageResId = R.drawable.ic_launcher_foreground,
        R.drawable.caroline_badran_unsplash,
        modifier = modifier.fillMaxSize()
    )
}

@Composable
fun TransformCanvasScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // 리소스를 Bitmap으로 디코딩 (리컴포지션 최소화)
    val bitmap = remember(R.drawable.caroline_badran_unsplash) {
        BitmapFactory.decodeResource(
            context.resources,
            R.drawable.caroline_badran_unsplash
        )
    }

    TransformCanvasComposable(
        bitmap,
        modifier = modifier.fillMaxSize()
    )
}

@Composable
fun TransformImageComposable(
    @DrawableRes imageResId: Int,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            TransformImageView(context).apply {
                // 여기서 바로 이미지 지정
                imageResId.let {
                    val d = AppCompatResources.getDrawable(context, it)
                    setImageDrawable(d)
                }
            }
        },
        update = { view ->
            // 필요하면 상태 변화에 따라 view.setImageDrawable(...) 등 업데이트
        }
    )
}

@Composable
fun TransformCanvasComposable(bitmap: Bitmap, modifier: Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            TransformCanvasView(context).apply {
                setBitmap(bitmap)
            }
        },
        update = { view ->
            view.setBitmap(bitmap)
        }
    )
}
