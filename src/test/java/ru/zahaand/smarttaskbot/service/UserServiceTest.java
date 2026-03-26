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

        @Test
        @DisplayName("returns true when user has both language and timezone set")
        void returnsTrueWhenFullyRegistered() {
            when(userRepository.findById(USER_ID))
                    .thenReturn(Optional.of(buildUser()));

            assertThat(userService.isRegistered(USER_ID)).isTrue();
        }

        @Test
        @DisplayName("returns false when user has no language (mid-registration)")
        void returnsFalseWhenLanguageNull() {
            when(userRepository.findById(USER_ID))
                    .thenReturn(Optional.of(buildPartialUser()));

            assertThat(userService.isRegistered(USER_ID)).isFalse();
        }

        @Test
        @DisplayName("returns false for an unknown user")
        void returnsFalseWhenUserNotFound() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

            assertThat(userService.isRegistered(USER_ID)).isFalse();
        }
    }

    @Nested
    class CreatePartialUser {

        @Test
        @DisplayName("saves User and UserState(AWAITING_LANGUAGE) for new user")
        void savesUserAndUserState() {
            when(userRepository.existsById(USER_ID)).thenReturn(false);

            userService.createPartialUser(USER_ID, "alice");

            verify(userRepository).save(any(User.class));
            verify(userStateRepository).save(any(UserState.class));
        }

        @Test
        @DisplayName("skips creation when user already exists")
        void skipsWhenAlreadyExists() {
            when(userRepository.existsById(USER_ID)).thenReturn(true);

            userService.createPartialUser(USER_ID, "alice");

            verify(userRepository, never()).save(any());
            verify(userStateRepository, never()).save(any());
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

    @Nested
    class DeleteUser {

        @Test
        @DisplayName("calls deleteById with the given userId")
        void callsDeleteById() {
            userService.deleteUser(USER_ID);

            verify(userRepository).deleteById(USER_ID);
        }

        @Test
        @DisplayName("does not throw when user does not exist")
        void doesNotThrowWhenAbsent() {
            doNothing().when(userRepository).deleteById(USER_ID);

            org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                    () -> userService.deleteUser(USER_ID));
        }
    }
}
