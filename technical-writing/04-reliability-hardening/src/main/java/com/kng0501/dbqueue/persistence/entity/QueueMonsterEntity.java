package com.kng0501.dbqueue.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity(name = "QueueMonsterEntity")
@Table(name = "queue_monster")
public class QueueMonsterEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "monster_id")
    private Long id;

    @Column(nullable = false, length = 1000)
    private String prompt;

    @Column(columnDefinition = "TEXT")
    private String image;

    protected QueueMonsterEntity() {
    }

    public QueueMonsterEntity(final String prompt) {
        this.prompt = prompt;
    }

    public void updateImage(final String image) {
        if (image == null || image.isBlank()) {
            throw new IllegalArgumentException("생성 이미지는 비어 있을 수 없습니다.");
        }
        this.image = image;
    }

    public Long getId() {
        return id;
    }

    public String getPrompt() {
        return prompt;
    }

    public String getImage() {
        return image;
    }
}
