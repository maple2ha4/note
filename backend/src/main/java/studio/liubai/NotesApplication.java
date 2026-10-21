package studio.liubai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(
    exclude =
        org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration
            .class)
public class NotesApplication {
  public static void main(String[] args) {
    SpringApplication.run(NotesApplication.class, args);
  }
}
