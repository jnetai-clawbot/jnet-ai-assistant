package com.jnetai.assistant.ui.screens.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jnetai.assistant.data.model.UsageRecord
import com.jnetai.assistant.ui.components.GlowCard
import com.jnetai.assistant.ui.components.SectionHeader
import com.jnetai.assistant.ui.screens.AppViewModel
import com.jnetai.assistant.ui.theme.NeonCyan
import com.jnetai.assistant.ui.theme.NeonPurple
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ActivityScreen(vm: AppViewModel) {
    val stats by produceState(com.jnetai.assistant.usage.UsageStats()) {
        value = vm.usage.stats()
    }
    val usageRecent by vm.usage.recentUsage(50).collectAsState(initial = emptyList())
    val activity by vm.usage.recentActivity(100).collectAsState(initial = emptyList())

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Spacer(Modifier.height(12.dp))
        Text("Activity & Usage", fontSize = 22.sp, fontWeight = FontWeight.Bold)

        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard(Modifier.weight(1f), "Today", "${stats.todayTokens}", "tokens (${stats.todayRequests} req)")
            StatCard(Modifier.weight(1f), "Total", "${stats.totalTokens}", "lifetime tokens")
        }

        Spacer(Modifier.height(12.dp))
        SectionHeader("Recent usage")
        if (usageRecent.isEmpty()) {
            Text("No usage recorded yet", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(usageRecent) { u -> UsageRow(u) }
            item {
                Spacer(Modifier.height(10.dp))
                SectionHeader("Activity log")
            }
            items(activity) { a ->
                GlowCard(Modifier.fillMaxWidth(), glow = NeonPurple.copy(alpha = 0.3f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(a.type.uppercase(), fontSize = 11.sp, color = NeonCyan, fontWeight = FontWeight.SemiBold)
                        Text(
                            " · ${SimpleDateFormat("MMM d HH:mm:ss", Locale.ROOT).format(Date(a.createdAt))}",
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(a.summary, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 2.dp))
                    if (a.detail.isNotBlank()) Text(a.detail, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Spacer(Modifier.height(20.dp))
                Text(
                    "Clear activity log",
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f))
                        .clickable { vm.clearActivityLog() }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.error, fontSize = 13.sp
                )
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun StatCard(modifier: Modifier, label: String, value: String, sub: String) {
    GlowCard(modifier) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = NeonCyan)
        Text(sub, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun UsageRow(u: UsageRecord) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(u.model.ifBlank { "unknown" }, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text(
                "${u.category} · ${SimpleDateFormat("HH:mm", Locale.ROOT).format(Date(u.createdAt))}",
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            "P ${u.promptTokens} + C ${u.completionTokens}",
            fontSize = 12.sp, color = NeonCyan
        )
    }
}