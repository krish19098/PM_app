package com.pmbible.app.ui.navigation

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pmbible.app.notifications.Destinations
import com.pmbible.app.ui.AppViewModel
import com.pmbible.app.ui.components.ErrorState
import com.pmbible.app.ui.components.LoadingState
import com.pmbible.app.ui.learn.LearnScreen
import com.pmbible.app.ui.learn.LessonScreen
import com.pmbible.app.ui.library.LibraryScreen
import com.pmbible.app.ui.onboarding.OnboardingScreen
import com.pmbible.app.ui.practice.InterviewListScreen
import com.pmbible.app.ui.practice.InterviewScreen
import com.pmbible.app.ui.practice.PracticeScreen
import com.pmbible.app.ui.practice.UpdatesScreen
import com.pmbible.app.ui.progress.HistoryScreen
import com.pmbible.app.ui.progress.ProgressScreen
import com.pmbible.app.ui.progress.SavedScreen
import com.pmbible.app.ui.session.SessionMode
import com.pmbible.app.ui.session.SessionScreen
import com.pmbible.app.ui.settings.SettingsScreen
import com.pmbible.app.ui.today.TodayScreen

object Routes {
    const val TODAY = "today"
    const val LEARN = "learn"
    const val LIBRARY = "library"
    const val PRACTICE = "practice"
    const val PROGRESS = "progress"
    const val LESSON = "lesson/{id}"
    const val SESSION = "session/{mode}?arg={arg}&minutes={minutes}"
    const val INTERVIEWS = "interviews"
    const val INTERVIEW = "interview/{id}"
    const val UPDATES = "updates"
    const val SAVED = "saved"
    const val HISTORY = "history"
    const val SETTINGS = "settings"

    fun lesson(id: String) = "lesson/$id"
    fun interview(id: String) = "interview/$id"
    fun session(mode: SessionMode, arg: String? = null, minutes: Int? = null) =
        "session/${mode.name}?arg=${arg.orEmpty()}&minutes=${minutes ?: -1}"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.TODAY, "Today", Icons.Outlined.WbSunny),
    Tab(Routes.LEARN, "Learn", Icons.Outlined.AutoStories),
    Tab(Routes.LIBRARY, "Library", Icons.Outlined.Search),
    Tab(Routes.PRACTICE, "Practice", Icons.Outlined.RecordVoiceOver),
    Tab(Routes.PROGRESS, "Progress", Icons.Outlined.Insights),
)

