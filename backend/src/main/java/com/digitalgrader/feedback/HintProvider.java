package com.digitalgrader.feedback;

public interface HintProvider {
    record Reply(String content,String model) {}
    Reply generate(String context);
}
