package com.kng0501.measurement;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MeasurementRunner implements ApplicationRunner {

    private final MeasurementProperties properties;
    private final MeasurementDatabase database;
    private final HttpJobClient jobs;
    private final MeasurementSummaryCalculator calculator;
    private final MeasurementResultWriter writer;
    private final Clock clock;

    public MeasurementRunner(
            final MeasurementProperties properties,
            final MeasurementDatabase database,
            final HttpJobClient jobs,
            final MeasurementSummaryCalculator calculator,
            final MeasurementResultWriter writer,
            final Clock clock
    ) {
        this.properties = properties;
        this.database = database;
        this.jobs = jobs;
        this.calculator = calculator;
        this.writer = writer;
        this.clock = clock;
    }

    @Override
    public void run(final ApplicationArguments arguments) throws Exception {
        properties.validate();
        final String databaseName = database.verifyMeasurementDatabaseIsEmpty();
        final String mysqlVersion = database.serverVersion();
        final String runId = UUID.randomUUID().toString();
        final Instant startedAt = clock.instant();
        final Instant deadline = startedAt.plus(properties.getTimeout());
        final List<RequestRecord> requests;
        final Observation observation;
        final int maximumPendingCount;
        try (QueueDepthMonitor queueDepthMonitor = new QueueDepthMonitor(database, properties.getPollingInterval())) {
            queueDepthMonitor.start(runId);
            requests = registerJobs(runId, deadline);
            observation = observeUntilTerminal(runId, acceptedJobIds(requests), deadline);
            maximumPendingCount = Math.max(observation.maximumPendingCount(), queueDepthMonitor.maximumPendingCount());
        }
        final Instant finishedAt = clock.instant();
        final MeasurementSummary summary = calculator.calculate(
                runId, startedAt, finishedAt, requests, observation.jobs(), maximumPendingCount
        );
        final EnvironmentReport environment = new EnvironmentReport(
                runId,
                startedAt,
                properties.getBaseUrl(),
                databaseName,
                properties.getJobCount(),
                properties.getRequestConcurrency(),
                properties.getTimeout(),
                properties.getPollingInterval(),
                properties.getExpectedWorkerConcurrency(),
                properties.getExpectedGeneratorDelay(),
                System.getProperty("java.version"),
                System.getProperty("os.name"),
                System.getProperty("os.version"),
                System.getProperty("os.arch"),
                mysqlVersion
        );
        writer.write(properties.getOutputDirectory(), summary, environment, requests, byId(observation.jobs()));
        log.info(
                "measurement completed: run_id={} succeeded={} failed={} unfinished={} missing={} retried={}",
                runId,
                summary.succeededCount(),
                summary.failedCount(),
                summary.unfinishedCount(),
                summary.missingAcceptedJobCount(),
                summary.retriedJobCount()
        );
    }

    private List<RequestRecord> registerJobs(final String runId, final Instant deadline) {
        final ExecutorService executor = Executors.newFixedThreadPool(
                properties.getRequestConcurrency(),
                Thread.ofPlatform().name("measurement-request-", 0).factory()
        );
        try {
            final List<RequestPlan> plans = new ArrayList<>();
            for (int requestNumber = 1; requestNumber <= properties.getJobCount(); requestNumber++) {
                plans.add(new RequestPlan(
                        requestNumber,
                        "measurement-" + runId + "-" + requestNumber,
                        clock.instant()
                ));
            }
            final List<Future<RequestRecord>> futures = invokeAll(executor, plans, deadline);
            final List<RequestRecord> requests = new ArrayList<>();
            for (int index = 0; index < futures.size(); index++) {
                final Future<RequestRecord> future = futures.get(index);
                final RequestPlan plan = plans.get(index);
                requests.add(future.isCancelled() ? timedOut(plan) : awaitRequest(future));
            }
            return List.copyOf(requests);
        } finally {
            shutdown(executor);
        }
    }

    private Observation observeUntilTerminal(
            final String runId,
            final Set<Long> acceptedJobIds,
            final Instant deadline
    ) {
        List<ObservedJob> latestJobs = List.of();
        int maximumPendingCount = 0;
        while (true) {
            latestJobs = database.findJobs(runId);
            maximumPendingCount = Math.max(maximumPendingCount, pendingCount(latestJobs));
            final boolean allTerminal = observeHttpStatuses(acceptedJobIds);
            if (allTerminal || !clock.instant().isBefore(deadline)) {
                latestJobs = database.findJobs(runId);
                maximumPendingCount = Math.max(maximumPendingCount, pendingCount(latestJobs));
                return new Observation(latestJobs, maximumPendingCount);
            }
            sleepUntilNextObservation(deadline);
        }
    }

    private boolean observeHttpStatuses(final Set<Long> acceptedJobIds) {
        if (acceptedJobIds.isEmpty()) {
            return true;
        }
        for (final long jobId : acceptedJobIds) {
            final ObservedJob observed = jobs.findJob(jobId).orElse(null);
            if (observed == null || !observed.isTerminal()) {
                return false;
            }
        }
        return true;
    }

    private void sleepUntilNextObservation(final Instant deadline) {
        final Duration remaining = Duration.between(clock.instant(), deadline);
        final Duration wait = remaining.compareTo(properties.getPollingInterval()) < 0
                ? remaining
                : properties.getPollingInterval();
        if (wait.isZero() || wait.isNegative()) {
            return;
        }
        try {
            Thread.sleep(wait);
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("측정 관찰이 중단됐습니다.", exception);
        }
    }

    private static RequestRecord awaitRequest(final Future<RequestRecord> future) {
        try {
            return future.get();
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Job 등록 요청 수집이 중단됐습니다.", exception);
        } catch (final ExecutionException exception) {
            throw new IllegalStateException("Job 등록 요청 실행이 실패했습니다.", exception.getCause());
        } catch (final CancellationException exception) {
            throw new IllegalStateException("Job 등록 요청이 취소됐습니다.", exception);
        }
    }

    private List<Future<RequestRecord>> invokeAll(
            final ExecutorService executor,
            final List<RequestPlan> plans,
            final Instant deadline
    ) {
        final long remainingNanos = Math.max(0, Duration.between(clock.instant(), deadline).toNanos());
        final List<Callable<RequestRecord>> tasks = plans.stream()
                .<Callable<RequestRecord>>map(plan -> () -> jobs.register(plan.requestNumber(), plan.prompt()))
                .toList();
        try {
            return executor.invokeAll(tasks, remainingNanos, TimeUnit.NANOSECONDS);
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Job 등록 요청 실행이 중단됐습니다.", exception);
        }
    }

    private RequestRecord timedOut(final RequestPlan plan) {
        final Instant finishedAt = clock.instant();
        return new RequestRecord(
                plan.requestNumber(),
                plan.prompt(),
                -1,
                null,
                null,
                plan.scheduledAt(),
                finishedAt,
                Math.max(0, Duration.between(plan.scheduledAt(), finishedAt).toMillis()),
                "measurement timeout"
        );
    }

    private static Set<Long> acceptedJobIds(final List<RequestRecord> requests) {
        final Set<Long> jobIds = new LinkedHashSet<>();
        for (final RequestRecord request : requests) {
            if (request.hasAcceptedJob()) {
                jobIds.add(request.jobId());
            }
        }
        return Set.copyOf(jobIds);
    }

    private static int pendingCount(final List<ObservedJob> jobs) {
        return (int) jobs.stream().filter(job -> job.status() == JobStatus.PENDING).count();
    }

    private static Map<Long, ObservedJob> byId(final List<ObservedJob> jobs) {
        final Map<Long, ObservedJob> values = new HashMap<>();
        for (final ObservedJob job : jobs) {
            values.put(job.jobId(), job);
        }
        return values;
    }

    private static void shutdown(final ExecutorService executor) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (final InterruptedException exception) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private record Observation(List<ObservedJob> jobs, int maximumPendingCount) {
    }

    private record RequestPlan(int requestNumber, String prompt, Instant scheduledAt) {
    }
}
