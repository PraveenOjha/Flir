# Android vs iOS FLIR Implementation Comparison

## Key Discrepancies Fixed

### 1. ✅ Listener Count Tracking (FIXED in FlirModule.m)

**Android:**
```kotlin
private var listenerCount = 0

@ReactMethod
fun addListener(eventName: String) {
    listenerCount++
    Log.d(TAG, "addListener: $eventName (count: $listenerCount)")
}

@ReactMethod
fun removeListeners(count: Int) {
    listenerCount -= count
    if (listenerCount < 0) listenerCount = 0
    Log.d(TAG, "removeListeners: $count (remaining: $listenerCount)")
}
```

**iOS (NOW FIXED):**
```objc
@implementation FlirModule {
  NSInteger _listenerCount;
}

RCT_EXPORT_METHOD(addListener : (NSString *)eventName) {
  _listenerCount++;
  NSLog(@"[FlirModule] addListener: %@ (count: %ld)", eventName, (long)_listenerCount);
  // ... re-emission logic
}

RCT_EXPORT_METHOD(removeListeners : (NSInteger)count) {
  _listenerCount -= count;
  if (_listenerCount < 0) _listenerCount = 0;
  NSLog(@"[FlirModule] removeListeners: %ld (remaining: %ld)", (long)count, (long)_listenerCount);
}
```

### 2. ✅ Event Emission Safety (FIXED in FlirModule.m)

**Android:** Checks `reactContext` before emitting
```kotlin
private fun emitDevicesFound(devices: List<Identity>) {
    val ctx = reactContext
    if (ctx == null) {
        Log.e(TAG, "Cannot emit FlirDevicesFound - reactContext is null!")
        return
    }
    // ... emit event
}
```

**iOS (NOW FIXED):**
```objc
- (void)onDevicesFound:(NSArray *)devices {
  // ... build device array
  
  if (_listenerCount > 0) {
    NSLog(@"[FlirModule] emitting FlirDevicesFound event");
    [self sendEventWithName:@"FlirDevicesFound" body:@{...}];
  } else {
    NSLog(@"[FlirModule] ⚠️ No listeners registered yet - devices will be re-emitted when listener is added");
  }
}
```

### 3. State Emission on Discovery

**Android:** Emits "discovering" state when discovery starts
```kotlin
fun startDiscovery(retry: Boolean = false) {
    Log.i(TAG, "[Flir-BRIDGE-DISCOVERY] startDiscovery(retry=$retry)")
    isScanning = true
    emitDeviceState("discovering")  // ← IMPORTANT
    sdkManager?.scan()
}
```

**iOS:** Needs to add this - currently missing

### 4. Auto-Start Stream on Connection

**Android:** Automatically starts streaming when connected
```kotlin
override fun onConnected(identity: Identity?) {
    Log.i(TAG, "Connected to: ${identity?.deviceId}")
    isConnected = true
    emitDeviceState("connected")
    startStream()  // ← AUTO-START
}
```

**iOS:** Already has this in `performConnection`, finds and starts stream

### 5. Connection State Logging

**Android:** Detailed bridge logging at every step
```kotlin
Log.i(TAG, "[Flir-BRIDGE-CONNECTION] Connecting to found device: $deviceId")
Log.i(TAG, "[Flir-BRIDGE-DISCONNECT] Disconnected callback")
Log.d(TAG, "[Flir-BRIDGE-STREAMING] stopStream")
```

**iOS:** Uses `FlirLogger` but needs to match Android's exact format for consistency

### 6. Device List Updates

**Android:** Logs each discovered device
```kotlin
override fun onDeviceListUpdated(devices: List<Identity>) {
    Log.i(TAG, "Devices updated: ${devices.size} found")
    devices.forEach { 
        Log.d(TAG, "  - ${it.deviceId} (${it.communicationInterface})")
    }
    emitDevicesFound(devices)
}
```

**iOS:** `cameraDiscovered` logs individual devices but doesn't log the full list update

### 7. State Event Fields

**Android - FlirDeviceConnected:**
```kotlin
val params = Arguments.createMap().apply {
    putString("state", state)           // "connected", "disconnected", "discovering"
    putBoolean("isConnected", isConnected)
    putBoolean("isStreaming", isStreaming)
    putBoolean("isEmulator", isEmulator())
    connectedDeviceName?.let { putString("deviceName", it) }
    connectedDeviceId?.let { putString("deviceId", it) }
}
ctx.emit("FlirDeviceConnected", params)
```

**iOS - onDeviceConnected:**
Currently sends device info only - needs to match Android's state format

## Remaining Differences to Address

### A. FlirModule Methods

Both platforms should expose the same React Native methods. Check:
- ✅ `startDiscovery()` - both have
- ✅ `stopDiscovery()` - both have  
- ✅ `connectToDevice(deviceId)` - both have
- ✅ `startEmulator(type)` - both have
- ✅ `stopFlir()` - both have
- ✅ `getDiscoveredDevices()` - both have
- ✅ `isEmulator()` - both have
- ✅ `isDeviceConnected()` - both have
- ✅ `getBatteryLevel()` - both have
- ✅ `isBatteryCharging()` - both have
- ⚠️ `initializeSDK()` - Android has, iOS missing
- ⚠️ `getDebugInfo()` - Android has, iOS missing

### B. Event Names & Payloads

All events should have identical names and payload structures:

| Event | Android | iOS | Match? |
|-------|---------|-----|--------|
| FlirDevicesFound | ✅ {devices[], count} | ✅ {devices[], count} | ✅ |
| FlirDeviceConnected | ✅ {state, isConnected, isStreaming, isEmulator, deviceName, deviceId} | ⚠️ device info only | ❌ |
| FlirDeviceDisconnected | ✅ {} | ✅ {} | ✅ |
| FlirStateChanged | ✅ {state, isConnected, isStreaming, isEmulator} | ✅ {state, isConnected, isStreaming, isEmulator} | ✅ |
| FlirError | ✅ {error} | ✅ {error} | ✅ |
| FlirFrameReceived | ✅ {width, height, timestamp} | ✅ {width, height, timestamp} | ✅ |
| FlirBatteryUpdated | ✅ {level, isCharging} | ⚠️ not emitted | ❌ |

### C. Initialization

**Android:** Explicit init with React context
```kotlin
fun init(context: Context) {
    if (context is ReactContext) {
        reactContext = context
    }
    // ... initialize SDK
}
```

**iOS:** Uses Swift singleton, no explicit init from RN
- FlirManager is initialized on first access
- No way to inject React context
- Works through delegate pattern instead

## Summary of Fixes Applied

✅ **FIXED**: Listener count tracking in iOS FlirModule.m
✅ **FIXED**: Check listener count before emitting events
✅ **FIXED**: Added logging to match Android pattern
✅ **FIXED**: Re-emission logic when listener is added

## Remaining Tasks (Lower Priority)

1. Add `initializeSDK()` method to iOS (optional - already auto-initializes)
2. Add `getDebugInfo()` method to iOS for debugging
3. Ensure `FlirDeviceConnected` event matches Android's full state payload
4. Ensure battery update events are emitted on iOS (currently only polled)

## Testing Checklist

After fixes, verify:
- [x] Listener count logs appear on iOS
- [x] Events are not emitted with "no listeners" warning
- [ ] Discovery shows devices immediately like Android
- [ ] Device selection from Settings works
- [ ] Connection flow matches Android
- [ ] Battery updates work (if device has battery)
- [ ] Emulator mode works identically on both platforms
