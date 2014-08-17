package io.github.floto.core.jobs;

import com.google.common.base.Throwables;

public class JobRunner {

    public <T> T runJob(Job<T> job) {
        try {
            return job.execute();
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }
}
