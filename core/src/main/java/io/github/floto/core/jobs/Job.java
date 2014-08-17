package io.github.floto.core.jobs;

public interface Job<T> {
    T execute() throws Exception;
}
