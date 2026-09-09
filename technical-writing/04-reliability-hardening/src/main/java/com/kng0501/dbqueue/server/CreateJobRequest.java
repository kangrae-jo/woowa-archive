package com.kng0501.dbqueue.server;

public record CreateJobRequest(String prompt) {

    public String requiredPrompt() {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("prompt는 비어 있을 수 없습니다.");
        }
        return prompt;
    }
}
