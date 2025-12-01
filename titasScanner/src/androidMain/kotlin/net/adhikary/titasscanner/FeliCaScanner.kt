package net.adhikary.titasscanner

import android.nfc.Tag
import android.nfc.tech.NfcF
import android.util.Log

/**
 * FeliCa Scanner that discovers all service codes and reads all blocks
 * This helps us identify which service and blocks contain the Titas gas balance
 */
class FeliCaScanner {
    
    data class ServiceScanResult(
        val serviceCode: String,
        val serviceCodeHex: Int,
        val blocks: List<BlockData>,
        val error: String? = null
    )
    
    data class BlockData(
        val blockNumber: Int,
        val hexData: String,
        val rawBytes: ByteArray,
        val interpretation: String // Our guess at what this might be
    )
    
    data class CompleteScanResult(
        val cardId: String,
        val manufacturer: String,
        val systemCode: String,
        val services: List<ServiceScanResult>
    )
    
    /**
     * Scan ALL common FeliCa service codes to find which ones are active
     */
    fun scanAllServices(tag: Tag): CompleteScanResult {
        val nfcF = NfcF.get(tag) ?: throw Exception("Not a FeliCa card!")
        
        try {
            nfcF.connect()
            
            val cardId = toHexString(nfcF.tag.id)
            val manufacturer = toHexString(nfcF.manufacturer)
            val systemCode = toHexString(nfcF.systemCode)
            
            Log.d(TAG, "=== FELICA CARD DETECTED ===")
            Log.d(TAG, "Card ID: $cardId")
            Log.d(TAG, "Manufacturer: $manufacturer")
            Log.d(TAG, "System Code: $systemCode")
            
            // Common FeliCa service codes to try
            val serviceCodesToTry = listOf(
                0x220F, // Transit (MRT uses this)
                0x130F, // Utility services
                0x118B, // E-money/utility
                0x120F, // Transit variant
                0x090F, // Common service
                0x008B, // Basic service  
                0x100B, // Utility
                0x1A8B, // E-money
0x0F8B, // Alternative
                0x200F, // Transit variant
                0x2F0F, // Another variant
                0x1317, // Utility
                0x1387, // Alternative utility
            )
            
            val results = mutableListOf<ServiceScanResult>()
            
            for (serviceCode in serviceCodesToTry) {
                Log.d(TAG, "Trying service code: 0x${serviceCode.toString(16).uppercase()}")
                val result = tryReadService(nfcF, serviceCode)
                if (result != null) {
                    results.add(result)
                    Log.d(TAG, "✓ Service 0x${serviceCode.toString(16).uppercase()} is active!")
                }
            }
            
            nfcF.close()
            
            return CompleteScanResult(
                cardId = cardId,
                manufacturer = manufacturer,
                systemCode = systemCode,
                services = results
            )
            
        } catch (e: Exception) {
            nfcF.close()
            throw e
        }
    }
    
