package com.kng0501.measurement;

public enum JobStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED;
    }
}
