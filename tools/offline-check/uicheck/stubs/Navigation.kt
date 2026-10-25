@file:Suppress("unused", "UNUSED_PARAMETER")

package androidx.navigation

import android.os.Bundle

open class NavDestination {
    val id: Int = 0
    val route: String? = null
}

class NavGraph : NavDestination() {
    companion object {
        @JvmStatic fun NavGraph.findStartDestination(): NavDestination = this
    }
}

class NavBackStackEntry {
    val destination: NavDestination = NavDestination()
    val arguments: Bundle? = null
}

class PopUpToBuilder {
    var inclusive: Boolean = false
    var saveState: Boolean = false
}

class NavOptionsBuilder {
    var launchSingleTop: Boolean = false
    var restoreState: Boolean = false
    fun popUpTo(id: Int, popUpToBuilder: PopUpToBuilder.() -> Unit = {}) {}
    fun popUpTo(route: String, popUpToBuilder: PopUpToBuilder.() -> Unit = {}) {}
}

open class NavController {
    val graph: NavGraph = NavGraph()
    fun navigate(route: String, builder: NavOptionsBuilder.() -> Unit) {}
    fun navigate(route: String) {}
    fun popBackStack(): Boolean = true
}

class NavHostController : NavController()

abstract class NavType<T> {
    companion object {
        @JvmField val StringType: NavType<String?> = object : NavType<String?>() {}
        @JvmField val IntType: NavType<Int> = object : NavType<Int>() {}
    }
}

class NavArgumentBuilder {
    var type: NavType<*> = NavType.StringType
    var defaultValue: Any? = null
    var nullable: Boolean = false
}

class NamedNavArgument

fun navArgument(name: String, builder: NavArgumentBuilder.() -> Unit): NamedNavArgument = NamedNavArgument()

class NavGraphBuilder
