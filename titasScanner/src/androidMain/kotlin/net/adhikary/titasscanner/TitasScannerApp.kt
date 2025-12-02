package net.adhikary.titasscanner

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun TitasScannerApp(
    scanResult: FeliCaScanner.CompleteScanResult?
) {
    var selectedTab by remember { mutableStateOf(0) }
    
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF4CAF50),
            secondary = Color(0xFF2196F3),
            background = Color(0xFF0A0E27),
            surface = Color(0xFF1A1F3A),
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            if (scanResult == null) {
                // Waiting state
                WaitingScreen()
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header with gradient
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(Color(0xFF1E88E5), Color(0xFF4CAF50))
                                )
                            )
                            .padding(24.dp)
                    ) {
                        Column {
                            Text(
                                text = "⛽ Titas Gas Scanner",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Card: ${scanResult.cardId.take(16)}...",
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.8f),
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    
                    // Tab Row
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("📊 Overview")}
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("🔬 Debug") }
                        )
                    }
                    
                    // Content
                    when (selectedTab) {
                        0 -> OverviewTab(scanResult)
                        1 -> DebugTab(scanResult)
                    }
                }
            }
        }
    }
}

@Composable
fun WaitingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF0A0E27), Color(0xFF1A1F3A))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                text = "💳",
                fontSize = 80.sp
            )
            Text(
                text = "Tap Your Titas Gas Card",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "Ready to scan...",
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun OverviewTab(scanResult: FeliCaScanner.CompleteScanResult) {
    val gasBalance = parseGasBalance(scanResult)
    val rechargeHistory = parseRechargeHistory(scanResult)
    val usageHistory = parseUsageHistory(scanResult)
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Gas Balance Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1E88E5)
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Current Gas Balance",
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${gasBalance} m³",
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (gasBalance > 0) "Ready to load on meter" else "Card is empty",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        }
        
        // Stats Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatsCard(
                    title = "Recharges",
                    value = "${rechargeHistory.size}",
                    icon = "🔄",
                    modifier = Modifier.weight(1f)
                )
                StatsCard(
                    title = "Usage Events",
                    value = "${usageHistory.size}",
                    icon = "⛽",
                    modifier = Modifier.weight(1f)
                )
            }
        }
        
        // Recharge History
        item {
            Text(
                text = "Recent Recharges",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
        
        items(rechargeHistory.take(5)) { recharge ->
            RechargeHistoryItem(recharge)
        }
        
        // Usage History
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Recent Usage",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
        
        items(usageHistory.take(5)) { usage ->
            UsageHistoryItem(usage)
        }
    }
}

@Composable
fun StatsCard(title: String, value: String, icon: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1F3A)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = icon, fontSize = 32.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = title,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun RechargeHistoryItem(recharge: RechargeEvent) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1F3A)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF4CAF50).copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "✓", fontSize = 24.sp, color = Color(0xFF4CAF50))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Meter Recharged",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = recharge.timestamp.format(DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a")),
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
fun UsageHistoryItem(usage: UsageEvent) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1F3A)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (usage.type == "OP") Color(0xFF2196F3).copy(alpha = 0.2f)
                        else Color(0xFFF44336).copy(alpha = 0.2f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = usage.type,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (usage.type == "OP") Color(0xFF2196F3) else Color(0xFFF44336)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (usage.type == "OP") "Recovery Leakage Check" else "Credit Blocking",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = usage.timestamp.format(DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a")),
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
fun DebugTab(scanResult: FeliCaScanner.CompleteScanResult) {
    val context = LocalContext.current
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Copy Button
        item {
            Button(
                onClick = {
                    val formatted = formatScanResultForCopy(scanResult)
                    copyToClipboard(context, formatted)
                    Toast.makeText(context, "✓ Copied!", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50)
                )
            ) {
                Text("📋 Copy All Raw Data", fontSize = 16.sp)
            }
        }
        
        // Card Info
        item {
            DebugInfoCard("Card Info", listOf(
                "ID" to scanResult.cardId,
                "Manufacturer" to scanResult.manufacturer,
                "System Code" to scanResult.systemCode
            ))
        }
        
        // Services
        items(scanResult.services) { service ->
            DebugServiceCard(service)
        }
    }
}

@Composable
fun DebugInfoCard(title: String, items: List<Pair<String, String>>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1F3A)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            items.forEach { (key, value) ->
                Row(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(
                        text = "$key: ",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )
                    Text(
                        text = value,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
fun DebugServiceCard(service: FeliCaScanner.ServiceScanResult) {
    var expanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1F3A)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Service ${service.serviceCode}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                    Text(
                        text = "${service.blocks.size} blocks",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Hide" else "Show", color = Color(0xFF4CAF50))
                }
            }
            
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    service.blocks.forEach { block ->
                        DebugBlockView(block)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun DebugBlockView(block: FeliCaScanner.BlockData) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0A0E27))
            .padding(12.dp)
    ) {
        Text(
            text = "Block ${block.blockNumber}",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = Color(0xFF4CAF50)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = block.hexData,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            color = Color.White.copy(alpha = 0.9f)
        )
        if (block.interpretation != "Unknown") {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "💡 ${block.interpretation}",
                fontSize = 10.sp,
                color = Color(0xFF2196F3),
                lineHeight = 14.sp
            )
        }
    }
}

