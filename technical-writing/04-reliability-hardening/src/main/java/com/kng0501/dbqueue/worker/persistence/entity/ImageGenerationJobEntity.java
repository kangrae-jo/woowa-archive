package com.kng0501.dbqueue.worker.persistence.entity;

import com.kng0501.dbqueue.worker.domain.JobStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "image_generation_job")
public class ImageGenerationJobEntity {

    @Id
    @Column(name = "job_id")
    private Long id;

    @Column(name = "monster_id", nullable = false)
    private Long monsterId;

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

    public Long getId() {
        return id;
    }

    public Long getMonsterId() {
        return monsterId;
    }

    public JobStatus getStatus() {
        return status;
    }
}
