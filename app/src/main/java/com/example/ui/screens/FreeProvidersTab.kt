package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.NetworkPing
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.CachedProviderEntry
import com.example.data.ExitIpChecker
import com.example.data.FreeProxyProvider
import com.example.data.FreeProxyProviderCache
import com.example.data.FreeProxyProviderManager
import com.example.data.LatencyTier
import com.example.model.countryCodeToFlag
import com.example.ui.FreeProviderResultSnapshot
import com.example.ui.FreeProviderStage
import com.example.ui.FreeProvidersUiState
import com.example.ui.i18n.AppStrings
import com.example.ui.theme.AmberStatus
import com.example.ui.theme.BaldrGold
import com.example.ui.theme.CoralError
import com.example.ui.theme.JetBrainsMonoFamily

@Composable
fun FreeProvidersTab(
    strings: AppStrings,
    isFarsi: Boolean,
    state: FreeProvidersUiState,
    onSelectProvider: (FreeProxyProvider) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCategory by rememberSaveable { mutableIntStateOf(0) } // 0=All, 1=Iran V2Ray, 2=Proxies
    val allProviders = FreeProxyProviderManager.providers
    val visibleProviders = remember(selectedCategory) {
        when (selectedCategory) {
            1 -> allProviders.filter { it.iranRecommended }
            2 -> allProviders.filter { !it.isV2Ray }
            else -> allProviders
        }
    }

    val iranCount = remember(allProviders) { allProviders.count { it.iranRecommended } }
    val proxyCount = remember(allProviders) { allProviders.count { !it.isV2Ray } }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("free_providers_list"),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 6.dp,
            bottom = 110.dp
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Filter Chips Row: All | Iran-Ready (V2Ray) | Proxies (SOCKS/HTTP)
        item(key = "free_providers_filters") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedCategory == 0,
                    onClick = { selectedCategory = 0 },
                    label = { Text("${strings.freeProviderFilterAll} (${allProviders.size})") },
                    modifier = Modifier.testTag("free_filter_all")
                )
                FilterChip(
                    selected = selectedCategory == 1,
                    onClick = { selectedCategory = 1 },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Verified,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = { Text("${strings.freeProviderFilterIran} ($iranCount)") },
                    modifier = Modifier.testTag("free_filter_iran")
                )
                FilterChip(
                    selected = selectedCategory == 2,
                    onClick = { selectedCategory = 2 },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Public,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = { Text("${strings.freeProviderFilterProxy} ($proxyCount)") },
                    modifier = Modifier.testTag("free_filter_proxy")
                )
            }
        }

        // Gradual Background Sync Progress Banner (shown while VPN is connected and refreshing feeds)
        if (state.backgroundSyncProviderName != null && state.backgroundSyncTotal > 0) {
            item(key = "free_providers_bg_sync") {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.40f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("free_provider_bg_sync_banner")
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Sync,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = strings.freeProviderBackgroundSyncBanner(
                                    state.backgroundSyncProviderName,
                                    state.backgroundSyncCompleted,
                                    state.backgroundSyncTotal
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        LinearProgressIndicator(
                            progress = {
                                (state.backgroundSyncCompleted.toFloat() / state.backgroundSyncTotal.toFloat())
                                    .coerceIn(0f, 1f)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        // Intro Info Banner
        item(key = "free_providers_intro") {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.42f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.36f)),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Bolt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = strings.freeProvidersBannerTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = strings.freeProvidersBannerSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Security Disclaimer Banner for Third-Party Free Providers
        item(key = "free_providers_disclaimer") {
            Surface(
                color = AmberStatus.copy(alpha = 0.14f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, AmberStatus.copy(alpha = 0.50f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("free_providers_disclaimer_banner")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.WarningAmber,
                        contentDescription = null,
                        tint = AmberStatus,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(20.dp)
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = strings.freeProvidersDisclaimerTitle,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = AmberStatus
                        )
                        Text(
                            text = strings.freeProvidersDisclaimerBody,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (!state.errorMessage.isNullOrBlank() && state.stage == FreeProviderStage.FAILED) {
            item(key = "free_providers_error") {
                Surface(
                    color = CoralError.copy(alpha = 0.14f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    border = BorderStroke(1.dp, CoralError.copy(alpha = 0.45f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("free_provider_error_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ErrorOutline,
                            contentDescription = null,
                            tint = CoralError,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = state.errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        items(visibleProviders, key = { it.id }) { provider ->
            val isThisActive = state.isBusy && state.activeProviderId == provider.id
            val isOtherBusy = state.isBusy && state.activeProviderId != provider.id
            val isBgUpdating = state.backgroundSyncProviderId == provider.id
            val lastResult = state.resultsByProviderId[provider.id]
            val cachedEntry = state.cachedEntries[provider.id]

            FreeProviderCard(
                strings = strings,
                isFarsi = isFarsi,
                provider = provider,
                description = if (isFarsi) provider.descriptionFa else provider.descriptionEn,
                isActive = isThisActive,
                isDisabled = isOtherBusy,
                isBgUpdating = isBgUpdating,
                activeState = if (isThisActive) state else null,
                cachedEntry = cachedEntry,
                lastResult = lastResult,
                onConnectClick = { onSelectProvider(provider) },
                onCancelClick = onCancel
            )
        }
    }
}

@Composable
private fun FreeProviderCard(
    strings: AppStrings,
    isFarsi: Boolean,
    provider: FreeProxyProvider,
    description: String,
    isActive: Boolean,
    isDisabled: Boolean,
    isBgUpdating: Boolean,
    activeState: FreeProvidersUiState?,
    cachedEntry: CachedProviderEntry?,
    lastResult: FreeProviderResultSnapshot?,
    onConnectClick: () -> Unit,
    onCancelClick: () -> Unit
) {
    val borderColor = when {
        isActive -> MaterialTheme.colorScheme.primary
        isBgUpdating -> MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
        provider.iranRecommended -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.50f)
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.36f)
    }

    val dateShort = remember(cachedEntry?.updatedAtMillis, isFarsi) {
        val ts = cachedEntry?.updatedAtMillis ?: 0L
        if (ts > 0L) FreeProxyProviderCache.formatDateTag(ts, isFarsi) else ""
    }
    val dateTimeText = remember(cachedEntry?.updatedAtMillis, isFarsi) {
        val ts = cachedEntry?.updatedAtMillis ?: 0L
        if (ts > 0L) FreeProxyProviderCache.formatDateTimeTag(ts, isFarsi) else ""
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f)
            } else {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.56f)
            },
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = BorderStroke(if (isActive || isBgUpdating) 1.5.dp else 1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(enabled = !isActive && !isDisabled, onClick = onConnectClick)
            .testTag("free_provider_card_${provider.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = if (provider.isV2Ray) {
                            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (provider.isV2Ray) Icons.Filled.CloudQueue else Icons.Filled.Public,
                                contentDescription = null,
                                tint = if (provider.isV2Ray) BaldrGold else MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        // Provider Name + Date directly on the title
                        Text(
                            text = if (dateShort.isNotEmpty()) "${provider.name} · $dateShort" else provider.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (provider.iranRecommended) {
                                Surface(
                                    color = BaldrGold.copy(alpha = 0.20f),
                                    border = BorderStroke(0.75.dp, BaldrGold.copy(alpha = 0.55f)),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = strings.freeProviderIranBadge,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = BaldrGold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = provider.protocolSummary,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = JetBrainsMonoFamily,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.width(8.dp))

                if (isActive) {
                    OutlinedButton(
                        onClick = onCancelClick,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("free_provider_cancel_${provider.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = strings.cancelButton,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(strings.cancelButton, maxLines = 1)
                    }
                } else {
                    Button(
                        onClick = onConnectClick,
                        enabled = !isDisabled,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("free_provider_connect_${provider.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.NetworkPing,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(strings.freeProviderPingConnectButton, maxLines = 1)
                    }
                }
            }

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Update Timestamp + Pre-bundled / Updated Ready Count Strip
            if (cachedEntry != null || isBgUpdating) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isBgUpdating) {
                            CircularProgressIndicator(
                                strokeWidth = 1.8.dp,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = strings.freeProviderUpdatingNow,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else if (dateTimeText.isNotEmpty() && cachedEntry != null) {
                            Icon(
                                imageVector = if (cachedEntry.isDefaultBundle) Icons.Filled.Schedule else Icons.Filled.CloudDone,
                                contentDescription = null,
                                tint = if (cachedEntry.isDefaultBundle) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (cachedEntry.isDefaultBundle) {
                                    strings.freeProviderDefaultDateLabel(dateTimeText)
                                } else {
                                    strings.freeProviderUpdatedDateLabel(dateTimeText)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = JetBrainsMonoFamily,
                                color = if (cachedEntry.isDefaultBundle) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (cachedEntry != null && cachedEntry.uris.isNotEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = strings.freeProviderCachedCount(cachedEntry.uris.size),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Active Progress Section
            if (isActive && activeState != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp)
                                )
                                val stageLabel = when (activeState.stage) {
                                    FreeProviderStage.FETCHING -> strings.freeProviderFetching
                                    FreeProviderStage.PINGING -> if (activeState.usedOfflineFallback && activeState.pingedCount == 0) {
                                        strings.freeProviderUsingOfflineFallback
                                    } else {
                                        strings.freeProviderPingingProgress(
                                            activeState.pingedCount,
                                            activeState.totalToPing
                                        )
                                    }
                                    FreeProviderStage.VERIFYING -> strings.freeProviderVerifyingBest
                                    else -> strings.freeProviderFetching
                                }
                                Text(
                                    text = stageLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            val liveBest = activeState.bestLatencyMs
                            if (liveBest != null && liveBest > 0) {
                                Text(
                                    text = strings.latencyMs(liveBest),
                                    fontFamily = JetBrainsMonoFamily,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        if (activeState.stage == FreeProviderStage.PINGING && activeState.totalToPing > 0) {
                            LinearProgressIndicator(
                                progress = {
                                    (activeState.pingedCount.toFloat() / activeState.totalToPing.toFloat())
                                        .coerceIn(0f, 1f)
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            } else if (lastResult != null) {
                // Previous Test Result Summary Strip
                val latency = lastResult.bestLatencyMs
                val tier = ExitIpChecker.classifyLatency(latency)
                val pingColor = when (tier) {
                    LatencyTier.EXCELLENT, LatencyTier.GOOD -> MaterialTheme.colorScheme.primary
                    LatencyTier.FAIR -> AmberStatus
                    else -> CoralError
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (latency != null && latency > 0) Icons.Filled.CheckCircle else Icons.Filled.Language,
                                contentDescription = null,
                                tint = pingColor,
                                modifier = Modifier.size(16.dp)
                            )
                            val flag = if (lastResult.countryCode.length == 2) {
                                "${countryCodeToFlag(lastResult.countryCode)} "
                            } else {
                                ""
                            }
                            Text(
                                text = "$flag${lastResult.bestServerLabel}",
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (lastResult.reachableCount > 0) {
                                Text(
                                    text = strings.freeProviderReachableCount(lastResult.reachableCount),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (latency != null && latency > 0) {
                                Surface(
                                    color = pingColor.copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = strings.latencyMs(latency),
                                        fontFamily = JetBrainsMonoFamily,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = pingColor,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
