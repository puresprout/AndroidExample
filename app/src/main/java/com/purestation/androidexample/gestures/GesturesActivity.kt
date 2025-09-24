package com.purestation.androidexample.gestures

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.purestation.androidexample.R
import com.purestation.androidexample.ui.theme.AndroidExampleTheme

class GesturesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AndroidExampleTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Screen(Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun Screen(modifier: Modifier = Modifier) {
    TransformImageComposable(
//        imageResId = R.drawable.ic_launcher_foreground,
        R.drawable.emma_swoboda_unsplash,
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
