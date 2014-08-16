package io.github.floto.util.task;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.google.common.base.Throwables;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Future;

public class TaskInfo<RESULT_TYPE> {
    private final String id;
    private CompletableFuture<RESULT_TYPE> resultFuture = new CompletableFuture<>();
    private String title;
    private final Instant creationDate;
    private Instant startDate;
    private Instant endDate;
    private Duration duration;
    private List<LogEntry> logEntries = new ArrayList<>();

    public TaskInfo(String title, Callable<RESULT_TYPE> taskCallable) {
        creationDate = Instant.now();
        this.title = title;
        this.id = getNextId();
    }

    static int nextId = 1;

    static private String getNextId() {
        // TODO: use UUID again
        return "" + nextId++;
    }

    public void complete(RESULT_TYPE result) {
        resultFuture.complete(result);
    }

    public void completeExceptionally(Throwable exception) {
        resultFuture.completeExceptionally(exception);
    }

    public RESULT_TYPE getResult() {
        try {
            return resultFuture.get();
        } catch (Exception exception) {
            throw Throwables.propagate(exception);
        }
    }

    public Future<RESULT_TYPE> getResultFuture() {
        return resultFuture;
    }

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
    }

    public Duration getDuration() {
        return duration;
    }

    public List<LogEntry> getLogEntries() {
        return logEntries;
    }

    public String getThreadName() {
        return title + "#" + id;
    }
}
