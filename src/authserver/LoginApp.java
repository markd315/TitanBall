package authserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"authserver", "gameserver", "networking"})
public class LoginApp {
    public static void main(String[] args) {

        SpringApplication.run(LoginApp.class, args);

    }
}