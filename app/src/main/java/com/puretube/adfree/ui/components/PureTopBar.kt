package com.puretube.adfree.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.puretube.adfree.ui.theme.CrimsonRed
import com.puretube.adfree.ui.theme.DarkCard
import com.puretube.adfree.ui.theme.DarkSurface
import com.puretube.adfree.ui.theme.PureBlack
import com.puretube.adfree.ui.theme.ShieldGreen
import com.puretube.adfree.ui.theme.TextMuted
import com.puretube.adfree.ui.theme.TextPrimary
import com.puretube.adfree.ui.theme.TextSecondary
import com.puretube.adfree.ui.theme.VioletContainer
import com.puretube.adfree.ui.theme.VioletOnContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PureTopBar(
    currentTitle: String,
    currentUrl: String,
    isLoading: Boolean,
    progress: Int,
    totalAdsBlocked: Int,
    isAdBlockEnabled: Boolean,
    activeDownloadsCount: Int = 0,
    onOpenDownloads: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenSpeed: () -> Unit,
    onOpenSettings: () -> Unit,
    onEnterPiP: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(DarkSurface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // App Brand & Logo
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(CrimsonRed),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "▶",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = "PureTube",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.3).sp,
                        color = TextPrimary
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(VioletContainer)
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "AD-FREE",
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.4.sp,
                            color = VioletOnContainer
                        )
                    }
                }
            }

            // Action Buttons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                // Shield / AdBlock Stats badge
                IconButton(
                    onClick = onOpenStats,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("btn_shield_stats")
                ) {
                    BadgedBox(
                        badge = {
                            if (totalAdsBlocked > 0) {
                                Badge(
                                    containerColor = if (isAdBlockEnabled) ShieldGreen else CrimsonRed,
                                    contentColor = PureBlack
                                ) {
                                    Text(
                                        text = if (totalAdsBlocked > 99) "99+" else totalAdsBlocked.toString(),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "AdBlock Shield",
                            tint = if (isAdBlockEnabled) ShieldGreen else TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Downloads Manager Button
                IconButton(
                    onClick = onOpenDownloads,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("btn_downloads")
                ) {
                    BadgedBox(
                        badge = {
                            if (activeDownloadsCount > 0) {
                                Badge(
                                    containerColor = CrimsonRed,
                                    contentColor = Color.White
                                ) {
                                    Text(
                                        text = activeDownloadsCount.toString(),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Downloads",
                            tint = if (activeDownloadsCount > 0) CrimsonRed else TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Picture-in-Picture
                IconButton(
                    onClick = onEnterPiP,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("btn_pip")
                ) {
                    Icon(
                        imageVector = Icons.Default.PictureInPictureAlt,
                        contentDescription = "Picture-in-Picture",
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Settings Button (Always prominently visible!)
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("btn_settings")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // More Menu
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("btn_more_menu")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More",
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier.background(DarkCard)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Settings & Privacy", color = TextPrimary) },
                            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null, tint = CrimsonRed) },
                            onClick = {
                                menuExpanded = false
                                onOpenSettings()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Downloads Manager", color = TextPrimary) },
                            leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null, tint = CrimsonRed) },
                            onClick = {
                                menuExpanded = false
                                onOpenDownloads()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Playback Speed", color = TextPrimary) },
                            leadingIcon = { Icon(Icons.Default.Speed, contentDescription = null, tint = CrimsonRed) },
                            onClick = {
                                menuExpanded = false
                                onOpenSpeed()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Refresh Page", color = TextPrimary) },
                            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null, tint = CrimsonRed) },
                            onClick = {
                                menuExpanded = false
                                onRefresh()
                            }
                        )
                    }
                }
            }
        }

        // Web Loading Progress
        AnimatedVisibility(
            visible = isLoading,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            LinearProgressIndicator(
                progress = progress / 100f,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp),
                color = CrimsonRed,
                trackColor = Color.Transparent
            )
        }
    }
}
