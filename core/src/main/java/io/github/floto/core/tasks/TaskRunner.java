package io.github.floto.core.tasks;

import com.google.common.base.Throwables;

public class TaskRunner {

    public <T> T runTask(Task<T> task) {
        try {
            return task.execute();
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }
}
