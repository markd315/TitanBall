package authserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;

@SpringBootApplication
public class LoginApp {
    public static void main(String[] args) throws IOException {
        SpringApplication.run(LoginApp.class, args);

    }
}