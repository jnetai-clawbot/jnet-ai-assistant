package com.jnetai.assistant.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jnetai.assistant.ui.theme.NeonCyan
import com.jnetai.assistant.ui.theme.NeonPurple
import com.jnetai.assistant.ui.theme.NeonPink

/**
 * Subtle neon glow card used throughout the app.
 *
 * The content is placed in a vertical Column so any number of children stack
 * top-to-bottom (text over text, buttons below text) and can NEVER overlap.
 */
@Composable
fun GlowCard(
    modifier: Modifier = Modifier,
    glow: Color = NeonPurple,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, glow.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        content()
    }
}

/** Inline success/error/info status banner (no intrusive dialogs). */
@Composable
fun StatusBanner(
    message: String,
    tone: Tone = Tone.INFO,
    modifier: Modifier = Modifier
) {
    val color = when (tone) {
        Tone.SUCCESS -> NeonCyan
        Tone.ERROR -> NeonPink
        Tone.INFO -> NeonPurple
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color)
        )
        Text(
            message,
            modifier = Modifier
                .padding(start = 8.dp)
                .weight(1f),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            maxLines = 4,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

enum class Tone { SUCCESS, ERROR, INFO }

/** Section header typography helper. */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        modifier = modifier.padding(bottom = 6.dp),
        color = MaterialTheme.colorScheme.primary,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold
    )
}

/** Screen title header. */
@Composable
fun ScreenHeader(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            title,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        subtitle?.let {
            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
    }
}