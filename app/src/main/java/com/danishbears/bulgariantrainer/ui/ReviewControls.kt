package com.danishbears.bulgariantrainer.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.danishbears.bulgariantrainer.R
import com.danishbears.bulgariantrainer.review.StudyScope

@Composable
internal fun ReviewScopeSelector(scope: String, onChange: (StudyScope) -> Unit, enabled: Boolean) {
    Column {
        Text(stringResource(R.string.review_scope_hint))
        StudyScope.entries.forEach { option -> FilterChip(selected = scope == option.name, enabled = enabled,
            onClick = { onChange(option) }, label = { Text(stringResource(when (option) {
                StudyScope.NEW -> R.string.review_scope_new
                StudyScope.DUE -> R.string.review_scope_due
                StudyScope.MIXED -> R.string.review_scope_mixed
            })) }) }
    }
}
@Composable
internal fun ReviewRefreshOnResume(onRefresh: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    val latest by rememberUpdatedState(onRefresh)
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) latest() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}