/** Decides between loading, onboarding and the main app, and routes notification taps. */
@Composable
fun PmBibleRoot(app: AppViewModel, pendingOpen: String?, onOpenConsumed: () -> Unit) {
    val prefs by app.prefs.collectAsState()
    val library by app.library.collectAsState()
    val error by app.loadError.collectAsState()
    val p = prefs
    when {
        error != null -> ErrorState(error!!, onRetry = app::loadContent)
        p == null || library == null -> LoadingState("Preparing your library…")
        !p.onboarded -> OnboardingScreen(app)
        else -> MainScaffold(app, pendingOpen, onOpenConsumed)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MainScaffold(app: AppViewModel, pendingOpen: String?, onOpenConsumed: () -> Unit) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route

    LaunchedEffect(pendingOpen) {
        when (pendingOpen) {
            Destinations.SESSION -> nav.navigate(Routes.session(SessionMode.TODAY))
            Destinations.REVIEW -> nav.navigate(Routes.session(SessionMode.REVIEW))
            Destinations.PROGRESS -> nav.switchTab(Routes.PROGRESS)
        }
        if (pendingOpen != null) onOpenConsumed()
    }

    // Each screen draws its own top bar and insets; this scaffold only owns the bottom navigation.
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (tabs.any { it.route == route }) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = { nav.switchTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Routes.TODAY, modifier = Modifier.padding(padding).consumeWindowInsets(padding)) {
            val openLesson: (String) -> Unit = { nav.navigate(Routes.lesson(it)) }
            composable(Routes.TODAY) {
                TodayScreen(
                    app = app,
                    onStart = { mode, arg, minutes -> nav.navigate(Routes.session(mode, arg, minutes)) },
                    onOpenLesson = openLesson,
                    onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                    onOpenInterviews = { nav.navigate(Routes.INTERVIEWS) },
                )
            }
            composable(Routes.LEARN) {
                LearnScreen(app, onOpenLesson = openLesson, onStartChallenge = { nav.navigate(Routes.session(SessionMode.CHECKPOINT, it)) })
            }
            composable(Routes.LIBRARY) {
                LibraryScreen(
                    app = app,
                    onOpenLesson = openLesson,
                    onOpenCase = { nav.navigate(Routes.session(SessionMode.CASE, it)) },
                    onOpenInterview = { nav.navigate(Routes.interview(it)) },
                    onOpenUpdates = { nav.navigate(Routes.UPDATES) },
                )
            }
            composable(Routes.PRACTICE) {
                PracticeScreen(
                    app = app,
                    onOpenInterviews = { nav.navigate(Routes.INTERVIEWS) },
                    onOpenCase = { nav.navigate(Routes.session(SessionMode.CASE, it)) },
                    onStartChallenge = { nav.navigate(Routes.session(SessionMode.CHECKPOINT, it)) },
                    onOpenUpdates = { nav.navigate(Routes.UPDATES) },
                    onOpenLesson = openLesson,
                )
            }
            composable(Routes.PROGRESS) {
                ProgressScreen(
                    app = app,
                    onReview = { nav.navigate(Routes.session(SessionMode.REVIEW)) },
                    onOpenLesson = openLesson,
                    onOpenSaved = { nav.navigate(Routes.SAVED) },
                    onOpenHistory = { nav.navigate(Routes.HISTORY) },
                    onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.LESSON, arguments = listOf(navArgument("id") { type = NavType.StringType })) { e ->
                LessonScreen(
                    app = app,
                    lessonId = e.arguments?.getString("id").orEmpty(),
                    onBack = { nav.popBackStack() },
                    onOpenLesson = openLesson,
                    onStartLesson = { nav.navigate(Routes.session(SessionMode.LESSON, it)) },
                    onOpenCase = { nav.navigate(Routes.session(SessionMode.CASE, it)) },
                    onOpenInterview = { nav.navigate(Routes.interview(it)) },
                    onStartChallenge = { nav.navigate(Routes.session(SessionMode.CHECKPOINT, it)) },
                )
            }
            composable(
                Routes.SESSION,
                arguments = listOf(
                    navArgument("mode") { type = NavType.StringType },
                    navArgument("arg") { type = NavType.StringType; defaultValue = "" },
                    navArgument("minutes") { type = NavType.IntType; defaultValue = -1 },
                ),
            ) { e ->
                val mode = SessionMode.entries.firstOrNull { it.name == e.arguments?.getString("mode") } ?: SessionMode.TODAY
                SessionScreen(
                    app = app,
                    mode = mode,
                    arg = e.arguments?.getString("arg")?.takeIf { it.isNotEmpty() },
                    minutes = e.arguments?.getInt("minutes")?.takeIf { it > 0 },
                    onClose = { nav.popBackStack() },
                    onOpenLesson = openLesson,
                )
            }
            composable(Routes.INTERVIEWS) {
                InterviewListScreen(app, onBack = { nav.popBackStack() }, onOpen = { nav.navigate(Routes.interview(it)) })
            }
            composable(Routes.INTERVIEW, arguments = listOf(navArgument("id") { type = NavType.StringType })) { e ->
                InterviewScreen(app, e.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() }, onOpenLesson = openLesson)
            }
            composable(Routes.UPDATES) { UpdatesScreen(app, onBack = { nav.popBackStack() }, onOpenLesson = openLesson) }
            composable(Routes.SAVED) {
                SavedScreen(
                    app = app,
                    onBack = { nav.popBackStack() },
                    onOpenLesson = openLesson,
                    onOpenCase = { nav.navigate(Routes.session(SessionMode.CASE, it)) },
                    onOpenInterview = { nav.navigate(Routes.interview(it)) },
                )
            }
            composable(Routes.HISTORY) { HistoryScreen(app, onBack = { nav.popBackStack() }, onOpenLesson = openLesson) }
            composable(Routes.SETTINGS) { SettingsScreen(app, onBack = { nav.popBackStack() }) }
        }
    }
}

/** Standard bottom-tab navigation: single top, restore each tab's state. */
private fun NavHostController.switchTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
