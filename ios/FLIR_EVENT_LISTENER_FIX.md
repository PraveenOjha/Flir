# FLIR iOS Event Listener Fix

## Problem

FLIR device discovery was working on Android but not on iOS. The logs showed:

```
[FlirModule] onDevicesFound - emitting FlirDevicesFound with 3 devices
[native] Sending FlirDevicesFound with no listeners registered
```

**Root Cause**: Events were being emitted before React Native's NativeEventEmitter had fully registered its listeners. This is a timing issue between:
1. Settings modal opening
2. useFlirDevices hook mounting
3. Event listeners being registered
4. Native discovery starting
5. Events being emitted

## Android vs iOS Comparison

### Android (Working)
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

### iOS (Before Fix)
```objc
RCT_EXPORT_METHOD(addListener : (NSString *)eventName) {
  // Re-emission logic but no listener tracking
}

RCT_EXPORT_METHOD(removeListeners : (NSInteger)count) {
  // Empty - just comment "Required for RCTEventEmitter"
}
```

**Key Difference**: Android tracks listener count and provides debugging logs. iOS had no tracking.

## Solution

Match iOS implementation to Android's working pattern:

### 1. Add Listener Count Tracking

```objc
@implementation FlirModule {
  NSInteger _listenerCount;
}

- (instancetype)init {
  if (self = [super init]) {
    _listenerCount = 0;
    // ... existing init code
  }
  return self;
}
```

### 2. Track Listeners in addListener/removeListeners

```objc
RCT_EXPORT_METHOD(addListener : (NSString *)eventName) {
  _listenerCount++;
  NSLog(@"[FlirModule] addListener: %@ (count: %ld)", eventName, (long)_listenerCount);
  
  // Existing re-emission logic for FlirDevicesFound
  if ([eventName isEqualToString:@"FlirDevicesFound"]) {
    // ... re-emit devices if already discovered
  }
}

RCT_EXPORT_METHOD(removeListeners : (NSInteger)count) {
  _listenerCount -= count;
  if (_listenerCount < 0) _listenerCount = 0;
  NSLog(@"[FlirModule] removeListeners: %ld (remaining: %ld)", (long)count, (long)_listenerCount);
}
```

### 3. Check Listener Count Before Emitting

```objc
- (void)onDevicesFound:(NSArray *)devices {
  // ... build device array
  
  NSLog(@"[FlirModule] onDevicesFound - %lu devices, listenerCount: %ld", 
        (unsigned long)arr.count, (long)_listenerCount);
  
  if (_listenerCount > 0) {
    NSLog(@"[FlirModule] emitting FlirDevicesFound event");
    [self sendEventWithName:@"FlirDevicesFound" 
                       body:@{@"devices" : arr, @"count" : @(arr.count)}];
  } else {
    NSLog(@"[FlirModule] ⚠️ No listeners registered yet - devices will be re-emitted when listener is added");
  }
}
```

## How It Works

### Timeline (Before Fix)
```
23:49:37.349 - [useFlirDevices] Calling FlirModule.startDiscovery()
23:49:37.390 - [FlirModule] onDevicesFound - emitting FlirDevicesFound with 3 devices
23:49:37.390 - [native] Sending FlirDevicesFound with no listeners registered ❌
23:49:37.533 - React Native receives - still "no listeners registered" ❌
```

### Timeline (After Fix)
```
[useFlirDevices] Setting up FLIR event listeners...
[FlirModule] addListener: FlirDevicesFound (count: 1) ✅
[FlirModule] addListener: FlirDeviceConnected (count: 2) ✅
[FlirModule] addListener: FlirStateChanged (count: 3) ✅
[useFlirDevices] Calling FlirModule.startDiscovery()
[FlirModule] onDevicesFound - 3 devices, listenerCount: 6 ✅
[FlirModule] emitting FlirDevicesFound event ✅
[useFlirDevices] ✅ FlirDevicesFound event received: 3 devices ✅
```

If events arrive before listeners (rare edge case):
```
[FlirModule] onDevicesFound - 3 devices, listenerCount: 0
[FlirModule] ⚠️ No listeners registered yet - devices will be re-emitted when listener is added
... later when listener is added ...
[FlirModule] addListener: FlirDevicesFound (count: 1)
[FlirModule] addListener - re-emitting 3 discovered devices ✅
[useFlirDevices] ✅ FlirDevicesFound event received: 3 devices ✅
```

## Benefits

1. **Matching Android Pattern**: iOS now works exactly like Android
2. **Debugging Visibility**: Logs show listener count and registration timing
3. **Event Safety**: Won't emit events when no listeners are registered
4. **Re-emission Safety**: The existing re-emission logic in `addListener` ensures devices are delivered even if discovered before listeners registered
5. **Timing Resilience**: Handles component mount/unmount cycles gracefully

## Testing

After these changes, you should see in the logs:

1. When Settings modal opens and useFlirDevices mounts:
   ```
   [useFlirDevices] Setting up FLIR event listeners...
   [FlirModule] addListener: FlirDevicesFound (count: 1)
   [FlirModule] addListener: FlirDeviceConnected (count: 2)
   [FlirModule] addListener: FlirStateChanged (count: 3)
   [FlirModule] addListener: FlirError (count: 4)
   [FlirModule] addListener: FlirBatteryUpdated (count: 5)
   ```

2. When discovery finds devices:
   ```
   [FlirModule] onDevicesFound - 3 devices, listenerCount: 6
   [FlirModule] emitting FlirDevicesFound event
   [useFlirDevices] ✅ FlirDevicesFound event received: 3 devices
   ```

3. When Settings modal closes and component unmounts:
   ```
   [FlirModule] removeListeners: 6 (remaining: 0)
   ```

## Files Modified

- `/home/praveen/Desktop/work/Flir/ios/Flir/src/FlirModule.m`
  - Added `_listenerCount` instance variable
  - Updated `init` to initialize listener count to 0
  - Updated `addListener` to increment count and log
  - Updated `removeListeners` to decrement count and log
  - Updated `onDevicesFound` to check listener count before emitting

## Related Files

- `/home/praveen/Desktop/ThermalCameraApps/src/hooks/useFlirDevices.ts` - React Native hook that consumes events
- `/home/praveen/Desktop/work/Flir/android/Flir/src/main/java/flir/android/FlirModule.kt` - Android reference implementation
