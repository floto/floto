package io.github.floto.util.task;

import java.time.Instant;

public class LogEntry {
    private final String message;
    private final String level;
    private final String logger;
    private final Instant timestamp;
    private String stackTrace;

    public LogEntry(String message, String level, String logger, Instant timestamp) {
        this.message = message;
        this.level = level;
        this.logger = logger;
        this.timestamp = timestamp;
    }

    public String getMessage() {
        return message;
    }

    public String getLevel() {
        return level;
    }

    public void setStackTrace(String stackTrace) {
        this.stackTrace = stackTrace;
    }

    public String getStackTrace() {
        return stackTrace;
    }

    public String getLogger() {
        return logger;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}
