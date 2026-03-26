package ru.zahaand.smarttaskbot.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.zahaand.smarttaskbot.model.Language;
import ru.zahaand.smarttaskbot.model.User;
import ru.zahaand.smarttaskbot.model.UserState;
import ru.zahaand.smarttaskbot.repository.UserRepository;
import ru.zahaand.smarttaskbot.repository.UserStateRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserStateRepository userStateRepository;

    @InjectMocks
    private UserService userService;

    private static final Long USER_ID = 42L;

    private User buildUser() {
        return User.builder()
                .telegramUserId(USER_ID)
                .username("alice")
                .language(Language.EN)
                .timezone("Europe/Moscow")
                .build();
    }

    private User buildPartialUser() {
        return User.builder()
                .telegramUserId(USER_ID)
                .username("alice")
                .build(); // language and timezone null
    }

    @Nested
    class IsRegistered {

        @DisplayName("returns true when user has both language and timezone set")
        @Test
        void returnsTrueWhenFullyRegistered() {
            when(userRepository.findById(USER_ID))
                    .thenReturn(Optional.of(buildUser()));

            assertThat(userService.isRegistered(USER_ID)).isTrue();
        }

        @DisplayName("returns false when user has no language (mid-registration)")
        @Test
        void returnsFalseWhenLanguageNull() {
            when(userRepository.findById(USER_ID))
                    .thenReturn(Optional.of(buildPartialUser()));

            assertThat(userService.isRegistered(USER_ID)).isFalse();
        }

        @DisplayName("returns false for an unknown user")
        @Test
        void returnsFalseWhenUserNotFound() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

            assertThat(userService.isRegistered(USER_ID)).isFalse();
        }
    }

    @Nested
    class CreatePartialUser {

        @DisplayName("saves User and UserState(AWAITING_LANGUAGE) for new user")
        @Test
        void savesUserAndUserState() {
            when(userRepository.existsById(USER_ID)).thenReturn(false);

            userService.createPartialUser(USER_ID, "alice");

            verify(userRepository).save(any(User.class));
            verify(userStateRepository).save(any(UserState.class));
        }

        @DisplayName("skips creation when user already exists")
        @Test
        void skipsWhenAlreadyExists() {
            when(userRepository.existsById(USER_ID)).thenReturn(true);

            userService.createPartialUser(USER_ID, "alice");

            verify(userRepository, never()).save(any());
            verify(userStateRepository, never()).save(any());
        }
    }

    @Nested
    class GetTimezone {

        @DisplayName("returns timezone string for a registered user")
        @Test
        void returnsTimezone() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(buildUser()));

            assertThat(userService.getTimezone(USER_ID)).isEqualTo("Europe/Moscow");
        }

        @DisplayName("throws IllegalStateException when user not found")
        @Test
        void throwsWhenUserNotFound() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getTimezone(USER_ID))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    class DeleteUser {

        @DisplayName("calls deleteById with the given userId")
        @Test
        void callsDeleteById() {
            userService.deleteUser(USER_ID);

            verify(userRepository).deleteById(USER_ID);
        }

        @DisplayName("does not throw when user does not exist")
        @Test
        void doesNotThrowWhenAbsent() {
            doNothing().when(userRepository).deleteById(USER_ID);

            org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                    () -> userService.deleteUser(USER_ID));
        }
    }
}
