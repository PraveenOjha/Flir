package com.flir.thermalsdk.image;

public interface ImageBuffer {
    int getWidth();

    int getHeight();

    int getStride();

    byte[] getPixelBuffer();
}
