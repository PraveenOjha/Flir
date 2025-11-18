package flir.android;

import android.graphics.Bitmap;

public class FrameDataHolder {
    
    public final Bitmap msxBitmap;
    public final Bitmap dcBitmap;

    public FrameDataHolder(Bitmap msxBitmap, Bitmap dcBitmap) {
        this.msxBitmap = msxBitmap;
        this.dcBitmap = dcBitmap;
    }
}
