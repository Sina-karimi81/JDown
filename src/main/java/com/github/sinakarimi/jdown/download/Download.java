package com.github.sinakarimi.jdown.download;

import com.github.sinakarimi.jdown.ClassManager;
import com.github.sinakarimi.jdown.configuration.ConfigurationConstants;
import com.github.sinakarimi.jdown.configuration.ConfigurationUtils;
import com.github.sinakarimi.jdown.dataObjects.Range;
import com.github.sinakarimi.jdown.dataObjects.Status;
import com.github.sinakarimi.jdown.database.TasksDAO;
import com.github.sinakarimi.jdown.exception.DownloadFailedException;
import com.github.sinakarimi.jdown.exception.DownloadNotResumableException;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Builder
@EqualsAndHashCode
@Slf4j
@AllArgsConstructor
public class Download implements Serializable {

    private final TasksDAO tasksDAO;

    @Getter
    @Setter
    private String name;
    @Getter
    @Setter
    private String type;
    @Getter
    @Setter
    private Long size;
    @Getter
    @Setter
    private String savePath;
    @Getter
    @Setter
    private String downloadUrl;
    @Getter
    @Setter
    private Boolean resumable;
    @Getter
    private ObjectProperty<Status> statusProperty;
    @Getter
    private SimpleDoubleProperty progressProperty;
    @Getter
    private SimpleStringProperty descriptionProperty;
    @Getter
    List<DownloadTask> downloadTasks;

    private int totalBytesRead;

    public void setStatus(Status status) {
        if (statusProperty == null) {
            statusProperty = new SimpleObjectProperty<>(status);
        } else {
            statusProperty.set(status);
        }
    }

    public void setProgress(double progress) {
        if (progressProperty == null) {
            progressProperty = new SimpleDoubleProperty(progress);
        } else {
            progressProperty.set(progress);
        }

        totalBytesRead = (int) (progress * size);
    }

    public void setDescription(String description) {
        if (descriptionProperty == null) {
            descriptionProperty = new SimpleStringProperty(description);
        } else {
            descriptionProperty.set(description);
        }
    }

    public void start() throws DownloadFailedException {
        log.info("started to create a request for the {}", name);
        List<Range> ranges = createRanges(size);

        createDownloadTasks(ranges);

        String filePath = getFilePath();
        downloadTasks.forEach(t -> t.start(downloadUrl, filePath));

        this.progressProperty = new SimpleDoubleProperty(0);
        statusProperty.set(Status.IN_PROGRESS);
        log.info("Finished creating request for {}", name);
    }

    /**
     * to reload the unfinished tasks, since after booting up again the inputStream object in null
     * we pretty much to the same thing as {@link #start()}
     */
    public void reloadSegments() {
        log.info("started to reload unfinished segments...");
        List<DownloadTask> notCompletedTasks = downloadTasks.stream()
                .filter(d -> !d.isCompleted())
                .toList();

        for (DownloadTask task : notCompletedTasks) {
            task.start(downloadUrl, getFilePath());
        }

        downloadTasks = notCompletedTasks;
        log.info("finished reloading unfinished segments");
    }

    private List<Range> createRanges(long size) {
        List<Range> ranges = new ArrayList<>();

        Integer numberOfThreads = ConfigurationUtils.getConfig(ConfigurationConstants.NUMBER_OF_THREADS, Integer.class);

        int interval = (int) Math.floorDiv(size, numberOfThreads);
        int start = 0;

        while (start < size) {
            int from = start;
            int to = Math.min((start + (interval - 1)), ((int) size - 1));

            if (to >= size) {
                to = -1;
            }

            ranges.add(new Range(from, to));
            start = start + interval;
        }

        return ranges;
    }

    private void createDownloadTasks(List<Range> ranges) {
        downloadTasks = new ArrayList<>();

        for (Range range : ranges) {
            DownloadTask task = new DownloadTask(range, this::updateProgress, this::updateStatus);
            downloadTasks.add(task);
        }
    }

    private String getFilePath() {
        return savePath + "/" + name;
    }

    private synchronized void updateProgress(int bytesRead) {
        totalBytesRead += bytesRead;
        double completedPercentage = (double) (totalBytesRead) / size;
        log.info("Download progress: {}\n", completedPercentage);
        progressProperty.set(completedPercentage);
    }

    private void updateStatus(Exception e) {
        log.info("inside update status method, with input : {} , and thread: {}", e == null, Thread.currentThread().getId());
        if (e != null) {
            log.info("inside update status method, with thread: {}, setting to ERROR", Thread.currentThread().getId());
            cancel();
            statusProperty.set(Status.ERROR);
            tasksDAO.updateStatus(name, Status.COMPLETED);
        } else {
            log.info("inside update status method, with thread: {}, setting to COMPLETE", Thread.currentThread().getId());
            boolean downloadComplete = downloadTasks.stream()
                    .parallel()
                    .allMatch(DownloadTask::isCompleted);
            if (!downloadComplete) {
                log.info("inside update status method, with thread: {}, IS COMPLETE: {}", Thread.currentThread().getId(), downloadComplete);
                return;
            }

            statusProperty.set(Status.COMPLETED);
            tasksDAO.updateStatus(name, Status.COMPLETED);
        }
    }

    public void cancel() {
        log.info("cancelling the download of file {}", name);
        downloadTasks.forEach(DownloadTask::cancel);
        statusProperty.set(Status.CANCELED);
        log.info("cancelled the download of file {} successfully", name);
    }

    public void pause() {
        if (!resumable) {
            String message = String.format("download of file %s cannot be paused!", name);
            throw new DownloadNotResumableException(message);
        }

        downloadTasks.forEach(DownloadTask::pause);
        statusProperty.set(Status.PAUSED);
        tasksDAO.updateProgression(name, (double) (totalBytesRead) / size);
        log.info("set item {} to paused status", name);
    }

    public void resume() {
        downloadTasks.forEach(DownloadTask::resume);
        statusProperty.set(Status.IN_PROGRESS);
        log.info("set item {} to in progress status", name);
    }

}
