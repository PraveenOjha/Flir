package com.flir.thermalsdk.live.remote;

public interface OnReceived<T> {
    void onReceived(T data);
}
