package com.puretube.adfree.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.puretube.adfree.data.download.DownloadEntity
import com.puretube.adfree.data.download.DownloadStatus
import com.puretube.adfree.ui.theme.CrimsonRed
import com.puretube.adfree.ui.theme.DarkBorder
import com.puretube.adfree.ui.theme.DarkCard
import com.puretube.adfree.ui.theme.DarkSurface
import com.puretube.adfree.ui.theme.PureBlack
import com.puretube.adfree.ui.theme.ShieldGreen
import com.puretube.adfree.ui.theme.TextMuted
import com.puretube.adfree.ui.theme.TextPrimary
import com.puretube.adfree.ui.theme.TextSecondary
import com.puretube.adfree.ui.theme.VioletContainer
import com.puretube.adfree.ui.theme.VioletOnContainer
import kotlinx.coroutines.launch
import java.io.File
import java.text.DecimalFormat

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    downloads: List<DownloadEntity>,
    onBack: () -> Unit,
    onPauseDownload: (String) -> Unit,
    onResumeDownload: (String) -> Unit,
    onCancelDownload: (String) -> Unit,
    onDeleteDownload: (id: String, deleteFromStorage: Boolean) -> Unit,
    isAmoledMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val tabTitles = remember { listOf("All", "Downloading", "Completed") }
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { tabTitles.size })
    var itemToDelete by remember { mutableStateOf<DownloadEntity?>(null) }

    val activeDownloads = remember(downloads) {
        downloads.filter {
            it.status == DownloadStatus.DOWNLOADING ||
            it.status == DownloadStatus.PAUSED ||
            it.status == DownloadStatus.QUEUED ||
            it.status == DownloadStatus.FAILED
        }
    }
    val completedDownloads = remember(downloads) {
        downloads.filter { it.status == DownloadStatus.COMPLETED }
    }

    val totalStorageBytes = completedDownloads.sumOf { it.totalBytes }
    val formattedTotalStorage = formatFileSize(totalStorageBytes)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Downloads Manager",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        if (completedDownloads.isNotEmpty()) {
                            Text(
                                text = "$formattedTotalStorage • ${completedDownloads.size} videos in Gallery",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = ShieldGreen
                            )
                        } else {
                            Text(
                                text = "Offline video library with instant resume",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("btn_downloads_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isAmoledMode) PureBlack else DarkSurface
                ),
                modifier = Modifier.testTag("downloads_top_bar")
            )
        },
        containerColor = if (isAmoledMode) PureBlack else DarkSurface,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Tabs with responsive swipe support
            TabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = if (isAmoledMode) PureBlack else DarkSurface,
                contentColor = CrimsonRed,
                indicator = { tabPositions ->
                    if (pagerState.currentPage < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                            color = CrimsonRed
                        )
                    }
                },
                divider = {
                    HorizontalDivider(color = DarkBorder, thickness = 1.dp)
                }
            ) {
                tabTitles.forEachIndexed { index, title ->
                    val count = when (index) {
                        1 -> activeDownloads.size
                        2 -> completedDownloads.size
                        else -> downloads.size
                    }

                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        },
                        modifier = Modifier.height(48.dp),
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = title,
                                    fontWeight = if (pagerState.currentPage == index) FontWeight.Bold else FontWeight.Medium,
                                    color = if (pagerState.currentPage == index) TextPrimary else TextSecondary,
                                    fontSize = 14.sp
                                )
                                if (count > 0) {
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(if (pagerState.currentPage == index) CrimsonRed else DarkBorder)
                                            .padding(horizontal = 7.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = count.toString(),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    )
                }
            }

            // Swipeable Horizontal Pager across tabs
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { pageIndex ->
                val pageList = when (pageIndex) {
                    1 -> activeDownloads
                    2 -> completedDownloads
                    else -> downloads
                }

                if (pageList.isEmpty()) {
                    DownloadsEmptyState(
                        tabIndex = pageIndex,
                        onBrowseVideos = onBack
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(pageList, key = { it.id }) { item ->
                            DownloadItemCard(
                                item = item,
                                onPause = {
                                    onPauseDownload(item.id)
                                    Toast.makeText(context, "Download paused", Toast.LENGTH_SHORT).show()
                                },
                                onResume = {
                                    onResumeDownload(item.id)
                                    Toast.makeText(context, "Resuming download...", Toast.LENGTH_SHORT).show()
                                },
                                onCancel = {
                                    onCancelDownload(item.id)
                                    Toast.makeText(context, "Download cancelled", Toast.LENGTH_SHORT).show()
                                },
                                onDelete = {
                                    itemToDelete = item
                                },
                                onPlay = { playVideo(context, item) },
                                onShare = { shareVideo(context, item) },
                                isAmoledMode = isAmoledMode
                            )
                        }
                    }
                }
            }
        }

        // Delete Choice Dialog (Choose between removing from list only or deleting from storage)
        itemToDelete?.let { deletingItem ->
            DeleteDownloadConfirmationDialog(
                item = deletingItem,
                onDeleteFromListOnly = {
                    onDeleteDownload(deletingItem.id, false)
                    Toast.makeText(context, "Removed from list (Video kept in storage)", Toast.LENGTH_SHORT).show()
                    itemToDelete = null
                },
                onDeleteFromStorageAndList = {
                    onDeleteDownload(deletingItem.id, true)
                    Toast.makeText(context, "Video deleted from phone storage", Toast.LENGTH_SHORT).show()
                    itemToDelete = null
                },
                onDismiss = { itemToDelete = null },
                isAmoledMode = isAmoledMode
            )
        }
    }
}

