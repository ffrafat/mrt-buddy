package net.adhikary.titasscanner

import android.app.Activity
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*

class MainActivity : ComponentActivity() {
    
    private var nfcAdapter: NfcAdapter? = null
    private val scanner = FeliCaScanner()
    private var scanResult = mutableStateOf<FeliCaScanner.CompleteScanResult?>(null)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize NFC
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        
        if (nfcAdapter == null) {
            Toast.makeText(this, "NFC not available on this device", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        
        if (!nfcAdapter!!.isEnabled) {
            Toast.makeText(this, "Please enable NFC in settings", Toast.LENGTH_LONG).show()
        }
        
        setContent {
            TitasScannerApp(scanResult = scanResult.value)
        }
        
        // Check if launched via NFC intent
        handleIntent(intent)
    }
    
    override fun onResume() {
        super.onResume()
        enableForegroundDispatch()
    }
    
    override fun onPause() {
        super.onPause()
        disableForegroundDispatch()
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }
    
    private fun enableForegroundDispatch() {
        nfcAdapter?.let { adapter ->
            val pendingIntent = android.app.PendingIntent.getActivity(
                this,
                0,
                Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                android.app.PendingIntent.FLAG_MUTABLE
            )
            
            adapter.enableForegroundDispatch(
                this,
                pendingIntent,
                null,
                null
            )
        }
    }
    
    private fun disableForegroundDispatch() {
        nfcAdapter?.disableForegroundDispatch(this)
    }
    
    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        
        when (intent.action) {
            NfcAdapter.ACTION_TECH_DISCOVERED,
            NfcAdapter.ACTION_TAG_DISCOVERED -> {
                val tag = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
                }
                
                tag?.let { scanCard(it) }
            }
        }
    }
    
    private fun scanCard(tag: Tag) {
        try {
            Toast.makeText(this, "Scanning card...", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "Starting comprehensive FeliCa scan...")
            
            // Scan all services
            val result = scanner.scanAllServices(tag)
            
            // Update UI
            scanResult.value = result
            
            Log.d(TAG, "Scan complete! Found ${result.services.size} services")
            
            // Log summary to Logcat for debugging
            Log.d(TAG, "=== SCAN SUMMARY ===")
            Log.d(TAG, "Card ID: ${result.cardId}")
            result.services.forEach { service ->
                Log.d(TAG, "Service ${service.serviceCode}: ${service.blocks.size} blocks")
                service.blocks.take(3).forEach { block ->
                    Log.d(TAG, "  Block ${block.blockNumber}: ${block.hexData}")
                    Log.d(TAG, "    → ${block.interpretation}")
                }
            }
            
            Toast.makeText(
                this,
                "Found ${result.services.size} service(s). Scroll to see data.",
                Toast.LENGTH_LONG
            ).show()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning card", e)
            Toast.makeText(
                this,
                "Error: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    
    companion object {
        private const val TAG = "TitasScanner"
    }
}
