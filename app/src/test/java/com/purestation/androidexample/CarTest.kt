package com.purestation.androidexample

import androidx.car.app.model.CarLocation
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CarTest {
    @Test
    fun test1() {
        val carLocation = mockk<CarLocation>()

        every { carLocation.latitude } returns 100.0

        assertTrue(100.0 ==  carLocation.latitude)
    }

    interface CarProperties {
        fun getSpeed(): Float?
    }

    // 일반 앱에서는 CarPropertyManager에 접근 불가
//    // main 소스의 실제 구현체
//    class CarProertiesImpl(val manager: CarPropertyManager) : CarProperties {
//        override fun getSpeed(): Float? = manager.getSpeed()
//    }

    @Test
    fun test2() {
        val carProperties = mockk<CarProperties>()

        every { carProperties.getSpeed() } returns 100f

        assertEquals(100f, carProperties.getSpeed())
    }
}