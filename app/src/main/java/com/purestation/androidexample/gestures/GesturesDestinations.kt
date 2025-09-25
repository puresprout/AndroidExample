package com.purestation.androidexample.gestures

sealed interface GesturesDestination {
    val route: String
    val title: String
}

data object Home : GesturesDestination {
    override val route = "home"
    override val title = "Home"
}

data object TransformImage : GesturesDestination {
    override val route = "transform_image"
    override val title = "Transform Image"
}

data object TransformCanvas : GesturesDestination {
    override val route = "transform_canvas"
    override val title = "Transform Canvas"
}

val screens = listOf(Home, TransformImage, TransformCanvas)

val itemList = listOf(TransformImage, TransformCanvas)
