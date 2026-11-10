package com.example.demo.service;

import com.example.demo.entity.User;
import com.example.demo.enums.Role;
import com.example.demo.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Optional<User> find(Long telegramId) {
        return userRepository.findByTelegramId(telegramId);
    }

    public User register(org.telegram.telegrambots.meta.api.objects.User tgUser, Role role) {
        return userRepository.findByTelegramId(tgUser.getId()).orElseGet(() -> {
            User user = new User();
            user.setTelegramId(tgUser.getId());
            user.setUsername(tgUser.getUserName());
            user.setFirstName(tgUser.getFirstName());
            user.setLastName(tgUser.getLastName());
            user.setRole(role);
            return userRepository.save(user);
        });
    }
}