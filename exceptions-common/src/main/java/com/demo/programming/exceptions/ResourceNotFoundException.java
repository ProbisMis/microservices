package com.demo.programming.exceptions;

import com.demo.programming.exceptions.base.BaseException;
import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends BaseException {

    private final String fieldName;
    private final Object fieldValue;

    public ResourceNotFoundException(String resourceName, String fieldName, Object fieldValue) {
        super(
                String.format("%s not found with %s: %s", resourceName, fieldName, fieldValue),
                "RESOURCE_NOT_FOUND",
                HttpStatus.NOT_FOUND,
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
