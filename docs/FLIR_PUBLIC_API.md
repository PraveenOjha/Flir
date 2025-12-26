# FLIR Public API (iOS & RN)

✅ **Goal**: Provide a stable, object-oriented API for native consumers and RN to access frames as BGRA bitmaps and receive events.

## iOS (Swift / Objective-C) usage 🔧

Import the header:

Objective-C
```
#import <Flir/FlirPublic.h>

// Set delegate
[FlirManager.shared setDelegate:self];

- (void)onFrameReceivedRaw:(NSData *)data width:(NSInteger)w height:(NSInteger)h bytesPerRow:(NSInteger)bpr timestamp:(double)ts {
  // data is BGRA (kCGBitmapByteOrder32Little | kCGImageAlphaPremultipliedFirst)
}
```

Swift
```
import Flir

class MyDelegate: NSObject, FlirPublicDelegate {
  func onFrameReceived(_ image: UIImage, width: Int, height: Int) { /* UI preview */ }
  func onFrameReceivedRaw(_ data: Data, width: Int, height: Int, bytesPerRow: Int, timestamp: Double) {
    // data is BGRA bytes; build CGImage/CIImage if needed
  }
}

FlirManager.shared.delegate = myDelegate
FlirManager.shared.startDiscovery()
```

## React Native usage (JS) 📱

The RN module exposes a promise method `getLatestFrameBitmap()` returning an object:

```js
const { FlirModule } = NativeModules;

// Returns: { width, height, bytesPerRow, dataBase64 }
const bmp = await FlirModule.getLatestFrameBitmap();
if (bmp) {
  const { width, height, bytesPerRow, dataBase64 } = bmp;
  const bytes = atob(dataBase64); // base64 -> raw bytes in JS
}

// Events: subscribe to "FlirFrameBitmapAvailable" to be informed when a raw frame is available (metadata only)
```

## Notes & Tips 💡
- The BGRA format is `kCGBitmapByteOrder32Little | kCGImageAlphaPremultipliedFirst` (Bytes order: B,G,R,A).
- Avoid sending large raw frames frequently over JS—prefer `getLatestFrameBitmap()` on demand or rely on native delegate.
- The RN event `FlirFrameBitmapAvailable` contains only metadata (width/height/timestamp) to avoid heavy payloads.

---

If you'd like, I can add a small iOS sample file and a minimal Android API parity (Bitmap) next; which do you want me to implement first?