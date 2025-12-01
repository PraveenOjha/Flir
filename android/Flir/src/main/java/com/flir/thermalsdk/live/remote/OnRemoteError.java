package com.flir.thermalsdk.live.remote;

import com.flir.thermalsdk.ErrorCode;

public interface OnRemoteError {
    void onRemoteError(ErrorCode error);
}
