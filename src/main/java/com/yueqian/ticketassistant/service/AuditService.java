package com.yueqian.ticketassistant.service;

public interface AuditService {
    void record(String userId, String action, String detail, String ip);
}
