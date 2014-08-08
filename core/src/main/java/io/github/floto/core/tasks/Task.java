package io.github.floto.core.tasks;

public interface Task<T> {
    T execute() throws Exception;
}
