package com.purestation.androidexample.analytics

import javax.inject.Inject

interface AnalyticsService {
    fun analyticsMethods()
}

// 구현체를 DI 그래프에 "어떻게 생성할지" 등록하기 위해 @Inject 생성자를 명시
// - 리플렉션 없이 컴파일 타임에 팩토리를 생성하도록 하는 표식
// - "주입을 받는다"는 의미가 아니라, "이 생성자를 사용해 인스턴스를 제공(provide)해도 된다"는 의미
class AnalyticsServiceImpl @Inject constructor() : AnalyticsService {
    override fun analyticsMethods() {
        println("analyticsMethods")
    }
}

//class AnalyticsServiceImpl @Inject constructor(
//    @ApplicationContext private val ctx: android.content.Context
//) : AnalyticsService {
//    override fun analyticsMethods() {
//
//    }
//}
