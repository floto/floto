package io.github.floto.core.virtualization.esx;

import com.vmware.vim25.mo.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EsxUtils {
    private static final Logger log = LoggerFactory.getLogger(EsxUtils.class);

    public static void waitForTask(Task task, String description) {
        try {
            boolean success = task.waitForTask(200, 100).equals(Task.SUCCESS);
            if (success) {
                log.info("SUCCESS: {}", description);
            } else {
                throw new RuntimeException(task.getTaskInfo().error.getLocalizedMessage());
            }
        } catch (Throwable exception) {
            throw new RuntimeException("Exception occurred: " + exception.getMessage() + " during task: " + description);
        }
    }
}
