# Titas Scanner - FeliCa Diagnostic Tool

This is a minimal diagnostic app to discover how Titas Gas cards store balance information.

## Purpose

- Scan ALL FeliCa service codes to find which ones are active
- Read ALL blocks from each service
- Interpret block data to identify potential balance fields
- Help us discover the correct service code and block structure for Titas cards

## How to Build

```bash
cd "d:\Synched Files\mrt-buddy"
./gradlew :titasScanner:assembleDebug
```

The APK will be in: `titasScanner/build/outputs/apk/debug/titasScanner-debug.apk`

## How to Use

1. Install the APK on your phone
2. **Before scanning**: Note your current Titas gas balance (from the official app or meter)
3. Open "Titas Scanner" app
4. Tap your Titas Gas card on the phone
5. The app will show:
   - Card ID and manufacturer info
   - All active service codes found
   - All blocks within each service
   - Interpretations of what each block might contain

## How to Find the Balance

### Method 1: Check Logcat (Most Detailed)

Connect your phone via USB and run:
```bash
adb logcat | findstr "FeliCaScanner"
```

Look for lines like:
```
Block 4: 00 00 C8 41 ... → Float[0-3]=25.00 m³?
```

### Method 2: Visual Comparison

1. **Recharge first**: Add a known amount (e.g., 500 Taka = 25 m³)
2. **Scan BEFORE** loading to meter
3. **Load to meter** by tapping card on it
4. **Scan AFTER** loading
5. **Compare** the two scans:
   - Before: Should show 25 m³ somewhere
   - After: Should show 0 m³ (card emptied)

### Method 3: Pattern Recognition

Look for these patterns in the scan results:

**If you have 25 m³ on card:**
- `Float[0-3]=25.00 m³?` ← Most likely!
- `Int24[0-2]=25` ← Possible (if stored as integer)

**Common locations:**
- Service `0x130F`, Block 4
- Service `0x118B`, Block 0-2
- Any service OTHER than `0x220F` (that's MRT)

## What Each Field Means

### Card Information
- **ID**: Unique card identifier (like serial number)
- **Manufacturer**: Card manufacturer code
- **System Code**: FeliCa system type (usually transit/utility)

### Service Code
- **0x220F**: MRT transit service (if present)
- **0x130F, 0x118B, etc.**: Likely Titas gas service

### Block Data
- **Hex Data**: Raw bytes in hexadecimal
- **Possible interpretations**:
  - `Float[0-3]=X m³`: Might be cubic meters of gas
  - `Int24[0-2]=X`: Might be Taka or counter
  - `ASCII="..."`: Might be text (card number, name)
  - `Timestamp?`: Might be date/time

## Debugging Tips

### If no services found:
- Card might not be FeliCa (check with NFC Tools app)
- Try scanning your MRT card first (should show service 0x220F)

### If services found but blocks seem random:
- Note your EXACT balance before scanning
- Look for that number in Float or Int fields
- Compare multiple scans with different balances

### If multiple possible matches:
1. Recharge 100 Taka (5 m³)
2. Scan
3. Look for block showing 5.00
4. Load to meter
5. Scan again
6. That block should now show 0.00
7. **That's your balance block!**

## Expected Result

After scanning your Titas card, you should see something like:

```
Service Code: 0x130F
Block 4: 00 00 C8 41 1A 2B 3C 4D ...
  → Float[0-3]=25.00 m³? ← THIS IS IT!

Block 5: 0A 00 00 00 ...
  → Int32[0-3]=10 (Recharge count?)
```

## Next Steps

Once you identify:
1. **Service Code** (e.g., 0x130F)
2. **Block Number** (e.g., 4)
3. **Data Type** (e.g., Float, little-endian)

Share this info, and I'll integrate proper Titas support into MRT Buddy!

## App ID

- Package: `net.adhikary.titasscanner`
- Different from MRT Buddy (`net.adhikary.mrtbuddy`)
- Can coexist on same device

## Troubleshooting

**"NFC not available"**
- Your device doesn't support NFC

**"Please enable NFC"**
- Go to Settings → Connected devices → NFC → Enable

**"Not a FeliCa card!"**
- You scanned a MIFARE card (like WASA)
- Try again with Titas card

**No data appears**
- Check logcat for detailed errors
- Ensure card is tapped properly
- Try scanning multiple times
