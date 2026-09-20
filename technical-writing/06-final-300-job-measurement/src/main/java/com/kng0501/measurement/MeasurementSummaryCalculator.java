package com.kng0501.measurement;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class MeasurementSummaryCalculator {

    public MeasurementSummary calculate(
            final String runId,
            final Instant startedAt,
            final Instant finishedAt,
            final List<RequestRecord> requests,
            final List<ObservedJob> databaseJobs,
            final int maximumPendingCount
    ) {
        final List<Long> acceptedJobIds = requests.stream()
                .filter(RequestRecord::hasAcceptedJob)
                .map(RequestRecord::jobId)
                .toList();
        final Set<Long> uniqueAcceptedJobIds = Set.copyOf(acceptedJobIds);
        final Map<Long, ObservedJob> jobsById = databaseJobs.stream()
                .filter(job -> uniqueAcceptedJobIds.contains(job.jobId()))
                .collect(Collectors.toMap(ObservedJob::jobId, job -> job));
        final List<ObservedJob> acceptedJobs = List.copyOf(jobsById.values());

        final int succeeded = count(acceptedJobs, job -> job.status() == JobStatus.SUCCEEDED);
        final int failed = count(acceptedJobs, job -> job.status() == JobStatus.FAILED);
        final int pending = count(acceptedJobs, job -> job.status() == JobStatus.PENDING);
        final int running = count(acceptedJobs, job -> job.status() == JobStatus.RUNNING);
        final boolean allTerminal = !uniqueAcceptedJobIds.isEmpty()
                && uniqueAcceptedJobIds.size() == jobsById.size()
                && acceptedJobs.stream().allMatch(ObservedJob::isTerminal);

        final LatencyPercentiles requestLatency = Percentiles.calculate(requests.stream()
                .map(request -> Duration.ofMillis(request.latencyMillis()))
                .toList());
        final LatencyPercentiles jobWaitingTime = Percentiles.calculate(acceptedJobs.stream()
                .filter(job -> job.createdAt() != null && job.startedAt() != null)
                .map(job -> durationBetween(job.createdAt(), job.startedAt()))
                .filter(duration -> !duration.isNegative())
                .toList());
        final LatencyPercentiles jobProcessingTime = Percentiles.calculate(acceptedJobs.stream()
                .filter(job -> job.startedAt() != null && job.finishedAt() != null)
                .map(job -> durationBetween(job.startedAt(), job.finishedAt()))
                .filter(duration -> !duration.isNegative())
                .toList());

        return new MeasurementSummary(
                runId,
                startedAt,
                finishedAt,
                allTerminal,
                totalCompletionMillis(startedAt, acceptedJobs, allTerminal),
                durationBetween(startedAt, finishedAt).toMillis(),
                requests.size(),
                count(requests, request -> request.httpStatus() == 202),
                count(requests, request -> !request.hasAcceptedJob()),
                uniqueAcceptedJobIds.size(),
                acceptedJobIds.size() - uniqueAcceptedJobIds.size(),
                succeeded,
                failed,
                pending,
                running,
                pending + running,
                uniqueAcceptedJobIds.size() - jobsById.size(),
                count(acceptedJobs, job -> job.status() == JobStatus.SUCCEEDED
                        && (job.image() == null || job.image().isBlank())),
                count(acceptedJobs, ObservedJob::hasMisconnectedImage),
                count(acceptedJobs, job -> job.attemptCount() >= 2),
                acceptedJobs.stream().mapToLong(job -> Math.max(job.attemptCount() - 1L, 0)).sum(),
                maximumPendingCount,
                requestLatency,
                jobWaitingTime,
                jobProcessingTime
        );
    }

    private static <T> int count(final List<T> values, final Predicate<T> predicate) {
        return (int) values.stream().filter(predicate).count();
    }

    private static Duration durationBetween(final Instant startedAt, final Instant finishedAt) {
        return Duration.between(startedAt, finishedAt);
    }

    private static Long totalCompletionMillis(
            final Instant startedAt,
            final List<ObservedJob> jobs,
            final boolean allTerminal
    ) {
        if (!allTerminal || jobs.isEmpty() || jobs.stream().anyMatch(job -> job.finishedAt() == null)) {
            return null;
        }
        final Instant latestFinishedAt = jobs.stream()
                .map(ObservedJob::finishedAt)
                .max(Instant::compareTo)
                .orElseThrow();
        return durationBetween(startedAt, latestFinishedAt).toMillis();
    }
}