@Composable
private fun DownloadsEmptyState(
    tabIndex: Int,
    onBrowseVideos: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(DarkCard),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (tabIndex) {
                        1 -> Icons.Default.Download
                        2 -> Icons.Default.CheckCircle
                        else -> Icons.Default.Folder
                    },
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(44.dp)
                )
            }

            Text(
                text = when (tabIndex) {
                    1 -> "No active downloads"
                    2 -> "No completed downloads yet"
                    else -> "Your downloads library is empty"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Text(
                text = "Play any YouTube video and tap the download button to save directly to your phone gallery with high speed.",
                fontSize = 13.sp,
                color = TextMuted,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Button(
                onClick = onBrowseVideos,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CrimsonRed,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("btn_browse_videos_empty")
            ) {
                Icon(Icons.Default.VideoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Browse YouTube", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun DownloadItemCard(
    item: DownloadEntity,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onPlay: () -> Unit,
    onShare: () -> Unit,
    isAmoledMode: Boolean
) {
    val animatedProgress by animateFloatAsState(
        targetValue = (item.progressPercent / 100f).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
        label = "download_progress_${item.id}"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = item.isFinished, onClick = onPlay)
            .animateContentSize()
            .testTag("download_card_${item.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isAmoledMode) PureBlack else DarkCard
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Thumbnail with play overlay & resolution badge
                Box(
                    modifier = Modifier
                        .size(width = 104.dp, height = 65.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black)
                        .clickable(enabled = item.isFinished, onClick = onPlay)
                ) {
                    AsyncImage(
                        model = item.thumbnailUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )

                    if (item.isFinished) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(Color.Black.copy(alpha = 0.35f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(CrimsonRed.copy(alpha = 0.95f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Resolution badge on thumbnail
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(3.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color.Black.copy(alpha = 0.85f))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = item.resolution.ifBlank { "HD" },
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Title & Responsive Metadata Column
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = item.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 17.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Quality Chip
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(VioletContainer)
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = item.qualityLabel.ifBlank { item.resolution },
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = VioletOnContainer,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        // File Size or Progress
                        Text(
                            text = if (item.isFinished) formatFileSize(item.totalBytes) else "${item.progressPercent}%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextSecondary,
                            maxLines = 1,
                            softWrap = false
                        )

                        // In Gallery Badge (Non-wrapping, compact pill)
                        if (item.status == DownloadStatus.COMPLETED) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(ShieldGreen.copy(alpha = 0.15f))
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = ShieldGreen,
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Text(
                                        text = "In Gallery",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = ShieldGreen,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Active Progress & Speed Row (Only when downloading)
            if (!item.isFinished) {
                Spacer(modifier = Modifier.height(10.dp))

                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = when (item.status) {
                        DownloadStatus.PAUSED -> Color(0xFFFFA000)
                        DownloadStatus.FAILED -> CrimsonRed
                        else -> ShieldGreen
                    },
                    trackColor = DarkBorder
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val downloadedStr = formatFileSize(item.downloadedBytes)
                    val totalStr = formatFileSize(item.totalBytes)
                    val speedStr = if (item.speedBytesPerSec > 0 && item.status == DownloadStatus.DOWNLOADING) {
                        "${formatFileSize(item.speedBytesPerSec)}/s"
                    } else ""

                    Text(
                        text = "$downloadedStr / $totalStr" + if (speedStr.isNotBlank()) " • $speedStr" else "",
                        fontSize = 11.sp,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    Text(
                        text = when (item.status) {
                            DownloadStatus.DOWNLOADING -> "Downloading..."
                            DownloadStatus.PAUSED -> "Paused"
                            DownloadStatus.QUEUED -> "Queued"
                            DownloadStatus.FAILED -> "Failed"
                            else -> ""
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (item.status) {
                            DownloadStatus.PAUSED -> Color(0xFFFFA000)
                            DownloadStatus.FAILED -> CrimsonRed
                            DownloadStatus.DOWNLOADING -> ShieldGreen
                            else -> TextMuted
                        },
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action Buttons Row with high tactile feedback & >=48dp touch accessibility
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (item.isFinished) {
                    // Play Button
                    Button(
                        onClick = onPlay,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CrimsonRed,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("btn_play_${item.id}")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Play", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Share Button
                    IconButton(
                        onClick = onShare,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(DarkBorder.copy(alpha = 0.5f))
                            .testTag("btn_share_${item.id}")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = TextPrimary, modifier = Modifier.size(16.dp))
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Delete Button
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(DarkBorder.copy(alpha = 0.5f))
                            .testTag("btn_delete_${item.id}")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF5350), modifier = Modifier.size(16.dp))
                    }
                } else {
                    // Active download controls: Pause / Resume / Cancel / Retry
                    if (item.status == DownloadStatus.DOWNLOADING) {
                        Button(
                            onClick = onPause,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DarkBorder,
                                contentColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .height(34.dp)
                                .testTag("btn_pause_${item.id}")
                        ) {
                            Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Pause", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    } else if (item.status == DownloadStatus.PAUSED) {
                        Button(
                            onClick = onResume,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ShieldGreen,
                                contentColor = PureBlack
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .height(34.dp)
                                .testTag("btn_resume_${item.id}")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Resume", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    } else if (item.status == DownloadStatus.FAILED) {
                        Button(
                            onClick = onResume,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CrimsonRed,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .height(34.dp)
                                .testTag("btn_retry_${item.id}")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Retry", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(DarkBorder.copy(alpha = 0.5f))
                            .testTag("btn_cancel_${item.id}")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel", tint = TextMuted, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

private fun playVideo(context: Context, item: DownloadEntity) {
    try {
        val file = File(item.localFilePath)
        val uri: Uri = try {
            if (file.exists()) {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            } else if (item.contentUri != null) {
                Uri.parse(item.contentUri)
            } else {
                Toast.makeText(context, "Media file not found in storage", Toast.LENGTH_SHORT).show()
                return
            }
        } catch (e: Exception) {
            if (item.contentUri != null) {
                Uri.parse(item.contentUri)
            } else {
                Toast.makeText(context, "Media file not found in storage", Toast.LENGTH_SHORT).show()
                return
            }
        }

        val isAudio = item.mimeType.startsWith("audio") || item.format == "m4a" || item.format == "mp3"
        val mime = if (isAudio) "audio/*" else (if (item.mimeType.isNotBlank()) item.mimeType else "video/*")

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, if (isAudio) "Play Audio with" else "Play with"))
    } catch (e: Exception) {
        Toast.makeText(context, "Cannot open media player: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun shareVideo(context: Context, item: DownloadEntity) {
    try {
        val file = File(item.localFilePath)
        val uri: Uri = try {
            if (file.exists()) {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            } else if (item.contentUri != null) {
                Uri.parse(item.contentUri)
            } else {
                Toast.makeText(context, "Media file not found", Toast.LENGTH_SHORT).show()
                return
            }
        } catch (e: Exception) {
            if (item.contentUri != null) {
                Uri.parse(item.contentUri)
            } else {
                Toast.makeText(context, "Media file not found", Toast.LENGTH_SHORT).show()
                return
            }
        }

        val isAudio = item.mimeType.startsWith("audio") || item.format == "m4a" || item.format == "mp3"
        val mime = if (isAudio) "audio/*" else (if (item.mimeType.isNotBlank()) item.mimeType else "video/*")

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "Listen/Watch: ${item.title}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, if (isAudio) "Share Audio" else "Share Video"))
    } catch (e: Exception) {
        Toast.makeText(context, "Cannot share media: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val df = DecimalFormat("#.0")
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0

    return when {
        gb >= 1.0 -> "${df.format(gb)} GB"
        mb >= 1.0 -> "${df.format(mb)} MB"
        kb >= 1.0 -> "${df.format(kb)} KB"
        else -> "$bytes B"
    }
}

@Composable
fun DeleteDownloadConfirmationDialog(
    item: DownloadEntity,
    onDeleteFromListOnly: () -> Unit,
    onDeleteFromStorageAndList: () -> Unit,
    onDismiss: () -> Unit,
    isAmoledMode: Boolean = false
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, DarkBorder, RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isAmoledMode) PureBlack else DarkSurface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(CrimsonRed.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = CrimsonRed,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "Delete Download",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Choose removal option",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Video info preview box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkCard)
                        .padding(10.dp)
                ) {
                    Column {
                        Text(
                            text = item.title,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${item.qualityLabel} • ${formatFileSize(item.totalBytes)}",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Option 1: Remove from List Only
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onDeleteFromListOnly),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(ShieldGreen.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = ShieldGreen,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Remove from List Only",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Keeps video saved in your Phone Gallery & Storage",
                                fontSize = 11.sp,
                                color = TextMuted,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Option 2: Delete from List & Storage
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onDeleteFromStorageAndList),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = CrimsonRed.copy(alpha = 0.12f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CrimsonRed.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(CrimsonRed.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                tint = CrimsonRed,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Delete from List & Phone Storage",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = CrimsonRed
                            )
                            Text(
                                text = "Permanently deletes the video file from your phone storage",
                                fontSize = 11.sp,
                                color = TextMuted,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DarkBorder,
                        contentColor = TextPrimary
                    )
                ) {
                    Text("Cancel", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
