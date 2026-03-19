package ru.zahaand.smarttaskbot.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.zahaand.smarttaskbot.model.User;
import ru.zahaand.smarttaskbot.repository.UserRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    private static final Long USER_ID = 42L;

    private User buildUser() {
        return User.builder()
                .telegramUserId(USER_ID)
                .username("alice")
                .timezone("Europe/Moscow")
                .build();
    }

    @Nested
    class Register {

        @Test
        @DisplayName("saves new user when not yet registered")
        void savesNewUser() {
            when(userRepository.existsById(USER_ID)).thenReturn(false);

            userService.register(USER_ID, "alice", "Europe/Moscow");

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getTelegramUserId()).isEqualTo(USER_ID);
            assertThat(captor.getValue().getTimezone()).isEqualTo("Europe/Moscow");
        }

        @Test
        @DisplayName("does not save when user already registered (idempotent)")
        void doesNotSaveWhenAlreadyRegistered() {
            when(userRepository.existsById(USER_ID)).thenReturn(true);

            userService.register(USER_ID, "alice", "Europe/Moscow");

            verify(userRepository, never()).save(any());
        }
    }

    @Nested
    class IsRegistered {

        @Test
        @DisplayName("returns true for a registered user")
        void returnsTrueForRegisteredUser() {
            when(userRepository.existsById(USER_ID)).thenReturn(true);

            assertThat(userService.isRegistered(USER_ID)).isTrue();
        }

        @Test
        @DisplayName("returns false for an unknown user")
        void returnsFalseForUnknownUser() {
            when(userRepository.existsById(USER_ID)).thenReturn(false);

            assertThat(userService.isRegistered(USER_ID)).isFalse();
        }
    }

    @Nested
    class GetTimezone {

        @Test
        @DisplayName("returns timezone string for a registered user")
        void returnsTimezone() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(buildUser()));

            assertThat(userService.getTimezone(USER_ID)).isEqualTo("Europe/Moscow");
        }

        @Test
        @DisplayName("throws IllegalStateException when user not found")
        void throwsWhenUserNotFound() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getTimezone(USER_ID))
                    .isInstanceOf(IllegalStateException.class);
        }
    }
}
