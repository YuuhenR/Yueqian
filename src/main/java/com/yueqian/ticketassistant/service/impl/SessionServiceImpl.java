package com.yueqian.ticketassistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yueqian.ticketassistant.entity.ChatSession;
import com.yueqian.ticketassistant.mapper.ChatSessionMapper;
import com.yueqian.ticketassistant.service.SessionService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SessionServiceImpl implements SessionService {

    private final ChatSessionMapper sessionMapper;

    public SessionServiceImpl(ChatSessionMapper sessionMapper) {
        this.sessionMapper = sessionMapper;
    }

    @Override
    public ChatSession create(String userId, String title) {
        ChatSession session = new ChatSession();
        session.setUserId(normalizeUser(userId));
        session.setTitle(StringUtils.hasText(title) ? title.trim() : "新的出行会话");
        session.setPinned(false);
        session.setDeleted(0);
        session.setCreateTime(LocalDateTime.now());
        session.setUpdateTime(LocalDateTime.now());
        sessionMapper.insert(session);
        return session;
    }

    @Override
    public List<ChatSession> list(String userId) {
        return sessionMapper.selectList(new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getUserId, normalizeUser(userId))
                .orderByDesc(ChatSession::getPinned)
                .orderByDesc(ChatSession::getUpdateTime));
    }

    @Override
    public ChatSession rename(String userId, Long id, String title) {
        ChatSession session = sessionMapper.selectOne(new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getId, id)
                .eq(ChatSession::getUserId, normalizeUser(userId)));
        if (session == null) {
            throw new IllegalArgumentException("会话不存在");
        }
        session.setTitle(title.trim());
        session.setUpdateTime(LocalDateTime.now());
        sessionMapper.updateById(session);
        return session;
    }

    @Override
    public void delete(String userId, Long id) {
        ChatSession session = sessionMapper.selectOne(new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getId, id)
                .eq(ChatSession::getUserId, normalizeUser(userId)));
        if (session == null) {
            throw new IllegalArgumentException("会话不存在");
        }
        sessionMapper.deleteById(id);
    }

    private String normalizeUser(String userId) {
        if (!StringUtils.hasText(userId)) {
            throw new IllegalArgumentException("缺少用户身份");
        }
        return userId.trim();
    }
}
