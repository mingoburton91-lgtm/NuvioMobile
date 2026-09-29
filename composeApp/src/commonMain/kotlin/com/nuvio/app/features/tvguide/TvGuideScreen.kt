package com.nuvio.app.features.tvguide

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal expect fun TvGuideScreen(
    modifier: Modifier = Modifier,
    onOpenStream: (url: String, title: String, channelId: String) -> Unit,
)
