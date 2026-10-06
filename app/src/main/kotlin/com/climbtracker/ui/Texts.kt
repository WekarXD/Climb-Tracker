package com.climbtracker.ui

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.climbtracker.R
import com.climbtracker.core.tracker.ColorName
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAccessor
import java.util.Locale

/**
 * Locale of the language the texts of the app are in. It is not always the phone's: on a phone
 * set to a language the app is not translated to, the texts are in English, and dates and
 * numbers must be written the English way too.
 */
fun Resources.textLocale(): Locale = Locale.forLanguageTag(getString(R.string.locale_tag))

/** "5 de octubre", "October 5". */
fun Resources.dayAndMonth(date: TemporalAccessor): String =
    DateTimeFormatter.ofPattern(getString(R.string.date_day_month), textLocale()).format(date)

/** "5 oct", "Oct 5". */
fun Resources.shortDay(date: TemporalAccessor): String =
    DateTimeFormatter.ofPattern(getString(R.string.date_short), textLocale()).format(date)

/** "5 oct 18:30", "Oct 5 18:30". */
fun Resources.shortDayAndTime(date: TemporalAccessor): String =
    DateTimeFormatter.ofPattern(getString(R.string.date_short) + " HH:mm", textLocale()).format(date)

/** "3.er intento", "Attempt 3". */
fun Resources.attemptNumber(n: Int): String {
    // Spanish abbreviates the ordinal its own way; elsewhere the plain number does.
    val number = if (textLocale().language == "es") ordinal(n) else n.toString()
    return getString(R.string.attempt_number, number)
}

/** [text] with a tick in front when [on]: how menus mark the option in use. */
fun ticked(text: String, on: Boolean): String = if (on) "✓ $text" else text

/** The name of a colour as shown, from the Spanish one the statistics group boulders by. */
@Composable
fun colourLabel(spanish: String): String =
    ColorName.entries.firstOrNull { it.spanish == spanish }?.let { stringResource(it.label()) } ?: spanish

@StringRes
fun ColorName.label(): Int = when (this) {
    ColorName.BLACK -> R.string.colour_black
    ColorName.WHITE -> R.string.colour_white
    ColorName.GREY -> R.string.colour_grey
    ColorName.PINK -> R.string.colour_pink
    ColorName.RED -> R.string.colour_red
    ColorName.ORANGE -> R.string.colour_orange
    ColorName.YELLOW -> R.string.colour_yellow
    ColorName.GREEN -> R.string.colour_green
    ColorName.TURQUOISE -> R.string.colour_turquoise
    ColorName.BLUE -> R.string.colour_blue
    ColorName.PURPLE -> R.string.colour_purple
}
