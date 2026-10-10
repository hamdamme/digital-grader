package com.digitalgrader;

import com.digitalgrader.config.RequiredEnvironment;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class GraderApplication {
    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(GraderApplication.class);
        application.addListeners(new RequiredEnvironment());
        application.run(args);
    }
}
