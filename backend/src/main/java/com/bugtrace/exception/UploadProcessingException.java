package com.bugtrace.exception;

public class UploadProcessingException extends RuntimeException {

    public UploadProcessingException(String message) {
        super(message);
    }

    public UploadProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
