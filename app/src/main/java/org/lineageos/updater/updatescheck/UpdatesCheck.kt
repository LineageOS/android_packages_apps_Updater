/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater.updatescheck

import android.content.Context
import android.os.SystemClock
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.annotation.RawRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.android.settingslib.spa.framework.theme.SettingsDimension
import com.android.settingslib.spa.widget.ui.Lottie
import com.android.settingslib.spa.widget.ui.SettingsBody
import java.util.Date
import kotlinx.coroutines.delay
import org.lineageos.updater.R

private const val MIN_CHECKING_DURATION_MILLIS = 2_000L
// Matches SettingsLib's expressive zero-state background size.
private val AnimationSize = 160.dp

sealed interface UpdatesCheckState {
    data object Idle : UpdatesCheckState
    data object Checking : UpdatesCheckState
    data object NoInternet : UpdatesCheckState
    data object Error : UpdatesCheckState
}

data class UpdatesCheckModel(
    val state: UpdatesCheckState,
    val lastCheckedTimestamp: Long,
    val canCheckForUpdates: Boolean,
)

@Composable
fun UpdatesCheck(
    model: UpdatesCheckModel,
    onCheckClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val state = rememberStateWithMinimumCheckingDuration(model.state)
    val lastCheckedText = remember(model.lastCheckedTimestamp) {
        formatLastCheckedText(context, model.lastCheckedTimestamp)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(SettingsDimension.itemPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SettingsDimension.itemPaddingVertical),
    ) {
        when (state) {
            UpdatesCheckState.Idle -> Unit
            UpdatesCheckState.Checking -> StatusContent(
                R.raw.sysupdater_progress,
                R.string.checking_for_updates,
            )

            UpdatesCheckState.NoInternet -> StatusContent(
                R.raw.sysupdater_error,
                R.string.check_your_internet_connection,
            )

            UpdatesCheckState.Error -> StatusContent(
                R.raw.sysupdater_error,
                R.string.updates_check_failed,
            )
        }

        if (model.canCheckForUpdates && state !is UpdatesCheckState.Checking) {
            CheckForUpdatesButton(onClick = onCheckClick)
        }

        Text(
            text = lastCheckedText,
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = SettingsDimension.itemPaddingEnd),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun CheckForUpdatesButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(text = stringResource(R.string.check_for_updates))
    }
}

/**
 * Shows the animated status illustration and its matching message.
 */
@Composable
private fun StatusContent(
    @RawRes animationResId: Int,
    @StringRes textResId: Int,
) {
    val text = stringResource(textResId)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SettingsDimension.itemPaddingVertical),
    ) {
        Lottie(
            resId = animationResId,
            modifier = Modifier
                .size(AnimationSize)
                .semantics { contentDescription = text },
        )
        SettingsBody(text)
    }
}

/**
 * Keeps the checking state visible long enough for the progress animation to be readable.
 */
@Composable
private fun rememberStateWithMinimumCheckingDuration(
    state: UpdatesCheckState,
): UpdatesCheckState {
    var displayedState by remember { mutableStateOf(state) }
    var checkingStartedAtMillis by remember { mutableLongStateOf(0L) }

    LaunchedEffect(state) {
        if (state == UpdatesCheckState.Checking) {
            checkingStartedAtMillis = SystemClock.elapsedRealtime()
            displayedState = state
            return@LaunchedEffect
        }

        if (displayedState == UpdatesCheckState.Checking) {
            val elapsed = SystemClock.elapsedRealtime() - checkingStartedAtMillis
            val remaining = MIN_CHECKING_DURATION_MILLIS - elapsed
            if (remaining > 0L) delay(remaining)
        }

        displayedState = state
    }

    return displayedState
}

/**
 * Formats the last checked time.
 *
 * Today's checks show only the time. Older checks show both date and time.
 */
private fun formatLastCheckedText(
    context: Context,
    timestampMillis: Long,
): String {
    val time = DateFormat.getTimeFormat(context).format(Date(timestampMillis))

    if (DateUtils.isToday(timestampMillis)) {
        return context.getString(R.string.header_last_updates_check_time, time)
    }

    val date = DateUtils.formatDateTime(
        context,
        timestampMillis,
        DateUtils.FORMAT_SHOW_DATE or
                DateUtils.FORMAT_ABBREV_MONTH or
                DateUtils.FORMAT_NO_YEAR,
    )

    return context.getString(R.string.header_last_updates_check, date, time)
}
