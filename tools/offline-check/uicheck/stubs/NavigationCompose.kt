@file:Suppress("unused", "UNUSED_PARAMETER")

package androidx.navigation.compose

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController

@Composable
fun NavHost(navController: NavHostController, startDestination: String, modifier: Modifier = Modifier, route: String? = null, builder: NavGraphBuilder.() -> Unit) {}

fun NavGraphBuilder.composable(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) {}

@Composable
fun rememberNavController(): NavHostController = NavHostController()

@Composable
fun NavController.currentBackStackEntryAsState(): State<NavBackStackEntry?> = mutableStateOf(null)
