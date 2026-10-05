package com.climbtracker.ui

import android.app.Application
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.climbtracker.ClimbApp
import com.climbtracker.core.tracker.AttemptRecord
import com.climbtracker.core.tracker.BoulderRecord
import com.climbtracker.core.tracker.Bucket
import com.climbtracker.core.tracker.ColorNames
import com.climbtracker.core.tracker.DayVolume
import com.climbtracker.core.tracker.PeriodSummary
import com.climbtracker.core.tracker.Records
import com.climbtracker.core.tracker.Stats
import com.climbtracker.core.tracker.StatsPeriod
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

class ProfileViewModel(app: Application) : AndroidViewModel(app) {
    private val climb = app as ClimbApp

    /** Every boulder, taken-down ones included, in the shape the statistics need. */
    val records: StateFlow<List<Pair<Long?, BoulderRecord>>> = climb.repository.summaries()
        .map { summaries ->
            val zone = ZoneId.systemDefault()
            fun day(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
            summaries.map { summary ->
                summary.gymId to BoulderRecord(
                    id = summary.boulder.id,
                    name = summary.boulder.name,
                    grade = summary.boulder.grade,
                    colorName = ColorNames.nameOf(summary.color),
                    color = summary.color,
                    createdDay = day(summary.boulder.createdAt),
                    attempts = summary.attempts.map { AttemptRecord(day(it.date), it.result) },
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var period by mutableStateOf(StatsPeriod.WEEK)

    val gyms = climb.repository.gyms().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Gym the statistics are limited to, or null for all of them. */
    var gym by mutableStateOf<Long?>(null)
}

private val RECORD_DAY = DateTimeFormatter.ofPattern("d 'de' MMMM", Locale.forLanguageTag("es-ES"))
private val SendTint = Color(0xFFE3F1E7)
private val TriedTint = Color(0xFFF1D9CB)

@Composable
fun ProfileScreen(bottomBar: @Composable () -> Unit = {}, vm: ProfileViewModel = viewModel()) {
    val tagged by vm.records.collectAsStateWithLifecycle()
    val gyms by vm.gyms.collectAsStateWithLifecycle()
    val boulders = tagged.filter { vm.gym == null || it.first == vm.gym }.map { it.second }
    val period = vm.period
    val today = LocalDate.now()
    val span = Stats.span(period, today)
    val summary = Stats.summary(boulders, span)
    val previous = Stats.previousSpan(period, today)?.let { Stats.summary(boulders, it) }
    val colours = Stats.byColor(boulders, span)
    val grades = Stats.byGrade(boulders, span)

    Scaffold(containerColor = Cream, bottomBar = bottomBar) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text("Perfil", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold) }
            item { PeriodHeader(period, onSelect = { vm.period = it }) }
            if (gyms.isNotEmpty()) {
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill(selected = vm.gym == null, onClick = { vm.gym = null }) { Text("Todos") }
                        for (gym in gyms) {
                            Pill(selected = vm.gym == gym.id, onClick = { vm.gym = gym.id }) { Text(gym.name) }
                        }
                    }
                }
            }
            item { SummaryCard(summary, previous) }

            item { SectionTitle("Volumen de entrenamiento") }
            item { VolumeChart(Stats.volumeByWeekday(boulders, span)) }

            item { SectionTitle("Por color") }
            item { Card { if (colours.isEmpty()) Empty() else ColourBars(colours) } }

            item { SectionTitle("Por grado") }
            item { Card { if (grades.isEmpty()) Empty() else GradeRows(grades) } }

            item { SectionTitle("Constancia") }
            item { Card { Consistency(Stats.consistency(boulders, today), Stats.weekStreak(boulders, today)) } }

            item { SectionTitle("Récords") }
            item { Card { RecordRows(Stats.records(boulders)) } }
        }
    }
}

