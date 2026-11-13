package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.data.model.TelecomRecord
import com.example.ui.theme.CarrierColors
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun CoverageMapScreen(
    records: List<TelecomRecord>,
    selectedCarrier: String,
    onSelectCarrier: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val visibleRecords = if (selectedCarrier == "Tất cả") {
        records
    } else {
        records.filter { it.carrier == selectedCarrier }
    }
    var focusedRecord by remember(visibleRecords) {
        mutableStateOf(visibleRecords.firstOrNull())
    }
    val hasValidMapsKey = BuildConfig.MAPS_API_KEY.isNotBlank() &&
        BuildConfig.MAPS_API_KEY != "MY_MAPS_API_KEY"

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(16.0544, 106.8), 5.6f)
    }
    val scope = rememberCoroutineScope()

    Column(modifier = modifier.fillMaxSize()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(listOf("Tất cả", "Viettel", "VinaPhone", "MobiFone")) { carrier ->
                FilterChip(
                    selected = selectedCarrier == carrier,
                    onClick = { onSelectCarrier(carrier) },
                    label = { Text(carrier) }
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(22.dp))
        ) {
            if (hasValidMapsKey) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    uiSettings = MapUiSettings(zoomControlsEnabled = true, myLocationButtonEnabled = false)
                ) {
                    visibleRecords.forEach { rec ->
                        val hue = when (rec.carrier) {
                            "Viettel" -> BitmapDescriptorFactory.HUE_RED
                            "VinaPhone" -> BitmapDescriptorFactory.HUE_AZURE
                            "MobiFone" -> BitmapDescriptorFactory.HUE_ORANGE
                            else -> BitmapDescriptorFactory.HUE_CYAN
                        }
                        Marker(
                            state = remember(rec.id, rec.latitude, rec.longitude) {
                                MarkerState(position = LatLng(rec.latitude, rec.longitude))
                            },
                            title = "${rec.carrier} • ${rec.province}",
                            snippet = "${rec.stationCode} | 5G: ${rec.coverage5gPercent}% | TB: ${formatCompactSubs(rec.subscribers)}",
                            icon = BitmapDescriptorFactory.defaultMarker(hue),
                            onClick = {
                                focusedRecord = rec
                                false
                            }
                        )
                    }
                }
            } else {
                InteractiveVietnamTopologyMap(
                    records = visibleRecords,
                    focusedRecord = focusedRecord,
                    onSelectRecord = { focusedRecord = it }
                )
                Surface(
                    color = Color(0xDD0B192C),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Bản đồ tọa độ nội bộ • Thêm MAPS_API_KEY vào Secrets để bật Google Maps nền vệ tinh/đường phố.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White
                        )
                    }
                }
            }
        }

        if (visibleRecords.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(visibleRecords, key = { it.id.ifBlank { "${it.carrier}_${it.stationCode}_${it.province}" } }) { rec ->
                    val isSelected = focusedRecord?.stationCode == rec.stationCode && focusedRecord?.province == rec.province
                    Card(
                        modifier = Modifier
                            .width(220.dp)
                            .clickable {
                                focusedRecord = rec
                                if (hasValidMapsKey) {
                                    scope.launch {
                                        cameraPositionState.animate(
                                            CameraUpdateFactory.newLatLngZoom(
                                                LatLng(rec.latitude, rec.longitude),
                                                10f
                                            )
                                        )
                                    }
                                }
                            },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(CarrierColors.colorFor(rec.carrier))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${rec.carrier} • ${rec.province}",
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "${rec.stationCode} • ${formatCompactSubs(rec.subscribers)} TB",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Phủ 5G: ${String.format(Locale.US, "%.1f%%", rec.coverage5gPercent)} • ${String.format(Locale.US, "%.2f, %.2f", rec.latitude, rec.longitude)}",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Chưa có điểm dữ liệu tọa độ cho bộ lọc này.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun InteractiveVietnamTopologyMap(
    records: List<TelecomRecord>,
    focusedRecord: TelecomRecord?,
    onSelectRecord: (TelecomRecord) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0A192F), Color(0xFF102A43), Color(0xFF0B253A))
                )
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val gridStepX = size.width / 6f
            val gridStepY = size.height / 8f
            val gridColor = Color.White.copy(alpha = 0.08f)
            for (i in 1..5) {
                drawLine(
                    color = gridColor,
                    start = Offset(gridStepX * i, 0f),
                    end = Offset(gridStepX * i, size.height),
                    strokeWidth = 1.5f
                )
            }
            for (j in 1..7) {
                drawLine(
                    color = gridColor,
                    start = Offset(0f, gridStepY * j),
                    end = Offset(size.width, gridStepY * j),
                    strokeWidth = 1.5f
                )
            }

            // Plot Vietnam lat [8.5..23.5], lng [102.0..110.0]
            val minLat = 8.2
            val maxLat = 23.8
            val minLng = 102.0
            val maxLng = 110.2
            val padX = size.width * 0.14f
            val padY = size.height * 0.16f
            val plotW = size.width - padX * 2
            val plotH = size.height - padY * 2

            fun toScreen(lat: Double, lng: Double): Offset {
                val nx = ((lng - minLng) / (maxLng - minLng)).toFloat().coerceIn(0f, 1f)
                val ny = (1.0 - (lat - minLat) / (maxLat - minLat)).toFloat().coerceIn(0f, 1f)
                return Offset(padX + nx * plotW, padY + ny * plotH)
            }

            // Connect consecutive nodes lightly to show backbone links
            if (records.size >= 2) {
                for (i in 0 until records.size - 1) {
                    val p1 = toScreen(records[i].latitude, records[i].longitude)
                    val p2 = toScreen(records[i + 1].latitude, records[i + 1].longitude)
                    drawLine(
                        color = Color(0xFF38BDF8).copy(alpha = 0.25f),
                        start = p1,
                        end = p2,
                        strokeWidth = 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                    )
                }
            }

            records.forEach { rec ->
                val center = toScreen(rec.latitude, rec.longitude)
                val color = CarrierColors.colorFor(rec.carrier)
                val isSelected = focusedRecord?.stationCode == rec.stationCode && focusedRecord?.province == rec.province
                drawCircle(
                    color = color.copy(alpha = if (isSelected) 0.35f else 0.18f),
                    radius = if (isSelected) 38f else 24f,
                    center = center
                )
                drawCircle(
                    color = color,
                    radius = if (isSelected) 14f else 10f,
                    center = center
                )
                drawCircle(
                    color = Color.White,
                    radius = if (isSelected) 5f else 3f,
                    center = center
                )
            }
        }

        focusedRecord?.let { rec ->
            Surface(
                color = Color(0xE60F2038),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(14.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "${rec.carrier} • ${rec.province} (${rec.region})",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White
                    )
                    Text(
                        text = "Trạm ${rec.stationCode} • Phủ sóng 5G: ${rec.coverage5gPercent}% • Gói ${rec.popularPackage}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFBAE6FD)
                    )
                }
            }
        }
    }
}
