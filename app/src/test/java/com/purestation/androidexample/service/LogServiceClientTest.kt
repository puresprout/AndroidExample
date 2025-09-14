package com.purestation.androidexample.service

import android.content.ComponentName
import android.content.Context
import android.os.IBinder
import androidx.test.core.app.ApplicationProvider
import com.purestation.androidexample.ILogResultCallback
import com.purestation.androidexample.ILogService
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.annotation.LooperMode.Mode.PAUSED

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(PAUSED)
class LogServiceClientTest {

    private data class Task(val entry: LogEntry, val cb: ILogResultCallback? = null)

    /** 실제 서비스 대신 사용할 페이크 Stub — 전달된 엔트리를 메모리에 축적 */
    private class FakeLogService : ILogService.Stub() {
        val received = mutableListOf<Task>()

        override fun sendLog(entry: LogEntry?) {
            received += Task(entry!!)
        }

        override fun sendLogWithCallback(
            entry: LogEntry?,
            cb: ILogResultCallback?
        ) {
            received += Task(entry!!, cb!!)
        }

        override fun asBinder(): IBinder = this
    }

    @Test
    fun `sendLog - 바인딩 전 큐 적재, 연결 후 모두 전송`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val client = LogServiceClient(context)
        val fake = FakeLogService()

        // 바인딩 전: 큐잉
        val e1 = mockk<LogEntry>(relaxed = true)
        val e2 = mockk<LogEntry>(relaxed = true)
        assertTrue(client.sendLog(e1))
        assertTrue(client.sendLog(e2))
        assertFalse(client.isBound.value)
        assertTrue(fake.received.isEmpty())

        // 연결 콜백 직접 트리거
        client.connection.onServiceConnected(
            ComponentName(context, LogService::class.java),
            fake.asBinder()
        )

        // 큐 플러시 확인
        assertTrue(client.isBound.value)
        assertEquals(2, fake.received.size)
        assertSame(e1, fake.received[0].entry)
        assertSame(e2, fake.received[1].entry)
    }

    @Test
    fun `sendLog - 서비스 null이면 true 반환 및 큐 적재`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val client = LogServiceClient(context)

        val entry = mockk<LogEntry>(relaxed = true)
        val ok = client.sendLog(entry)
        assertTrue("서비스 미연결 시에도 호출부는 true를 받아야 함", ok)
    }
}
