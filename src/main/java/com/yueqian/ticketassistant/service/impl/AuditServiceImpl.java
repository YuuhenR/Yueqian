package com.yueqian.ticketassistant.service.impl;

import com.yueqian.ticketassistant.entity.AuditLog;
import com.yueqian.ticketassistant.mapper.AuditLogMapper;
import com.yueqian.ticketassistant.service.AuditService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AuditServiceImpl implements AuditService {

    private final AuditLogMapper auditLogMapper;

    public AuditServiceImpl(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    @Override
    public void record(String userId, String action, String detail, String ip) {
        AuditLog log = new AuditLog();
        log.setUserId(userId);
        log.setAction(action);
        log.setDetail(detail == null ? "" : detail.substring(0, Math.min(980, detail.length())));
        log.setIp(ip);
        log.setCreateTime(LocalDateTime.now());
        auditLogMapper.insert(log);
    }
}
