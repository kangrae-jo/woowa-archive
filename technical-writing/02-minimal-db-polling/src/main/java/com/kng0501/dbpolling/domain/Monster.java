package com.kng0501.dbpolling.domain;

public record Monster(long id, String prompt, String image) {

    public Monster {
        if (id <= 0) {
            throw new IllegalArgumentException("monster id는 양수여야 합니다.");
        }
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("prompt는 비어 있을 수 없습니다.");
        }
    }

    public boolean hasImage() {
        return image != null;
    }
}
