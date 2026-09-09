package com.kng0501.dbpolling.server.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "image_generation_request")
public class ImageGenerationRequestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 1000)
    private String prompt;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(6)")
    private Instant createdAt;

    protected ImageGenerationRequestEntity() {
    }

    public ImageGenerationRequestEntity(final String prompt, final Instant createdAt) {
        this.prompt = prompt;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getPrompt() {
        return prompt;
    }
}
