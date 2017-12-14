package com.db.awmd.challenge;

import com.db.awmd.challenge.service.EmailNotificationService;
import com.db.awmd.challenge.service.NotificationService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class DevChallengeApplication {

  public static void main(String[] args) {
    SpringApplication.run(DevChallengeApplication.class, args);
  }

  @Bean
  NotificationService notificationService() {
    return new EmailNotificationService();
  }
}