@Composable
private fun PeriodHeader(period: StatsPeriod, onSelect: (StatsPeriod) -> Unit) {
    val (title, versus) = when (period) {
        StatsPeriod.WEEK -> "ESTA SEMANA" to "VS SEMANA ANTERIOR"
        StatsPeriod.MONTH -> "ESTE MES" to "VS MES ANTERIOR"
        StatsPeriod.YEAR -> "ESTE AÑO" to "VS AÑO ANTERIOR"
        StatsPeriod.ALL -> "DESDE SIEMPRE" to ""
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Terracotta, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            if (versus.isNotEmpty()) Text(versus, color = Muted, style = MaterialTheme.typography.labelLarge)
        }
        Surface(shape = RoundedCornerShape(50), color = CardWhite, border = BorderStroke(1.dp, Hairline)) {
            Row(Modifier.padding(3.dp)) {
                for ((option, label) in listOf(
                    StatsPeriod.WEEK to "S", StatsPeriod.MONTH to "M", StatsPeriod.YEAR to "A", StatsPeriod.ALL to "Todo",
                )) {
                    val selected = option == period
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (selected) Terracotta else Color.Transparent)
                            .clickable { onSelect(option) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text(label, color = if (selected) Color.White else Muted, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(now: PeriodSummary, before: PeriodSummary?) {
    Surface(shape = RoundedCornerShape(20.dp), color = CardWhite, contentColor = Ink, border = BorderStroke(1.dp, Hairline)) {
        Row(Modifier.fillMaxWidth().height(96.dp)) {
            SummaryCell("TOPS", now.tops, before?.tops, Modifier.weight(1f))
            Box(Modifier.width(1.dp).fillMaxHeight().background(Hairline))
            SummaryCell("FLASHES", now.flashes, before?.flashes, Modifier.weight(1f))
            Box(Modifier.width(1.dp).fillMaxHeight().background(Hairline))
            SummaryCell("SESIONES", now.sessions, before?.sessions, Modifier.weight(1f))
        }
    }
}

@Composable
private fun SummaryCell(label: String, value: Int, before: Int?, modifier: Modifier) {
    Column(modifier.padding(14.dp)) {
        Text(label, color = Muted, style = MaterialTheme.typography.labelMedium, letterSpacing = 1.2.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
            val delta = if (before == null) 0 else value - before
            if (delta != 0) {
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(if (delta > 0) SendTint else TriedTint)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        if (delta > 0) "↑$delta" else "↓${-delta}",
                        color = if (delta > 0) SendGreen else Terracotta,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        modifier = Modifier.padding(top = 8.dp),
        color = Muted,
        style = MaterialTheme.typography.labelLarge,
        letterSpacing = 1.2.sp,
    )
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = CardWhite,
        contentColor = Ink,
        border = BorderStroke(1.dp, Hairline),
    ) {
        Box(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun Empty() {
    Text("Sin datos en este periodo.", color = Muted)
}

/** One column per weekday: the pale bar is boulders tried, the solid part those topped. */
@Composable
private fun VolumeChart(volume: List<DayVolume>) {
    val highest = max(1, volume.maxOf { it.tried })
    Column {
        Row(
            Modifier.fillMaxWidth().height(140.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            volume.forEachIndexed { index, day ->
                Column(
                    Modifier.weight(1f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    if (day.tried > 0) Text(day.tried.toString(), color = Muted, style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(if (day.tried == 0) 3.dp else (88 * day.tried / highest).dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (day.tried == 0) Hairline else TriedTint),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        if (day.topped > 0) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(day.topped.toFloat() / day.tried)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Terracotta),
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "LMXJVSD"[index].toString(),
                        color = if (day.tried > 0) Ink else Muted,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Legend(TriedTint, "${volume.sumOf { it.tried }} intentados")
            Spacer(Modifier.width(16.dp))
            Legend(Terracotta, "${volume.sumOf { it.topped }} encadenados")
        }
    }
}

@Composable
private fun Legend(colour: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 18.dp, height = 6.dp).clip(RoundedCornerShape(50)).background(colour))
        Spacer(Modifier.width(6.dp))
        Text(text, color = Muted, style = MaterialTheme.typography.labelLarge)
    }
}

/** One bar per hold colour: its height is the number of boulders, the solid part those sent. */
@Composable
private fun ColourBars(buckets: List<Bucket>) {
    val highest = max(1, buckets.maxOf { it.total })
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        for (bucket in buckets) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${bucket.sent}/${bucket.total}", color = Muted, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                Box(Modifier.height(96.dp), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier
                            .width(34.dp)
                            .height((96 * bucket.total / highest).dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(bucket.color).copy(alpha = 0.25f)),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        if (bucket.sent > 0) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(bucket.sent.toFloat() / bucket.total)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(bucket.color)),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                ColorDot(bucket.color, 10.dp)
                Text(bucket.label, color = Muted, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun GradeRows(buckets: List<Bucket>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (bucket in buckets) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(bucket.label, Modifier.width(48.dp), fontWeight = FontWeight.Bold)
                Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(50)).background(Hairline)) {
                    if (bucket.sent > 0) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(bucket.sent.toFloat() / bucket.total)
                                .clip(RoundedCornerShape(50))
                                .background(Terracotta),
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text("${bucket.sent}/${bucket.total}", color = Muted)
            }
        }
    }
}

/** A column per week, oldest on the left, with a square per day from Monday down to Sunday. */
@Composable
private fun Consistency(weeks: List<List<Boolean>>, streak: Int) {
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            for (week in weeks) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    for (climbed in week) {
                        Box(
                            Modifier.size(18.dp).clip(RoundedCornerShape(4.dp))
                                .background(if (climbed) Terracotta else Hairline),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            when (streak) {
                0 -> "Sin racha de semanas todavía."
                1 -> "1 semana seguida con sesión."
                else -> "$streak semanas seguidas con sesión."
            },
            color = Muted,
        )
    }
}

@Composable
private fun RecordRows(records: Records) {
    val hardest = records.hardestSend
    val most = records.mostAttempts
    val best = records.bestSession
    if (hardest == null && most == null && best == null) {
        Text("Registra intentos para ver tus récords.", color = Muted)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (hardest != null) RecordRow("Grado más alto encadenado", hardest)
        if (best != null) RecordRow("Mejor sesión", "${best.second} tops · ${RECORD_DAY.format(best.first)}")
        if (most != null) RecordRow("Bloque más peleado", "${most.first} · ${most.second} intentos")
    }
}

@Composable
private fun RecordRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = Muted)
        Text(value, fontWeight = FontWeight.Bold)
    }
}
