package com.purestation.androidexample

sealed interface AppRoute {
    val route: String

    data object Home : AppRoute {
        override val route = "home"
    }

    // 1뎁스
    data object Flow : AppRoute {
        override val route = "flow"
    }

    data object Service : AppRoute {
        override val route = "service"
    }

    data object Gestures : AppRoute {
        override val route = "gestures"
    } // nested graph의 엔트리

    data object Draw : AppRoute {
        override val route = "draw"
    }

    data object Editor : AppRoute {
        override val route = "editor"
    }

    data object AnalyticsWithDI : AppRoute {
        override val route = "analyticsWithDI"
    }

    data object Chart : AppRoute {
        override val route = "chart"
    }

    // Gestures 하위
    sealed interface GesturesRoute : AppRoute
    data object GesturesHome : GesturesRoute {
        override val route = "gestures/home"
    }

    data object TransformImage : GesturesRoute {
        override val route = "gestures/transformImage"
    }

    data object TransformCanvas : GesturesRoute {
        override val route = "gestures/transformCanvas"
    }
}

val screens = listOf(
    AppRoute.Flow, AppRoute.Service, AppRoute.Gestures, AppRoute.Draw, AppRoute.Editor,
    AppRoute.AnalyticsWithDI
)

val gesturesScreens = listOf(AppRoute.TransformImage, AppRoute.TransformCanvas)
