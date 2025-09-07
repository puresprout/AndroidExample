package com.purestation.androidexample

import android.app.Activity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33]) // Robolectric 지원 SDK 버전
class MainActivityTest {
    @Test
    fun testMainActivityTitle() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        /*
        String resource ID #0x7f0f001c
        android.content.res.Resources$NotFoundException: String resource ID #0x7f0f001c

        위와 같은 오류가 발생하면 build.gradle.kts에 다음 옵션 설정 필요

        isIncludeAndroidResources = true
         */
        val expectedTitle = activity.getString(R.string.app_name)

        assertEquals(expectedTitle, activity.title)
    }
}

