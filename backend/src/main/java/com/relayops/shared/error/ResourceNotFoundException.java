package com.relayops.shared.error;

public class ResourceNotFoundException extends BusinessException {

    public ResourceNotFoundException(String resource, Object id) {
        super("RESOURCE_NOT_FOUND", resource + " not found: " + id);
    }
}
