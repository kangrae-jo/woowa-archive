package com.kng0501.dbqueue.persistence.entity;

import com.kng0501.dbqueue.domain.JobStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity(name = "ImageGenerationJobEntity")
@Table(name = "image_generation_job")
public class ImageGenerationJobEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "job_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "monster_id", nullable = false)
    private QueueMonsterEntity monster;

    @Column(nullable = false, length = 1000)
    private String prompt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private JobStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at", nullable = false, columnDefinition = "DATETIME(6)")
    private Instant nextAttemptAt;

    @Column(name = "deadline_at", columnDefinition = "DATETIME(6)")
    private Instant deadlineAt;

    @Convert(converter = UuidStringConverter.class)
    @Column(name = "claim_token", length = 36, columnDefinition = "CHAR(36)")
    private UUID claimToken;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(6)")
    private Instant createdAt;

    @Column(name = "started_at", columnDefinition = "DATETIME(6)")
    private Instant startedAt;

    @Column(name = "finished_at", columnDefinition = "DATETIME(6)")
    private Instant finishedAt;

    @Column(name = "last_error", length = 2000)
    private String lastError;

    protected ImageGenerationJobEntity() {
    }

    public ImageGenerationJobEntity(
            final QueueMonsterEntity monster,
            final String prompt,
            final Instant now
    ) {
        this.monster = monster;
        this.prompt = prompt;
        this.status = JobStatus.PENDING;
        this.attemptCount = 0;
        this.nextAttemptAt = now;
        this.createdAt = now;
    }

    public Long getId() {
        return id;
    }

    public QueueMonsterEntity getMonster() {
        return monster;
    }

    public String getPrompt() {
        return prompt;
    }

    public JobStatus getStatus() {
        return status;
    }
}
