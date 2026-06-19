package br.com.renan.corebanking.onboarding;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AccountOnboardingListenerApplication {
    public static void main(String[] args) {
        SpringApplication.run(AccountOnboardingListenerApplication.class, args);
    }
}
