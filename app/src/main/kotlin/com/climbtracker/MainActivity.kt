package com.climbtracker

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.climbtracker.ui.ClimbTheme
import com.climbtracker.ui.CropScreen
import com.climbtracker.ui.DetailScreen
import com.climbtracker.ui.EditorScreen
import com.climbtracker.ui.MainTabs
import com.climbtracker.ui.NewWallScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app follows the system theme, and so do the icons of the system bars.
        val bars = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        setContent {
            ClimbTheme {
                ClimbNavHost()
            }
        }
    }
}

@Composable
fun ClimbNavHost() {
    val nav = rememberNavController()
    NavHost(nav, startDestination = "home") {
        composable("home") {
            MainTabs(
                onNew = { nav.navigate("newWall") },
                onOpen = { boulderId -> nav.navigate("detail/$boulderId") },
            )
        }
        composable("newWall") {
            NewWallScreen(
                onImported = { nav.navigate("crop") },
                onWall = { wallId -> nav.navigate("editor/$wallId") },
                onBack = { nav.popBackStack() },
            )
        }
        composable("crop") {
            CropScreen(
                onDone = { wallId -> nav.navigate("editor/$wallId") { popUpTo("home") } },
                onBack = { nav.popBackStack() },
            )
        }
        composable(
            "editor/{wallId}?boulderId={boulderId}",
            arguments = listOf(
                navArgument("wallId") { type = NavType.LongType },
                navArgument("boulderId") {
                    type = NavType.LongType
                    defaultValue = -1L
                },
            ),
        ) {
            EditorScreen(
                onSaved = { boulderId -> nav.navigate("detail/$boulderId") { popUpTo("home") } },
                onBack = { nav.popBackStack() },
            )
        }
        composable(
            "detail/{boulderId}",
            arguments = listOf(navArgument("boulderId") { type = NavType.LongType }),
        ) {
            DetailScreen(
                onEdit = { wallId, boulderId -> nav.navigate("editor/$wallId?boulderId=$boulderId") },
                onScanAnother = { wallId -> nav.navigate("editor/$wallId") },
                onBack = { nav.popBackStack() },
            )
        }
    }
}
