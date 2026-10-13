package com.digitalgrader.config;

import java.util.List;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;

/** Validate before constructing a datasource; never print configuration values. */
public final class RequiredEnvironment implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {
    private static final List<String> REQUIRED = List.of("DB_URL", "DB_USERNAME", "DB_PASSWORD", "JWT_SECRET");

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        validate(event.getEnvironment());
    }

    public static void validate(Environment environment) {
        for (String name : REQUIRED) {
            String value = environment.getProperty(name);
            if (value == null || value.isBlank() || value.equals("replace_with_generated_secret")) {
                throw new IllegalStateException("Missing or placeholder configuration: " + name
                        + ". Run scripts/init-dev-env.py and load .env.");
            }
        }
        if (!environment.getProperty("DB_URL", "").startsWith("jdbc:postgresql://")) {
            throw new IllegalStateException("DB_URL must be a PostgreSQL JDBC URL.");
        }
        if (environment.getProperty("JWT_SECRET", "").length() < 64) {
            throw new IllegalStateException("JWT_SECRET must contain at least 64 characters.");
        }
    }
}
