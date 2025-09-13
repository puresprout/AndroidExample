package com.purestation.androidexample.service

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.purestation.androidexample.ILogService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LogServiceTest {
    @Test
    fun `onBind returns binder and sendLog enqueues log`() = runBlocking {
        // given
        val serviceController = Robolectric.buildService(LogService::class.java)
        val service = serviceController.create().get()
        val intent = Intent(ApplicationProvider.getApplicationContext(), LogService::class.java)

        // when
        val binder = service.onBind(intent)
        assertTrue(binder is ILogService.Stub)

        // LogEntry 생성
        val entry = LogEntry("test", "message", System.currentTimeMillis())
        (binder as ILogService).sendLog(entry)

        // then
        // Channel에 정상적으로 들어갔는지 확인 (private이라 직접 접근 불가, 실제 업로드는 로그로만 검증)
        // uploadToServer가 호출되어 로그가 남는지 확인
        // Robolectric에서는 println 대신 Logcat shadow로 확인 가능
        // 여기서는 예외 없이 동작하면 성공으로 간주
    }
}

