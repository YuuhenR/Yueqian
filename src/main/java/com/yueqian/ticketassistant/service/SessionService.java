package com.yueqian.ticketassistant.service;

import com.yueqian.ticketassistant.entity.ChatSession;

import java.util.List;

public interface SessionService {
    ChatSession create(String userId, String title);
    List<ChatSession> list(String userId);
    ChatSession rename(String userId, Long id, String title);
    void delete(String userId, Long id);
}
