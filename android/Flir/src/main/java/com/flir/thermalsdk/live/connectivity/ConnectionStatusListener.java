package com.flir.thermalsdk.live.connectivity;

import com.flir.thermalsdk.ErrorCode;

public interface ConnectionStatusListener {
    void onDisconnected(ErrorCode errorCode);
}
