package com.pantrypulse.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.pantrypulse.app.ui.theme.PulseTheme

/** One entry in the quick-actions menu. [highlight] draws it in the accent colour (e.g. free food nearby). */
data class QuickAction(val icon: ImageVector, val label: String, val highlight: Boolean = false, val onClick: () -> Unit)

/**
 * A single floating button that opens a tidy menu of the app's main shortcuts, instead of several
 * separate floating buttons. [badgeCount] shows on the closed button (e.g. giveaways near you).
 * Fills its parent so the dimmed backdrop can cover the screen; tapping the backdrop or Back closes it.
 */
@Composable
fun QuickActionsFab(actions: List<QuickAction>, badgeCount: Int, modifier: Modifier = Modifier) {
    val c = PulseTheme.colors
    var expanded by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = expanded) { expanded = false }

    Box(modifier.fillMaxSize()) {
        AnimatedVisibility(expanded, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { expanded = false },
            )
        }
        Column(
            Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 104.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AnimatedVisibility(expanded) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    actions.forEach { action ->
                        val run = { expanded = false; action.onClick() }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Surface(
                                modifier = Modifier.clickable(onClick = run),
                                shape = RoundedCornerShape(14.dp),
                                color = if (action.highlight) c.accent else c.card,
                                contentColor = if (action.highlight) Color.White else c.ink,
                                shadowElevation = 3.dp,
                            ) {
                                Text(action.label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                            }
                            SmallFloatingActionButton(
                                onClick = run,
                                containerColor = if (action.highlight) c.accent else c.card,
                                contentColor = if (action.highlight) Color.White else c.ink,
                                modifier = Modifier.testTag("quick_${action.label}"),
                            ) { Icon(action.icon, contentDescription = action.label) }
                        }
                    }
                }
            }
            FloatingActionButton(
                onClick = { expanded = !expanded },
                containerColor = c.accent,
                contentColor = Color.White,
                modifier = Modifier.testTag("fab_quick"),
            ) {
                BadgedBox(badge = { if (badgeCount > 0 && !expanded) Badge { Text("$badgeCount") } }) {
                    Icon(if (expanded) Icons.Outlined.Close else Icons.Outlined.Add, contentDescription = if (expanded) "Close quick actions" else "Quick actions")
                }
            }
        }
    }
}
