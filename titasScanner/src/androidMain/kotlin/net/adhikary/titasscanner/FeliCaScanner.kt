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
     * Request the actual service code list from the card (FeliCa Command 0x02)
     */
    private fun requestServiceCodeList(nfcF: NfcF): List<Int> {
        try {
            val idm = nfcF.tag.id
            val command = ByteArray(11)
            var idx = 0
            
            command[idx++] = 0x0B.toByte() // Length
            command[idx++] = 0x02.toByte() // Command: Request Service Code
            
            // Copy IDM
            idm.copyInto(destination = command, destinationOffset = idx)
            idx += idm.size
            
            command[idx++] = 0x01.toByte() // Number of nodes = 1 (request all)
            
            val response = nfcF.transceive(command)
            
            if (response.size < 13) return emptyList()
            
            // Parse service codes from response
            val numServices = response[10].toInt() and 0xFF
            val serviceCodes = mutableListOf<Int>()
            
            for (i in 0 until numServices) {
                val offset = 11 + (i * 2)
                if (offset + 1 < response.size) {
                    val serviceCode = ((response[offset + 1].toInt() and 0xFF) shl 8) or
                                     (response[offset].toInt() and 0xFF)
                    serviceCodes.add(serviceCode)
                    Log.d(TAG, "Card reports service code: 0x${serviceCode.toString(16).uppercase()}")
                }
            }
            
            return serviceCodes
        } catch (e: Exception) {
            Log.d(TAG, "Could not request service code list: ${e.message}")
            return emptyList()
        }
    }
    
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
            
            // First, try to request actual service codes from the card
            val reportedServices = requestServiceCodeList(nfcF)
            
            // Expanded list of service codes to try (including ones specific to system 0x92E4)
            val serviceCodesToTry = mutableListOf<Int>()
            
            // Add reported services first
            serviceCodesToTry.addAll(reportedServices)
            
            // Add common codes
            serviceCodesToTry.addAll(listOf(
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
                // Additional utility/gas service codes
                0x080B, // Utility variant
                0x088B, // Utility variant 2
                0x0F0B, // Utility variant 3
                0x170B, // Utility variant 4
                0x178B, // Utility variant 5
                // Try all possible low service codes (0x0000 - 0x00FF)
                0x0001, 0x0002, 0x0003, 0x0004, 0x0005, 0x0006, 0x0007, 0x0008,
                0x0009, 0x000A, 0x000B, 0x000C, 0x000D, 0x000E, 0x000F,
                // Try variations with high byte 0x01
                0x0100, 0x0101, 0x0102, 0x0103, 0x0108, 0x0109, 0x010A, 0x010B,
                0x010F, 0x0180, 0x0188, 0x018B, 0x018F,
            ))
            
            // Remove duplicates
            val uniqueServices = serviceCodesToTry.distinct()
            
            val results = mutableListOf<ServiceScanResult>()
            
            Log.d(TAG, "Trying ${uniqueServices.size} service codes...")
            
            for (serviceCode in uniqueServices) {
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
     * Enhanced to specifically look for 11 m³ (or any known balance)
     */
    private fun interpretBlock(block: ByteArray, blockNumber: Int): String {
        val interpretations = mutableListOf<String>()
        
        // Try EVERY byte offset for different data types
        for (offset in 0 until minOf(13, block.size - 3)) {
            // Little-endian float (most likely for gas volume)
            try {
                val floatLE = java.nio.ByteBuffer.wrap(block, offset, 4)
                    .order(java.nio.ByteOrder.LITTLE_ENDIAN).float
                if (floatLE in 0.0f..1000.0f && !floatLE.isNaN() && !floatLE.isInfinite()) {
                    val highlighted = if (floatLE in 10.5f..11.5f) "🎯 MATCH!" else ""
                    interpretations.add("Float-LE[$offset-${offset+3}]=${"%.2f".format(floatLE)} $highlighted")
                }
            } catch (e: Exception) {}
            
            // Big-endian float
            try {
                val floatBE = java.nio.ByteBuffer.wrap(block, offset, 4)
                    .order(java.nio.ByteOrder.BIG_ENDIAN).float
                if (floatBE in 0.0f..1000.0f && !floatBE.isNaN() && !floatBE.isInfinite()) {
                    val highlighted = if (floatBE in 10.5f..11.5f) "🎯 MATCH!" else ""
                    interpretations.add("Float-BE[$offset-${offset+3}]=${"%.2f".format(floatBE)} $highlighted")
                }
            } catch (e: Exception) {}
        }
        
        // Try integers at different offsets
        for (offset in 0 until minOf(14, block.size - 1)) {
            // 16-bit little-endian
            if (offset + 1 < block.size) {
                val int16LE = ((block[offset + 1].toInt() and 0xFF) shl 8) or
                             (block[offset].toInt() and 0xFF)
                if (int16LE in 1..1000) {
                    val highlighted = if (int16LE in 10..12) "🎯 MATCH!" else ""
                    interpretations.add("Int16-LE[$offset-${offset+1}]=$int16LE $highlighted")
                }
            }
            
            // 16-bit big-endian  
            if (offset + 1 < block.size) {
                val int16BE = ((block[offset].toInt() and 0xFF) shl 8) or
                             (block[offset + 1].toInt() and 0xFF)
                if (int16BE in 1..1000) {
                    val highlighted = if (int16BE in 10..12) "🎯 MATCH!" else ""
                    interpretations.add("Int16-BE[$offset-${offset+1}]=$int16BE $highlighted")
                }
            }
            
            // Single byte
            val singleByte = block[offset].toInt() and 0xFF
            if (singleByte in 1..100) {
                val highlighted = if (singleByte in 10..12) "🎯 MATCH!" else ""
                interpretations.add("Byte[$offset]=$singleByte $highlighted")
            }
        }
        
        // Try 24-bit and 32-bit at standard positions
        val int24LE = extractInt24(block, 0)
        val int32LE = extractInt32(block, 0)
        
        if (int24LE in 1..10000) {
            val highlighted = if (int24LE in 10..12) "🎯 MATCH!" else ""
            interpretations.add("Int24-LE[0-2]=$int24LE $highlighted")
        }
        
        if (int32LE in 1..100000) {
            val highlighted = if (int32LE in 10..12 || int32LE in 1100..1100) "🎯 MATCH!" else ""
            interpretations.add("Int32-LE[0-3]=$int32LE $highlighted")
        }

        // Try as BCD (Binary Coded Decimal) - common in utility meters
        try {
            for (offset in 0 until minOf(15, block.size)) {
                val bcd = block[offset].toInt() and 0xFF
                val tens = (bcd shr 4) and 0x0F
                val ones = bcd and 0x0F
                if (tens <= 9 && ones <= 9) {
                    val value = tens * 10 + ones
                    if (value in 1..99) {
                        val highlighted = if (value in 10..12) "🎯 MATCH!" else ""
                        interpretations.add("BCD[$offset]=$value $highlighted")
                    }
                }
            }
        } catch (e: Exception) {}
        
        // Try as ASCII string
        val ascii = block.filter { it in 32..126 }.map { it.toInt().toChar() }.joinToString("")
        if (ascii.length > 2) {
            val highlighted = if ("11" in ascii) "🎯 MATCH!" else ""
            interpretations.add("ASCII=\"$ascii\" $highlighted")
        }
        
        // Try as decimal string in bytes
        val decimalChars = block.filter { it in 48..57 }.map { (it - 48).toChar() }.joinToString("")
        if (decimalChars.length > 1) {
            val highlighted = if ("11" in decimalChars) "🎯 MATCH!" else ""
            interpretations.add("Decimal digits=\"$decimalChars\" $highlighted")
        }
        
        return if (interpretations.isEmpty()) {
            "Unknown"
        } else {
            interpretations.take(10).joinToString("; ") // Limit to top 10 to avoid clutter
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
