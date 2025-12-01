package com.flir.thermalsdk.androidsdk.image;

import android.graphics.Bitmap;
import com.flir.thermalsdk.image.JavaImageBuffer;

/**
 * Stub class for FLIR SDK BitmapAndroid - compile-time only.
 * The actual implementation is loaded at runtime from the FLIR SDK AAR.
 */
public class BitmapAndroid {
    private Bitmap bitMap;
    
    public static BitmapAndroid createBitmap(JavaImageBuffer buffer) {
        throw new UnsupportedOperationException("Stub!");
    }
    
    public Bitmap getBitMap() {
        throw new UnsupportedOperationException("Stub!");
    }
}
