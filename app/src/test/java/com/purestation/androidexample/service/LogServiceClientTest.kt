package com.purestation.androidexample.service

import android.content.ComponentName
import android.content.Context
import android.os.IBinder
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.purestation.androidexample.ILogService
import io.mockk.mockk
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.annotation.LooperMode.Mode.PAUSED
import java.lang.reflect.Field
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(PAUSED)
class LogServiceClientTest {

    /** 실제 서비스 대신 사용할 페이크 Stub — 전달된 엔트리를 메모리에 축적 */
    private class FakeLogService : ILogService.Stub() {
        val received = mutableListOf<LogEntry?>()
        override fun sendLog(entry: LogEntry?) {
            received += entry
        }
        override fun asBinder(): IBinder = this
    }

    // ---------- 유틸: private 필드 접근 ----------
    private fun <T> getPrivateField(target: Any, name: String): T {
        val f: Field = target::class.java.getDeclaredField(name)
        f.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return f.get(target) as T
    }

    private fun setPrivateField(target: Any, name: String, value: Any?) {
        val f: Field = target::class.java.getDeclaredField(name)
        f.isAccessible = true
        f.set(target, value)
    }

    // ---------- 메인 루퍼 전진 ----------
    private fun idleMain(ms: Long) {
        val mainShadow = Shadows.shadowOf(Looper.getMainLooper())
        mainShadow.idleFor(ms, TimeUnit.MILLISECONDS)
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
        val connection = getPrivateField<android.content.ServiceConnection>(client, "connection")
        connection.onServiceConnected(
            ComponentName(context, LogService::class.java),
            fake.asBinder()
        )

        // 큐 플러시 확인
        assertTrue(client.isBound.value)
        assertEquals(2, fake.received.size)
        assertSame(e1, fake.received[0])
        assertSame(e2, fake.received[1])
    }

    @Test
    fun `unbind - 상태 초기화 및 재연결 잡 취소, delay 초기화`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val client = LogServiceClient(context)
        val connection = getPrivateField<android.content.ServiceConnection>(client, "connection")
        val fake = FakeLogService()

        // 연결 먼저 성립
        connection.onServiceConnected(
            ComponentName(context, LogService::class.java),
            fake.asBinder()
        )
        assertTrue(client.isBound.value)

        // 재연결 잡이 존재한다고 가정(더미 Job 주입)
        val dummyJob: Job = GlobalScope.launch { /* no-op */ }
        setPrivateField(client, "reconnectJob", dummyJob)

        // 언바인드 수행
        client.unbind()

        // 상태/잡/딜레이 초기화 확인
        assertFalse(client.isBound.value)
        val job = getPrivateField<Job?>(client, "reconnectJob")
        assertNull(job)
        val delayMs: Long = getPrivateField(client, "delayMs")
        assertEquals(500L, delayMs)
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
