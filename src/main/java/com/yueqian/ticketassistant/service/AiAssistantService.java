package com.yueqian.ticketassistant.service;

public interface AiAssistantService {
    AiAnswer answer(String userId, Long sessionId, String message);

    record AiAnswer(String text, String toolName) {
    }
}
