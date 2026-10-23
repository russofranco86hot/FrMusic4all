/**
 * FrMusic4all (C) 2026
 * Licensed under GPL-3.0
 */

package com.metrolist.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.music.R
import com.metrolist.music.ui.screens.Screens

@Composable
fun WatchTabIndicator(
    currentPage: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = remember {
        listOf(
            Triple(0, R.drawable.home_filled, "Música"),
            Triple(1, R.drawable.ic_tv, "TV"),
            Triple(2, R.drawable.radio, "Radio"),
            Triple(3, R.drawable.library_music_filled, "Local"),
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { (index, iconRes, label) ->
            val isSelected = currentPage == index
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF1E1E1E))
                    .clickable { onTabSelected(index) }
                    .padding(horizontal = if (isSelected) 8.dp else 5.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(iconRes),
                            contentDescription = label,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = label,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = label,
                        tint = Color.LightGray.copy(alpha = 0.8f),
                        modifier = Modifier.size(10.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun WatchTabIndicator(
    currentRoute: String?,
    onTabSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = remember {
        listOf(
            Triple(Screens.Home.route, R.drawable.home_filled, "Música"),
            Triple(Screens.TvStreaming.route, R.drawable.ic_tv, "TV"),
            Triple(Screens.Radio.route, R.drawable.radio, "Radio"),
            Triple(Screens.Library.route, R.drawable.library_music_filled, "Local"),
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { (route, iconRes, label) ->
            val isSelected = currentRoute == route || (route == Screens.Home.route && currentRoute == null)
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF1E1E1E))
                    .clickable { onTabSelected(route) }
                    .padding(horizontal = if (isSelected) 8.dp else 5.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(iconRes),
                            contentDescription = label,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = label,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = label,
                        tint = Color.LightGray.copy(alpha = 0.8f),
                        modifier = Modifier.size(10.dp)
                    )
                }
            }
        }
    }
}
