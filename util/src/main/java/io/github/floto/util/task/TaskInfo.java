package io.github.floto.util.task;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Throwables;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Future;

public class TaskInfo<RESULT_TYPE> {
    public enum Status {
        QUEUED,
        RUNNING,
        CANCELLED,
        ERROR,
        SUCCESS
    }

    private final String id;
    private CompletableFuture<RESULT_TYPE> resultFuture = new CompletableFuture<>();
    private String title;
    private final Instant creationDate;
    private Instant startDate;
    private Instant endDate;
    private Duration duration;
    private Long durationInMs;
    private List<LogEntry> logEntries = new ArrayList<>();
    private Status status = Status.QUEUED;

    public TaskInfo(String taskId, String title, Callable<RESULT_TYPE> taskCallable) {
        creationDate = Instant.now();
        this.title = title;
        this.id = taskId;
    }

    public void complete(RESULT_TYPE result) {
        resultFuture.complete(result);
    }

    public void completeExceptionally(Throwable exception) {
        resultFuture.completeExceptionally(exception);
    }

    @JsonIgnore
    public RESULT_TYPE getResult() {
        try {
            return resultFuture.get();
        } catch (Exception exception) {
            throw Throwables.propagate(exception);
        }
    }

    @JsonIgnore
    public Future<RESULT_TYPE> getResultFuture() {
        return resultFuture;
    }

    @JsonIgnore
    public CompletionStage<RESULT_TYPE> getCompletionStage() {
        return resultFuture;
    }

    public String getTitle() {
        return title;
    }

    public String getId() {
        return id;
    }

    public Instant getCreationDate() {
        return creationDate;
    }

    public Instant getStartDate() {
        return startDate;
    }

    public Instant getEndDate() {
        return endDate;
    }

    protected void setStartDate(Instant startDate) {
        this.startDate = startDate;
    }

    protected void setEndDate(Instant endDate) {
        this.endDate = endDate;
        this.duration = Duration.between(startDate, endDate);
        this.durationInMs = this.duration.toMillis();
    }

    @JsonIgnore
    public Duration getDuration() {
        return duration;
    }

    @JsonIgnore
    public List<LogEntry> getLogEntries() {
        return logEntries;
    }

    @JsonIgnore
    public String getThreadName() {
        return title + " #" + id;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Long getDurationInMs() {
        return durationInMs;
    }
}