// Parser functions
fun parseGasBalance(result: FeliCaScanner.CompleteScanResult): Int {
    val service = result.services.firstOrNull() ?: return 0
    val block5 = service.blocks.firstOrNull { it.blockNumber == 5 } ?: return 0
    return block5.rawBytes.getOrNull(6)?.toInt()?.and(0xFF) ?: 0
}

fun parseRechargeHistory(result: FeliCaScanner.CompleteScanResult): List<RechargeEvent> {
    val service = result.services.firstOrNull() ?: return emptyList()
    val events = mutableListOf<RechargeEvent>()
    
    for (blockNum in 11..13) {
        val block = service.blocks.firstOrNull { it.blockNumber == blockNum } ?: continue
        
        // Parse two 8-byte recharge events per block
        for (offset in listOf(0, 8)) {
            if (offset + 7 < block.rawBytes.size) {
                val bytes = block.rawBytes.sliceArray(offset until offset + 8)
                if (bytes[0] != 0.toByte()) {
                    val event = parseRechargeEvent(bytes)
                    if (event != null) events.add(event)
                }
            }
        }
    }
    
    return events
}

fun parseRechargeEvent(bytes: ByteArray): RechargeEvent? {
    return try {
        val year = 2000 + (bytes[0].toInt() and 0xFF)
        val month = bytes[1].toInt() and 0xFF
        val day = bytes[2].toInt() and 0xFF
        val hour = bytes[3].toInt() and 0xFF
        val minute = bytes[4].toInt() and 0xFF
        
        RechargeEvent(
            timestamp = LocalDateTime.of(year, month, day, hour, minute)
        )
    } catch (e: Exception) {
        null
    }
}

fun parseUsageHistory(result: FeliCaScanner.CompleteScanResult): List<UsageEvent> {
    val service = result.services.firstOrNull() ?: return emptyList()
    val events = mutableListOf<UsageEvent>()
    
    for (blockNum in 14..18) {
        val block = service.blocks.firstOrNull { it.blockNumber == blockNum } ?: continue
        
        for (offset in listOf(0, 8)) {
            if (offset + 7 < block.rawBytes.size) {
                val bytes = block.rawBytes.sliceArray(offset until offset + 8)
                if (bytes[0] != 0.toByte()) {
                    val event = parseUsageEvent(bytes)
                    if (event != null) events.add(event)
                }
            }
        }
    }
    
    return events
}

fun parseUsageEvent(bytes: ByteArray): UsageEvent? {
    return try {
        val year = 2000 + (bytes[0].toInt() and 0xFF)
        val month = bytes[1].toInt() and 0xFF
        val day = bytes[2].toInt() and 0xFF
        val hour = bytes[3].toInt() and 0xFF
        val minute = bytes[4].toInt() and 0xFF
        val type = if (bytes[7] == 0x50.toByte()) "OP" else "CR"
        
        UsageEvent(
            timestamp = LocalDateTime.of(year, month, day, hour, minute),
            type = type
        )
    } catch (e: Exception) {
        null
    }
}

data class RechargeEvent(val timestamp: LocalDateTime)
data class UsageEvent(val timestamp: LocalDateTime, val type: String)

fun formatScanResultForCopy(result: FeliCaScanner.CompleteScanResult): String {
    val sb = StringBuilder()
    sb.appendLine("=== TITAS GAS CARD SCAN ===")
    sb.appendLine("Card: ${result.cardId}")
    sb.appendLine("System: ${result.systemCode}")
    sb.appendLine("Gas Balance: ${parseGasBalance(result)} m³")
    sb.appendLine()
    result.services.forEach { service ->
        sb.appendLine("Service ${service.serviceCode}:")
        service.blocks.forEach { block ->
            sb.appendLine("  Block ${block.blockNumber}: ${block.hexData}")
        }
    }
    return sb.toString()
}

fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Titas Scan", text)
    clipboard.setPrimaryClip(clip)
}
