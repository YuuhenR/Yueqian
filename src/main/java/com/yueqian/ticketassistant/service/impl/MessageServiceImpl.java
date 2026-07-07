package com.yueqian.ticketassistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yueqian.ticketassistant.entity.ChatMessage;
import com.yueqian.ticketassistant.mapper.ChatMessageMapper;
import com.yueqian.ticketassistant.service.MessageService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class MessageServiceImpl implements MessageService {

    private final ChatMessageMapper messageMapper;

    public MessageServiceImpl(ChatMessageMapper messageMapper) {
        this.messageMapper = messageMapper;
    }

    @Override
    public ChatMessage save(Long sessionId, String userId, String role, String content, String toolName) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("缺少用户身份");
        }
        message.setUserId(userId);
        message.setRole(role);
        message.setContent(content);
        message.setToolName(toolName);
        message.setCreateTime(LocalDateTime.now());
        messageMapper.insert(message);
        return message;
    }

    @Override
    public List<ChatMessage> list(Long sessionId, String userId) {
        return messageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId)
                .eq(userId != null && !userId.isBlank(), ChatMessage::getUserId, userId)
                .orderByAsc(ChatMessage::getCreateTime)
                .orderByAsc(ChatMessage::getId));
    }

    @Override
    public long count() {
        return messageMapper.selectCount(null);
    }

    @Override
    public long count(String userId) {
        return messageMapper.selectCount(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getUserId, userId));
    }
}