    /**
     * Try to read a specific service code and return its blocks
     */
    private fun tryReadService(nfcF: NfcF, serviceCode: Int): ServiceScanResult? {
        return try {
            // Try to read multiple blocks (0-19, like MRT does)
            val blocks = mutableListOf<BlockData>()
            
            // Read blocks in batches
            for (startBlock in listOf(0, 10)) {
                val command = generateReadCommand(
                    idm = nfcF.tag.id,
                    serviceCode = serviceCode,
                    startBlockNumber = startBlock,
                    numberOfBlocks = 10
                )
                
                val response = nfcF.transceive(command)
                
                // Check if response is valid (not an error)
                if (isValidResponse(response)) {
                    val parsedBlocks = parseBlocks(response, startBlock)
                    blocks.addAll(parsedBlocks)
                }
            }
            
            if (blocks.isNotEmpty()) {
                ServiceScanResult(
                    serviceCode = "0x${serviceCode.toString(16).uppercase()}",
                    serviceCodeHex = serviceCode,
                    blocks = blocks
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.d(TAG, "Service 0x${serviceCode.toString(16)} not available: ${e.message}")
            null
        }
    }
    
    /**
     * Generate FeliCa read command (same as MRT Buddy)
     */
    private fun generateReadCommand(
        idm: ByteArray,
        serviceCode: Int,
        numberOfBlocks: Int = 10,
        startBlockNumber: Int = 0
    ): ByteArray {
        val serviceCodeList = byteArrayOf(
            (serviceCode and 0xFF).toByte(),
            ((serviceCode shr 8) and 0xFF).toByte()
        )
        
        val blockListElements = ByteArray(numberOfBlocks * 2)
        for (i in 0 until numberOfBlocks) {
            blockListElements[i * 2] = 0x80.toByte()
            blockListElements[i * 2 + 1] = (startBlockNumber + i).toByte()
        }
        
        val commandLength = 14 + blockListElements.size
        val command = ByteArray(commandLength)
        var idx = 0
        
        command[idx++] = commandLength.toByte()
        command[idx++] = 0x06.toByte()
        
        idm.copyInto(destination = command, destinationOffset = idx)
        idx += idm.size
        
        command[idx++] = 0x01.toByte()
        command[idx++] = serviceCodeList[0]
        command[idx++] = serviceCodeList[1]
        command[idx++] = numberOfBlocks.toByte()
        
        blockListElements.copyInto(destination = command, destinationOffset = idx)
        
        return command
    }
    
    /**
     * Check if FeliCa response indicates success
     */
    private fun isValidResponse(response: ByteArray): Boolean {
        if (response.size < 13) return false
        
        // Check status flags (bytes 10 and 11)
        val statusFlag1 = response[10]
        val statusFlag2 = response[11]
        
        return statusFlag1 == 0x00.toByte() && statusFlag2 == 0x00.toByte()
    }
    
    /**
     * Parse blocks from FeliCa response
     */
    private fun parseBlocks(response: ByteArray, startBlockNumber: Int): List<BlockData> {
        val blocks = mutableListOf<BlockData>()
        
        if (response.size < 13) return blocks
        
        val numBlocks = response[12].toInt() and 0xFF
        val blockData = response.copyOfRange(13, response.size)
        
        val blockSize = 16
        if (blockData.size < numBlocks * blockSize) return blocks
        
        for (i in 0 until numBlocks) {
            val offset = i * blockSize
            val block = blockData.copyOfRange(offset, offset + blockSize)
            
            blocks.add(BlockData(
                blockNumber = startBlockNumber + i,
                hexData = toHexString(block),
                rawBytes = block,
                interpretation = interpretBlock(block, startBlockNumber + i)
            ))
        }
        
        return blocks
    }
    
    /**
     * Try to interpret what a block might contain
     */
    private fun interpretBlock(block: ByteArray, blockNumber: Int): String {
        val interpretations = mutableListOf<String>()
        
        // Try as little-endian integers
        val int24 = extractInt24(block, 0)
        val int32 = extractInt32(block, 0)
        
        // Check if values are in reasonable ranges
        if (int24 in 0..10000) {
            interpretations.add("Int24[0-2]=$int24 (might be Taka or m³)")
        }
        
        if (int32 in 0..100000) {
            interpretations.add("Int32[0-3]=$int32")
        }
        
        // Try as float
        try {
            val floatVal = java.nio.ByteBuffer.wrap(block).order(java.nio.ByteOrder.LITTLE_ENDIAN).float
            if (floatVal in 0.0f..1000.0f && !floatVal.isNaN() && !floatVal.isInfinite()) {
                interpretations.add("Float[0-3]=${"%.2f".format(floatVal)} m³?")
            }
        } catch (e: Exception) {}
        
        // Try as ASCII string
        val ascii = block.filter { it in 32..126 }.map { it.toInt().toChar() }.joinToString("")
        if (ascii.length > 3) {
            interpretations.add("ASCII=\"$ascii\"")
        }
        
        // Check for timestamp patterns (like MRT does)
        val timestamp24 = extractInt24BigEndian(block, 4)
        if (timestamp24 > 0) {
            interpretations.add("Timestamp?[4-6]=0x${timestamp24.toString(16)}")
        }
        
        return if (interpretations.isEmpty()) {
            "Unknown"
        } else {
            interpretations.joinToString("; ")
        }
    }
    
    // Utility functions
    private fun extractInt24(bytes: ByteArray, offset: Int): Int {
        if (offset + 2 >= bytes.size) return 0
        return ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
               ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
               (bytes[offset].toInt() and 0xFF)
    }
    
    private fun extractInt32(bytes: ByteArray, offset: Int): Int {
        if (offset + 3 >= bytes.size) return 0
        return ((bytes[offset + 3].toInt() and 0xFF) shl 24) or
               ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
               ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
               (bytes[offset].toInt() and 0xFF)
    }
    
    private fun extractInt24BigEndian(bytes: ByteArray, offset: Int): Int {
        if (offset + 2 >= bytes.size) return 0
        return ((bytes[offset].toInt() and 0xFF) shl 16) or
               ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
               (bytes[offset + 2].toInt() and 0xFF)
    }
    
    private fun toHexString(bytes: ByteArray): String =
        bytes.joinToString(" ") { "%02X".format(it) }
    
    companion object {
        private const val TAG = "FeliCaScanner"
    }
}
