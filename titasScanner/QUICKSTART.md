# 🚀 Quick Start Guide - Titas Scanner

## Build the APK

```bash
cd "d:\Synched Files\mrt-buddy"
gradlew.bat :titasScanner:assembleDebug
```

APK location: `titasScanner\build\outputs\apk\debug\titasScanner-debug.apk`

## Install & Use

1. **Install APK** on your Android phone
2. **Enable NFC** in phone settings
3. **Open "Titas Scanner"** app
4. **Tap your Titas Gas card** on phone back
5. **View results** - scroll through all services and blocks

## What to Look For

### 🎯 Your Gas Balance

**If you have 25 m³ of gas:**

Look for these patterns:
- `Float[0-3]=25.00 m³?` ← Most likely!
- `Int24[0-2]=25`
- `Int32[0-3]=2500` (if stored in cents/liters)

### 🔍 Which Service?

- **Service 0x220F** = MRT Transit (ignore this)
- **Service 0x130F** = Likely Titas Gas
- **Service 0x118B** = Alternative utility service
- **Other codes** = Could be Titas

### 📍 Which Block?

- Usually Block 0-5 in the Titas service
- Compare with known balance to identify

## Testing Method

### The Definitive Test:

```
1. Note current balance: X m³
2. Scan card → See X somewhere
3. Tap card on meter → Transfers all gas
4. Scan card again → See 0 (empty card)
5. The value that changed from X to 0 = BALANCE ✓
```

### Alternative Test (if you can recharge):

```
1. Scan card → See old balance
2. Recharge 500 Taka (~25 m³)
3. Scan card again → Balance increased by 25
4. That's your balance field!
```

## Debug with ADB

```bash
# See detailed logs
adb logcat -s FeliCaScanner:D

# Clear old logs first
adb logcat -c
```

## Common Issues

**"NFC not available"**
→ Phone doesn't have NFC hardware

**"Please enable NFC"**  
→ Settings → Connected devices → NFC → ON

**"Not a FeliCa card!"**
→ You scanned wrong card (use Titas, not WASA)

**No services found**
→ Try scanning MRT card first to verify app works

## What to Report

Once you find it, report:
```
✅ Service Code: 0x130F
✅ Block Number: 4  
✅ Byte Range: 0-3
✅ Data Type: Float (little-endian)
✅ Known Balance: 25 m³
✅ Scanned Value: 25.00 m³
```

## Next Steps

After discovery → I integrate into MRT Buddy → You get:
- ✅ Read MRT balance
- ✅ Read Titas gas balance
- ✅ One app for both cards
- ✅ No more guessing!

---

**Time Required**: ~5-10 minutes to discover format  
**One-time process**: After finding format, no more scanning needed!
