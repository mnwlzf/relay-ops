package com.relayops.integration.sub2db;

/**
 * Sub2API 只读库未启用、配置不完整或处于失败冷却期时抛出。
 */
public class Sub2ReadOnlyUnavailableException extends RuntimeException {

    public Sub2ReadOnlyUnavailableException(String message) {
        super(message);
    }

    public Sub2ReadOnlyUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}