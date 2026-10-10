package com.digitalgrader.assignments;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record Rubric(@NotNull @Valid Weights weights, @NotNull @Min(0) @Max(100) Integer coverageThreshold,
        @NotNull @Size(max=2) List<@NotNull @Pattern(regexp="checkstyle|spotbugs") String> qualityChecks) {
    public record Weights(@NotNull @Min(0) @Max(100) Integer tests, @NotNull @Min(0) @Max(100) Integer coverage,
            @NotNull @Min(0) @Max(100) Integer codeQuality) {
        @JsonIgnore @AssertTrue public boolean isTotalValid() {
            return tests == null || coverage == null || codeQuality == null || tests + coverage + codeQuality == 100;
        }
    }
    @JsonIgnore @AssertTrue public boolean isChecksUnique() { return qualityChecks == null || qualityChecks.stream().distinct().count() == qualityChecks.size(); }
}
