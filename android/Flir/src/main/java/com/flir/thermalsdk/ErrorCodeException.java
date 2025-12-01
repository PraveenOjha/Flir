package com.flir.thermalsdk;

public class ErrorCodeException extends Exception {
    private final ErrorCode errorCode;

    public ErrorCodeException(ErrorCode errorCode) {
        super(errorCode.toString());
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
