package com.kng0501.dbpolling.worker.domain;

public record ImageGenerationRequest(long id, String prompt) {

    public ImageGenerationRequest {
        if (id <= 0) {
            throw new IllegalArgumentException("request id는 양수여야 합니다.");
        }
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("prompt는 비어 있을 수 없습니다.");
        }
    }
}
