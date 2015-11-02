package io.github.floto.util.task;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JSR310Module;
import com.google.common.base.Throwables;
import com.google.common.cache.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public class TaskPersistence {
    private final Logger log = LoggerFactory.getLogger(TaskPersistence.class);

    private final File tasksDirectory;
    private final ObjectMapper objectMapper;
    private AtomicLong nextTaskId;
    private LoadingCache<String, OutputStream> logStreamCache = CacheBuilder.newBuilder().removalListener(new RemovalListener<String, OutputStream>() {
        @Override
        public void onRemoval(RemovalNotification<String, OutputStream> notification) {
            try {
                log.debug("Closing {}", notification.getKey());
                notification.getValue().close();
            } catch(Throwable throwable) {
                log.warn("Unable to close logfile for {}", notification.getKey(), throwable);
            }
        }
    }).build(new CacheLoader<String, OutputStream>() {
        @Override
        public OutputStream load(String taskId) throws Exception {
            // TODO: close
            return new FileOutputStream(getLogFile(taskId));
        }
    });

    public TaskPersistence() {
        try {
            tasksDirectory = new File(System.getProperty("user.home") + "/.floto/tasks");
            FileUtils.forceMkdir(tasksDirectory);
            long numberOfTasks = tasksDirectory.listFiles((FileFilter) FileFilterUtils.directoryFileFilter()).length;
            // TODO: use actual maximum task id

            nextTaskId = new AtomicLong(numberOfTasks + 1);
            objectMapper = new ObjectMapper();
            objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
            objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
            objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            objectMapper.registerModule(new JSR310Module());
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }

    }

    public String getNextTaskId() {
        return "" + nextTaskId.getAndIncrement();
    }

    public void save(TaskInfo<?> taskInfo) {
        try {
            File taskDirectory = getTaskDirectory(taskInfo.getId());
            FileUtils.forceMkdir(taskDirectory);
            File tmpFile = new File(taskDirectory, UUID.randomUUID().toString());
            File infoFile = getTaskInfoFile(taskInfo.getId());
            objectMapper.writeValue(tmpFile, taskInfo);
            Files.move(tmpFile.toPath(), infoFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    private File getTaskInfoFile(String taskId) {
        return new File(getTaskDirectory(taskId), "info.json");
    }

    private File getLogFile(String taskId) {
        return new File(getTaskDirectory(taskId), "log.json");
    }

    private File getTaskDirectory(String taskId) {
        return new File(tasksDirectory, taskId);
    }

    public Collection<TaskInfo<?>> getTasks() {
//        return taskMap.values();
        return Collections.emptyList();
    }

    public void writeTasks(OutputStream output) {
        try {
            output.write("[\n".getBytes());
            boolean needComma = false;
            File[] files = tasksDirectory.listFiles((FileFilter) FileFilterUtils.directoryFileFilter());
            List<Integer> numbers = new ArrayList<>();
            for (File directory : files) {
                try {
                    numbers.add(Integer.valueOf(directory.getName()));
                } catch (Throwable ignored) {

                }
            }
            numbers.sort(Comparator.<Integer>reverseOrder());
            numbers = numbers.subList(0, Math.min(100, numbers.size()));
            for (int taskId: numbers) {
                File infoFile = new File(new File(tasksDirectory, String.valueOf(taskId)), "info.json");
                if (!infoFile.exists()) {
                    continue;
                }
                if (needComma) {
                    output.write(",\n".getBytes());
                } else {
                    needComma = true;
                }
                try (FileInputStream fis = new FileInputStream(infoFile)) {
                    IOUtils.copy(fis, output);
                }

            }
            output.write("]\n".getBytes());
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    public void addLogEntry(String id, LogEntry logEntry) {
        try {
            OutputStream outputStream = logStreamCache.getUnchecked(id);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            objectMapper.writer().writeValue(baos, logEntry);
            synchronized (outputStream) {
                outputStream.write(("\n" + baos.size() + "\n").getBytes());
                outputStream.write(baos.toByteArray());
            }
        } catch(Throwable ignored) {
            // do not log errors when logging
        }
    }

    @SuppressWarnings("deprecation")
    public void writeLogs(String taskId, OutputStream output) {
        try(FileInputStream input = new FileInputStream(getLogFile(taskId))) {
            DataInputStream dataInput = new DataInputStream(input);
            output.write("[\n".getBytes());
            byte[] buffer = new byte[4*1024];
            boolean needComma = false;
            try {
                while (true) {
                    IOUtils.skip(input, 1);
                    String line = dataInput.readLine();
                    if(line == null || line.isEmpty()) {
                        break;
                    }
                    int length = Integer.parseInt(line);
                    if(length > buffer.length) {
                        buffer = new byte[length];
                    }
                    IOUtils.readFully(input, buffer, 0, length);
                    if (needComma) {
                        output.write(",\n".getBytes());
                    } else {
                        needComma = true;
                    }
                    output.write(buffer, 0, length);
                }
            } catch(EOFException ignored) {
                // EOF reached
            }
            output.write("]\n".getBytes());
        } catch(Throwable throwable) {
            Throwables.propagate(throwable);
        }
    }

    public void closeLogFile(String taskId) {
        try {
            logStreamCache.invalidate(taskId);
        } catch(Throwable throwable) {
            log.warn("Unable to close logfile for {}", taskId, throwable);
        }

    }

    public InputStream getLogStream(String taskId) {
        try {
            return new FileInputStream(getLogFile(taskId));
        } catch (FileNotFoundException e) {
            throw Throwables.propagate(e);
        }
    }
}
