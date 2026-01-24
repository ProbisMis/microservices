package com.demo.programming.exceptions;

import com.demo.programming.exceptions.base.BaseException;
import org.springframework.http.HttpStatus;

public class DuplicateResourceException extends BaseException {

    private final String fieldName;
    private final Object fieldValue;

    public DuplicateResourceException(String resourceName, String fieldName, Object fieldValue) {
        super(
                String.format("%s already exists with %s: %s", resourceName, fieldName, fieldValue),
                "DUPLICATE_RESOURCE",
                HttpStatus.CONFLICT,
                resourceName
        );
        this.fieldName = fieldName;
        this.fieldValue = fieldValue;
    }

    public String getFieldName() {
        return fieldName;
    }

    public Object getFieldValue() {
        return fieldValue;
    }
}
