package com.gradely.grading;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public interface Sandbox {
    record Result(int exitCode,String log,List<String> junit,String jacoco,Map<String,String> quality) {}
    void verifyReady();
    Result run(Path archive);
}
