package ru.zahaand.smarttaskbot;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Disabled("Integration smoke test — requires running PostgreSQL and Telegram credentials. " +
          "Run manually with a populated .env file to verify the full application context loads.")
class SmartTaskBotApplicationTests {

    @Test
    void contextLoads() {
    }

}
