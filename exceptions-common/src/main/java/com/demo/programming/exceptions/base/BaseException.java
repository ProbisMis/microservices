package com.demo.programming.exceptions.base;

import org.springframework.http.HttpStatus;

public abstract class BaseException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus httpStatus;
    private final String resourceName;

    protected BaseException(String message, String errorCode, HttpStatus httpStatus, String resourceName) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.resourceName = resourceName;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getResourceName() {
        return resourceName;
    }
}
