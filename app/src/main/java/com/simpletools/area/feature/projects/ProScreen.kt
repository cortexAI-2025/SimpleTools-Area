package com.simpletools.area.feature.projects

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.simpletools.area.R
import com.simpletools.area.core.billing.AreaBilling
import com.simpletools.area.core.billing.BillingState
import com.simpletools.area.ui.AppTopBar

@Composable
internal fun ProScreen(
    billing: AreaBilling,
    activity: Activity,
    onBack: () -> Unit,
    onProjects: () -> Unit,
    onTrack: () -> Unit,
) {
    val state by billing.state.collectAsState()
    Scaffold(topBar = { AppTopBar(stringResource(R.string.area_pro), onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.area_free_summary))
            Text(stringResource(R.string.area_pro_summary))
            if (state.active) Text(stringResource(R.string.area_pro_active))
            if (state.pending) Text(stringResource(R.string.area_pro_pending))
            val status = when (state.status) {
                BillingState.Status.IDLE -> null
                BillingState.Status.CHECKING -> R.string.area_pro_checking
                BillingState.Status.UNAVAILABLE -> R.string.area_pro_unavailable
                BillingState.Status.NO_PURCHASE -> if (state.pending) null else R.string.area_pro_no_purchase
                BillingState.Status.CANCELLED -> R.string.area_pro_cancelled
            }
            status?.let { Text(stringResource(it)) }
            if (!state.active) {
                Button(
                    onClick = { billing.buy(activity) },
                    enabled = state.price != null && !state.pending && state.status != BillingState.Status.CHECKING,
                ) {
                    Text(state.price?.let { stringResource(R.string.area_pro_buy_price, it) } ?: stringResource(R.string.area_pro_buy))
                }
            }
            OutlinedButton(onClick = billing::refresh, enabled = state.status != BillingState.Status.CHECKING) {
                Text(stringResource(R.string.area_pro_restore))
            }
            OutlinedButton(onClick = onProjects) { Text(stringResource(R.string.area_projects)) }
            Button(onClick = onTrack, enabled = state.active) { Text(stringResource(R.string.area_gps_track)) }
        }
    }
}
