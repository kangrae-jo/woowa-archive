package com.kng0501.dbqueue.server.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "queue_monster")
public class MonsterEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "monster_id")
    private Long id;

    @Column(nullable = false, length = 1000)
    private String prompt;

    @Column(columnDefinition = "TEXT")
    private String image;

    protected MonsterEntity() {
    }

    public MonsterEntity(final String prompt) {
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
