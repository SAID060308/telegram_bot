package com.example.demo.bot;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Foydalanuvchi hozir qaysi bosqichda ekanini eslab qoladi.
 * Hozircha xotirada saqlanadi (bot qayta ishga tushsa tozalanadi).
 */
@Service
public class StateService {

    public static class Session {
        public BotState state = BotState.NONE;
        // Vaqtinchalik ma'lumotlar (test nomi, fayl id, tanlangan imtihon va h.k.)
        public final Map<String, Object> data = new ConcurrentHashMap<>();
    }

    private final Map<Long, Session> sessions = new ConcurrentHashMap<>();

    public Session get(Long telegramId) {
        return sessions.computeIfAbsent(telegramId, id -> new Session());
    }

    public BotState getState(Long telegramId) {
        return get(telegramId).state;
    }

    public void setState(Long telegramId, BotState state) {
        get(telegramId).state = state;
    }

    public void put(Long telegramId, String key, Object value) {
        get(telegramId).data.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(Long telegramId, String key) {
        return (T) get(telegramId).data.get(key);
    }

    public void clear(Long telegramId) {
        sessions.remove(telegramId);
    }
}