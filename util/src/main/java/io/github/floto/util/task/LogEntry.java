package io.github.floto.util.task;

public class LogEntry {
    private String message;
    private String level;

    public LogEntry(String message, String level) {
        this.message = message;
        this.level = level;
    }

    public String getMessage() {
        return message;
    }

    public String getLevel() {
        return level;
    }
}
