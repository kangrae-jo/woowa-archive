package com.kng0501.dbpolling.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity(name = "BaselineMonsterEntity")
@Table(name = "monster")
public class BaselineMonsterEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 1000)
    private String prompt;

    @Column(columnDefinition = "TEXT")
    private String image;

    protected BaselineMonsterEntity() {
    }

    public BaselineMonsterEntity(final String prompt) {
        this.prompt = prompt;
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
