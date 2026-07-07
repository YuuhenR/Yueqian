package com.yueqian.ticketassistant.service;

public interface SensitiveDataService {
    String encrypt(String plainText);
    String decrypt(String value);
    boolean matches(String plainText, String storedValue);
    String maskIdCard(String value);
}
