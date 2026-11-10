package com.example.demo.service;

import com.example.demo.entity.TelegramChannel;
import com.example.demo.entity.User;
import com.example.demo.repository.TelegramChannelRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class ChannelService {

    private final TelegramChannelRepository repo;

    public ChannelService(TelegramChannelRepository repo) {
        this.repo = repo;
    }

    public Optional<TelegramChannel> findByTeacher(User teacher) {
        return repo.findByTeacher(teacher);
    }

    /** @return false bo'lsa, kanal boshqa ustozga biriktirilgan */
    public boolean isTakenByAnother(Long chatId, User teacher) {
        return repo.findByChatId(chatId)
                .map(c -> !c.getTeacher().getId().equals(teacher.getId()))
                .orElse(false);
    }

    public TelegramChannel link(User teacher, Long chatId, String title, String username) {
        TelegramChannel ch = repo.findByTeacher(teacher).orElseGet(TelegramChannel::new);
        ch.setTeacher(teacher);
        ch.setChatId(chatId);
        ch.setChannelName(title);
        ch.setChannelUsername(username);
        return repo.save(ch);
    }
}
