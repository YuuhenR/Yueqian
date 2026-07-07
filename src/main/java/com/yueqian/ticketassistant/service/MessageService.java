package com.yueqian.ticketassistant.service;

import com.yueqian.ticketassistant.entity.ChatMessage;

import java.util.List;

public interface MessageService {
    ChatMessage save(Long sessionId, String userId, String role, String content, String toolName);
    List<ChatMessage> list(Long sessionId, String userId);
    long count();
    long count(String userId);
}
