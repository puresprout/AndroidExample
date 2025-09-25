package com.purestation.androidexample

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.purestation.androidexample.flow.FlowScreen
import com.purestation.androidexample.gestures.GesturesHomeScreen
import com.purestation.androidexample.gestures.TransformCanvasScreen
import com.purestation.androidexample.gestures.TransformImageScreen
import com.purestation.androidexample.service.ServiceScreen
import com.purestation.androidexample.ui.theme.AndroidExampleTheme

@Composable
fun AppNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(
        navController = navController,
        startDestination = AppRoute.Home.route,
        modifier = modifier
    ) {
        composable(AppRoute.Home.route) {
            HomeScreen(onClick = { dest -> navController.navigate(dest.route) })
        }
        composable(AppRoute.Flow.route) {
            FlowScreen()
        }
        composable(AppRoute.Service.route) {
            ServiceScreen()
        }
        composable(AppRoute.Chart.route) {
        }

        // Gestures: Nested Graph
        navigation(
            startDestination = AppRoute.GesturesHome.route,
            route = AppRoute.Gestures.route
        ) {
            composable(AppRoute.GesturesHome.route) {
                GesturesHomeScreen(
                    onClick = { subDest -> navController.navigate(subDest.route) }
                )
            }
            composable(AppRoute.TransformImage.route) {
                TransformImageScreen()
            }
            composable(AppRoute.TransformCanvas.route) {
                TransformCanvasScreen()
            }
        }
    }
}

@Composable
fun HomeScreen(modifier: Modifier = Modifier, onClick: (AppRoute) -> Unit) {
    Column(modifier.padding(horizontal = 16.dp)) {
        screens.forEachIndexed { index, item ->
            Text(
                text = item.route,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .clickable {
                        onClick(item)
                    }
            )

            if (index < gesturesScreens.size) {
                HorizontalDivider()
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    AndroidExampleTheme {
        HomeScreen(onClick = {})
    }
}