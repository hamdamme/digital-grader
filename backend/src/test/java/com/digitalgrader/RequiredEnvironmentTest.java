package com.digitalgrader;

import com.digitalgrader.config.RequiredEnvironment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;

class RequiredEnvironmentTest {
    private MockEnvironment valid() {
        return new MockEnvironment().withProperty("DB_URL", "jdbc:postgresql://localhost/test")
                .withProperty("DB_USERNAME", "test").withProperty("DB_PASSWORD", "fixture-only")
                .withProperty("JWT_SECRET", "x".repeat(64));
    }
    @Test void acceptsValidConfiguration() {
        assertThatCode(() -> RequiredEnvironment.validate(valid())).doesNotThrowAnyException();
    }
    @ParameterizedTest @ValueSource(strings = {"DB_URL", "DB_USERNAME", "DB_PASSWORD", "JWT_SECRET"})
    void identifiesMissingVariableWithoutLeakingOtherValues(String key) {
        MockEnvironment environment = valid();
        ((org.springframework.core.env.MapPropertySource) environment.getPropertySources().get("mockProperties"))
                .getSource().remove(key);
        assertThatThrownBy(() -> RequiredEnvironment.validate(environment))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining(key)
                .hasMessageNotContaining("fixture-only").hasMessageNotContaining("x".repeat(64));
    }
    @Test void rejectsTemplateSecrets() {
        assertThatThrownBy(() -> RequiredEnvironment.validate(valid().withProperty("JWT_SECRET", "replace_with_generated_secret")))
                .hasMessageContaining("JWT_SECRET");
    }
    @Test void rejectsShortJwtSecret() {
        assertThatThrownBy(() -> RequiredEnvironment.validate(valid().withProperty("JWT_SECRET", "short")))
                .hasMessageContaining("at least 64");
    }
    @Test void rejectsWrongDatabaseType() {
        assertThatThrownBy(() -> RequiredEnvironment.validate(valid().withProperty("DB_URL", "jdbc:h2:mem:test")))
                .hasMessageContaining("PostgreSQL");
    }
}
