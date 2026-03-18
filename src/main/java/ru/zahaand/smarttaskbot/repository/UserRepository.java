package ru.zahaand.smarttaskbot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.zahaand.smarttaskbot.model.User;

public interface UserRepository extends JpaRepository<User, Long> {
}
