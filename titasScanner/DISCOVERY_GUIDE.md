# Titas Scanner App - Complete Guide

## 🎯 What This App Does

This is a **diagnostic tool** that scans your Titas Gas FeliCa card and shows YOU all the data so we can figure out where the balance is stored.

## 📋 How We'll Discover the Balance Location

### The Scientific Method:

1. **Baseline Scan**: 
   - Know your current balance (e.g., "I have 25 m³")
   - Scan your card
   - Look for "25" or "25.00" in the results

2. **Transfer Test**:
   - Scan card (shows X m³)
   - Tap card on meter (transfers all gas)
   - Scan card again (should show 0 m³)
   - **Compare before/after** → Different value = balance location!

3. **Recharge Test**:
   - Start with empty card (0 m³)
   - Recharge 500 Taka (= 25 m³ at 20 Taka/m³ rate)
   - Scan card
   - Look for "25.00" appearing in new location

4. **Pattern Matching**:
   - The scanner tries to interpret each block:
     - As Float (e.g., 25.00 m³)
     - As Integer (e.g., 25 or 2500)
     - As ASCII text
   - It highlights likely candidates

## 📊 What You'll See

```
┌──────────────────────────────────────┐
│  Service Code: 0x130F                │ ← Titas service (hypothesis)
│  ────────────────────────────────    │
│  Block 0:                            │
│  00 00 00 00 00 00 00 00 ...        │
│  → Unknown                           │
│                                      │
│  Block 4:                            │
│  00 00 C8 41 12 34 56 78 ...        │
│  → Float[0-3]=25.00 m³? ✓✓✓        │ ← THIS IS IT!
│  → Int32[4-7]=1450744626            │
│                                      │
│  Block 5:                            │
│  0A 00 00 00 ...                    │
│  → Int32[0-3]=10                    │ ← Recharge count?
└──────────────────────────────────────┘
```

## 🔍 Interpretation Guide

### Float Values
```
Bytes: 00 00 C8 41
Interpretation: Float[0-3]=25.00 m³

How to verify:
- Does 25.00 match your known balance? ✓
- Java's Float.intBitsToFloat(0x41C80000) = 25.0
```

### Integer Values
```
Bytes: E8 03 00 00
Interpretation: Int32[0-3]=1000

Might be:
- 1000 Taka (if storing in Taka)
- 1000 liters (if storing in liters)
- Counter (usage count)
```

### Timestamps
```
Bytes: 01 A4 48
Interpretation: Timestamp?[4-6]=0x01A448

Might be encoded date (like MRT does)
```

## 🎨 Visual Debugging Workflow

### Step-by-Step Process:

**Phase 1: Initial Discovery**
```
1. Open Titas Scanner app
2. Tap your Titas card
3. Screenshot the results
4. Note which service codes were found
   (Looking for anything OTHER than 0x220F)
```

**Phase 2: Balance Identification**
```
1. Check your current balance on meter (e.g., "15 m³")
2. Scan card with scanner
3. Look for "15" or "15.00" in Float/Int fields
4. Mark potential candidates
```

**Phase 3: Confirmation**
```
1. Recharge 500 Taka (adds ~25 m³)
2. Scan BEFORE tapping on meter
3. Should show old balance + 25 m³
4. Tap on meter (transfers to meter)
5. Scan AFTER
6. Should show 0 m³
7. The field that changed = BALANCE! ✓
```

## 📱 Logcat Debugging (Advanced)

Connect phone via USB and run:
```bash
adb logcat -s FeliCaScanner:D
```

You'll see detailed output like:
```
FeliCaScanner: === FELICA CARD DETECTED ===
FeliCaScanner: Card ID: 01 23 45 67 89 AB CD EF
FeliCaScanner: Manufacturer: 01 20
FeliCaScanner: System Code: FE 00
FeliCaScanner: Trying service code: 0x220F
FeliCaScanner: ✓ Service 0x220F is active!
FeliCaScanner: Trying service code: 0x130F
FeliCaScanner: ✓ Service 0x130F is active!
FeliCaScanner: Block 4: 00 00 C8 41 1A 2B 3C 4D
FeliCaScanner:   → Float[0-3]=25.00 m³?
```

## 🧪 Test Scenarios

### Scenario 1: Empty Card
```
Known state: 0 m³ on card
Expected result: All blocks show zeros or very small values
```

### Scenario 2: After Recharge (Before Transfer)
```
Known state: Just recharged 500 Taka (25 m³)
Expected result: One block shows 25.00 or 2500
```

### Scenario 3: After Transfer to Meter
```
Known state: Just tapped on meter
Expected result: Balance block now shows 0
```

## 🎯 Success Criteria

You've successfully identified the balance when:
1. ✅ You find a service code (e.g., 0x130F)
2. ✅ You find a block number (e.g., Block 4)
3. ✅ Value matches your known balance
4. ✅ Value changes when you recharge
5. ✅ Value becomes 0 after transferring to meter

## 📤 Sharing Results

Once you've scanned, share:
1. Screenshot of the app
2. Your known balance at time of scan
3. Which interpretation matched
4. Logcat output (if USB connected)

Example report:
```
Known balance: 25 m³
Service found: 0x130F
Block: 4
Data: 00 00 C8 41 ...
Interpretation: Float[0-3]=25.00 m³
Confirmed: ✓ (matched after recharge test)
```

## 🚀 Next Steps After Discovery

Once we know:
- Service Code: `0x130F`
- Block Number: `4`
- Data Type: `Float (little-endian)`
- Offset: `0-3 bytes`

I can:
1. Add proper Titas reading to MRT Buddy
2. Show gas balance alongside MRT balance
3. Track recharge history
4. Alert when low balance
5. Calculate approximate usage

## 💡 Expected Timeline

- **Build & Install**: 2 minutes
- **First Scan**: 30 seconds
- **Identification**: 5-15 minutes (with recharge test)
- **Integration to MRT Buddy**: 30 minutes

Total: ~20 minutes of testing to discover the format!

---

**Remember**: This is a one-time diagnostic. Once we find the format, MRT Buddy will read it automatically! 🎉
