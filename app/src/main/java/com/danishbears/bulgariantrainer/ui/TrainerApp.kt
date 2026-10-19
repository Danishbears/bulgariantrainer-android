package com.danishbears.bulgariantrainer.ui

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.danishbears.bulgariantrainer.R
import com.danishbears.bulgariantrainer.data.VocabularyRepository
import com.danishbears.bulgariantrainer.transfer.ContentTransferRepository
import com.danishbears.bulgariantrainer.transfer.DocumentGateway

@Composable
fun TrainerApp(repository: VocabularyRepository, transfer: ContentTransferRepository) {
    val controller = rememberNavController()
    val model: VocabularyViewModel = viewModel(factory = viewModelFactory {
        initializer { VocabularyViewModel(repository, createSavedStateHandle()) }
    })
    val current by controller.currentBackStackEntryAsState()
    val route = current?.destination?.route ?: "home"
    val snackbar = remember { SnackbarHostState() }
    val notice by model.notice.collectAsStateWithLifecycle()
    val message = notice?.let { stringResource(when (it.kind) {
        NoticeKind.ARCHIVED -> R.string.word_archived
        NoticeKind.RESTORED -> R.string.word_restored
        NoticeKind.ARCHIVE_FAILED -> R.string.archive_error
    }) }
    LaunchedEffect(notice?.id) {
        val event = notice
        if (event != null && message != null) {
            snackbar.showSnackbar(message, withDismissAction = true)
            model.clearNotice(event.id)
        }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (!route.startsWith("word/")) NavigationBar {
                listOf("home" to R.string.home, "vocabulary" to R.string.dictionary, "transfer" to R.string.transfer).forEach { (target, label) ->
                    NavigationBarItem(selected = route == target,
                        onClick = { controller.navigate(target) {
                            popUpTo(controller.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        } },
                        icon = { Icon(when (target) { "home" -> Icons.Default.Home; "vocabulary" -> Icons.AutoMirrored.Filled.List; else -> Icons.Default.Settings }, contentDescription = null) },
                        label = { Text(stringResource(label)) })
                }
            }
        },
    ) { padding ->
        NavHost(controller, startDestination = "home",
            modifier = Modifier.padding(padding).consumeWindowInsets(padding)) {
            composable("home") {
                val state by model.dashboard.collectAsStateWithLifecycle()
                DashboardScreen(state, model::retryDashboard,
                    onDictionary = { controller.navigate("vocabulary") { launchSingleTop = true } },
                    onAdd = { controller.navigate("word/new") })
            }
            composable("vocabulary") {
                VocabularyScreen(model, onAdd = { controller.navigate("word/new") },
                    onEdit = { controller.navigate("word/$it") })
            }
            composable("transfer") {
                val context = LocalContext.current.applicationContext
                val model: TransferViewModel = viewModel(factory = viewModelFactory {
                    initializer { TransferViewModel(transfer, DocumentGateway(context), createSavedStateHandle()) }
                })
                TransferScreen(model)
            }
            composable("word/{wordId}", arguments = listOf(navArgument("wordId") { type = NavType.StringType })) {
                val editor: WordEditorViewModel = viewModel(factory = viewModelFactory {
                    initializer { WordEditorViewModel(repository, createSavedStateHandle()) }
                })
                WordEditorScreen(editor, onClose = { controller.popBackStack() })
            }
        }
    }
}
