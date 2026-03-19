package ru.zahaand.smarttaskbot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.zahaand.smarttaskbot.model.User;
import ru.zahaand.smarttaskbot.repository.UserRepository;

import java.time.LocalDateTime;

/**
 * Service for managing user data and registration.
 * Handles user persistence, registration status checks, and
 * retrieval of user-specific settings like timezones.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public void register(Long telegramUserId,
                         String username,
                         String timezone) {

        if (userRepository.existsById(telegramUserId)) {
            return;
        }

        User user = User.builder()
                .telegramUserId(telegramUserId)
                .username(username)
                .timezone(timezone)
                .createdAt(LocalDateTime.now())
                .build();

        userRepository.save(user);
        log.info("User registered: userId={}", telegramUserId);
    }

    public boolean isRegistered(Long telegramUserId) {
        return userRepository.existsById(telegramUserId);
    }

    public String getTimezone(Long telegramUserId) {
        return userRepository.findById(telegramUserId)
                .map(User::getTimezone)
                .orElseThrow(() -> {
                    log.error("Timezone lookup failed — user not found: userId={}", telegramUserId);
                    return new IllegalStateException("User not found: " + telegramUserId);
                });
    }
}
