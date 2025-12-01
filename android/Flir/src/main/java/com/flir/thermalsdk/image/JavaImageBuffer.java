package com.flir.thermalsdk.image;

public class JavaImageBuffer implements ImageBuffer {
    public final byte[] pixelBuffer;
    public final int height;
    public final int width;
    public final int stride;

    public JavaImageBuffer(byte[] pixelBuffer, int width, int height, int stride) {
        this.pixelBuffer = pixelBuffer;
        this.width = width;
        this.height = height;
        this.stride = stride;
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public int getStride() {
        return stride;
    }

    @Override
    public byte[] getPixelBuffer() {
        return pixelBuffer;
    }
}
