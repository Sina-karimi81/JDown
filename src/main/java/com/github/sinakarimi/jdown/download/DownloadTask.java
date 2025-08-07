package com.github.sinakarimi.jdown.download;

import com.github.sinakarimi.jdown.common.HttpUtils;
import com.github.sinakarimi.jdown.configuration.ConfigurationConstants;
import com.github.sinakarimi.jdown.configuration.ConfigurationUtils;
import com.github.sinakarimi.jdown.dataObjects.DataSegment;
import com.github.sinakarimi.jdown.dataObjects.Range;
import com.github.sinakarimi.jdown.dataObjects.Status;
import com.github.sinakarimi.jdown.exception.CancelationFailedException;
import com.github.sinakarimi.jdown.exception.DownloadFailedException;
import com.github.sinakarimi.jdown.exception.DownloadNotResumableException;
import javafx.beans.property.*;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import static com.github.sinakarimi.jdown.common.HttpConstants.RANGE;

@Slf4j
@EqualsAndHashCode
//TODO: this class needs to change a way that each download segments becomes a downloadTask not the entire thing.
// this way we can pause/resume and update the database for efficiently, I think :))
public class DownloadTask {

    @Getter
    private final ConcurrentMap<Range, Boolean> segments;

    /**
     * Using property classes makes coding easier since they automatically bind to the values change event,
     * and we don't have to do any computation to check for changes
     */
    @Getter
    private SimpleStringProperty nameProperty;
    @Getter
    @Setter
    private String type;
    @Getter
    private ObjectProperty<Status> statusProperty;
    @Getter
    private SimpleLongProperty sizeProperty;
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
    private SimpleDoubleProperty progressProperty;
    @Getter
    private SimpleStringProperty descriptionProperty;

    private int totalBytesRead = 0;
    private CompletableFuture<Void> downloadTask;

    // A Lock implementation
    private final ReentrantLock lock = new ReentrantLock();
    // A condition object which we can use to put a thread to waiting state and the be able to wake it up
    // using the await and signal methods
    private final Condition isPausedCondition = lock.newCondition();
    private final AtomicBoolean isPaused = new AtomicBoolean(true);

    public DownloadTask() {
        segments = new ConcurrentHashMap<>();
        progressProperty = new SimpleDoubleProperty(0);
        this.descriptionProperty = new SimpleStringProperty();
    }

    @Builder
    public DownloadTask(String name, String type, Status status, Long size, String savePath, String downloadUrl, Boolean resumable, double progress, String description, boolean isPaused) {
        this.nameProperty = new SimpleStringProperty(name);
        this.type = type;
        this.statusProperty = new SimpleObjectProperty<>(status);
        this.sizeProperty = new SimpleLongProperty(size);
        this.savePath = savePath;
        this.downloadUrl = downloadUrl;
        this.resumable = resumable;
        this.isPaused.set(isPaused);
        this.progressProperty = new SimpleDoubleProperty(progress);
        this.descriptionProperty = new SimpleStringProperty(description);
        totalBytesRead = (int) ((progress / 100) * sizeProperty.get());
        this.segments = new ConcurrentHashMap<>();
    }

    // if a different wasn't used, lombok wouldn't recognize the new field
    @Builder(builderMethodName = "builderWithSegment", buildMethodName = "buildWithSegments")
    public DownloadTask(String name, String type, Status status, Long size, String savePath, String downloadUrl, Boolean resumable, double progress, String description, boolean isPaused, ConcurrentMap<Range, Boolean> segments) {
        this.nameProperty = new SimpleStringProperty(name);
        this.type = type;
        this.statusProperty = new SimpleObjectProperty<>(status);
        this.sizeProperty = new SimpleLongProperty(size);
        this.savePath = savePath;
        this.downloadUrl = downloadUrl;
        this.resumable = resumable;
        this.isPaused.set(isPaused);
        this.progressProperty = new SimpleDoubleProperty(progress);
        totalBytesRead = (int) ((progress / 100) * sizeProperty.get());
        this.descriptionProperty = new SimpleStringProperty(description);
        this.segments = segments;
    }

    /**
     * a simple convenience method
     * @return name of downloadTask
     */
    public String getName() {
        return nameProperty.get();
    }

    /**
     * a simple convenience method
     * @return status of downloadTask
     */
    public Status getStatus() {
        return statusProperty.get();
    }

    /**
     * a simple convenience method
     * @return size of downloadTask
     */
    public Long getSize() {
        return sizeProperty.get();
    }

    /**
     * a simple convenience method, {@link SimpleStringProperty#getValueSafe()} returns an empty string if the underline
     * value is null
     * @return description of downloadTask
     */
    public String getDescription() {
        return descriptionProperty.getValueSafe();
    }

    /**
     * a simple convenience method
     * @return progress percentage of downloadTask
     */
    public double getProgress() {
        return progressProperty.get();
    }

    public void setNameProperty(String name) {
        if (nameProperty == null) {
            nameProperty = new SimpleStringProperty(name);
        } else {
            nameProperty.set(name);
        }
    }

    public void setStatusProperty(Status status) {
        if (statusProperty == null) {
            statusProperty = new SimpleObjectProperty<>(status);
        } else {
            statusProperty.set(status);
        }
    }

    public void setSizeProperty(Long size) {
        if (sizeProperty == null) {
            sizeProperty = new SimpleLongProperty(size);
        } else {
            sizeProperty.set(size);
        }
    }

    public void setProgressProperty(double progress) {
        if (progressProperty == null) {
            progressProperty = new SimpleDoubleProperty(progress);
        } else {
            progressProperty.set(progress);
        }

        totalBytesRead = (int) ((progress / 100) * sizeProperty.get());
    }

    public void setDescriptionProperty(String description) {
        if (descriptionProperty == null) {
            descriptionProperty = new SimpleStringProperty(description);
        } else {
            descriptionProperty.set(description);
        }
    }

    public void cancel() {
        log.info("cancelling the download of file {}", nameProperty.get());
        boolean cancel = downloadTask.cancel(true);
        if (cancel) {
            statusProperty.set(Status.CANCELED);
            log.info("cancelled the download of file {} successfully", nameProperty.get());
        } else {
            log.error("failed to cancel the download of file {} successfully", nameProperty.get());
            throw new CancelationFailedException("Failed to cancel the download!");
        }
    }

    public void pause() {
        if (!resumable) {
            String message = String.format("download of file %s cannot be paused!", nameProperty.get());
            throw new DownloadNotResumableException(message);
        }

        try { // these must happen in a try catch block otherwise they throw exception
            lock.lock();
            this.isPaused.set(true);
        } finally {
            lock.unlock();
        }
        statusProperty.set(Status.PAUSED);
        log.info("set item {} to paused status, isPaused: {}", nameProperty.get(), isPaused);
    }

    /**
     * Used to resume a paused download, updates the isPaused field and calls the {@link Condition#signalAll()} to wake up
     * all the sleeping threads
     */
    public void resume() {
        try { // these must happen in a try catch block otherwise they throw exception
            lock.lock();
            this.isPaused.set(false);
            isPausedCondition.signalAll(); // wakes up all waiting threads
        } finally {
            lock.unlock();
        }

        statusProperty.set(Status.IN_PROGRESS);
        log.info("set item {} to in progress status, isPaused: {}", nameProperty.get(), isPaused);
    }

    public boolean isPaused() {
        return isPaused.get();
    }

    public boolean isCancelled() {
        return downloadTask.isCancelled();
    }

    /**
     * to reload the unfinished segments, since after booting up again the CompletableFuture object does not have any tasks in it
     * we pretty much to the same thing as {@link #start()}
     */
    public void reloadSegments() {
        log.info("started to reload unfinished segments...");
        List<Range> notCompletedSegments = segments.entrySet()
                .stream()
                .filter(e -> !e.getValue())
                .map(Map.Entry::getKey)
                .toList();

        isPaused.set(true);

        List<CompletableFuture<Void>> requests = new ArrayList<>();
        for (Range notCompletedSegment : notCompletedSegments) {
            CompletableFuture<Void> voidCompletableFuture = CompletableFuture.runAsync(() -> reloadHttpRequests(notCompletedSegment));
            requests.add(voidCompletableFuture);
        }

        downloadTask = composeRequests(requests);
        log.info("finished reloading unfinished segments");
    }

    private void reloadHttpRequests(Range range) {
        try {
            checkIsPausedCondition();
            sendHttpRequest(range);
        } catch (Exception e) {
            log.error("failed create a request for the given item with name {}!!!", nameProperty.get(), e);
            statusProperty.set(Status.ERROR);
            throw new DownloadFailedException("Failed to download the requested file: " + nameProperty.get(), e);
        }
    }

    public void start() throws DownloadFailedException {
        List<CompletableFuture<Void>> requests = new ArrayList<>();

        log.info("started to create a request for the {}", nameProperty.get());
        List<Range> ranges = createRanges(sizeProperty.get());

        for (Range range: ranges) {
            CompletableFuture<Void> downloadPartition = createDownloadSegments(range);
            requests.add(downloadPartition);
        }

        downloadTask = composeRequests(requests);

        statusProperty.set(Status.IN_PROGRESS);
        log.info("Finished creating request for {}", nameProperty.get());
    }

    private CompletableFuture<Void> createDownloadSegments(Range range) {
        return CompletableFuture.runAsync(() -> {
            segments.put(range, false);
            sendHttpRequest(range);
        });
    }

    private List<Range> createRanges(long size) {
        List<Range> ranges = new ArrayList<>();

        Integer numberOfThreads = ConfigurationUtils.getConfig(ConfigurationConstants.NUMBER_OF_THREADS, Integer.class);

        int interval = (int) Math.floorDiv(size, numberOfThreads);
        int start = 0;

        while (start < size) {
            int from = start;
            int to = Math.min((start + (interval - 1)), ((int) size - 1));
            ranges.add(new Range(from, to));
            start = start + interval;
        }

        return ranges;
    }

    private String createRangeHeader(Range range) {
        String rangeValues;
        if (range.getTo() >= sizeProperty.get()) {
            rangeValues = String.format("bytes=%d-", range.getFrom());
        } else {
            rangeValues = String.format("bytes=%d-%d", range.getFrom(), range.getTo());
        }
        return rangeValues;
    }

    private void sendHttpRequest(Range range) {
        HttpURLConnection connection = null;
        PausableInputStream inputStream = null;
        FileChannel fileChannel = null;

        try {
            checkIsPausedCondition();
            // Open HTTP connection
            URL url = new URL(downloadUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            // set a header
            connection.setRequestProperty(RANGE.getValue(), createRangeHeader(range));

            // Check for successful response
            int responseCode = connection.getResponseCode();
            if (!HttpUtils.isStatusCode2xx(responseCode)) {
                throw new IOException("Server returned HTTP response code: " + responseCode);
            }

            // Open file channel in READ_WRITE mode (create if needed)
            Path filePath = Path.of(savePath + "/" + nameProperty.get());
            fileChannel = FileChannel.open(filePath, StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE);

            // Set the file position where writing will start
            fileChannel.position(range.getFrom());

            // Get input stream from the connection
            inputStream = new PausableInputStream(connection.getInputStream());

            // Buffer for reading data
            ByteBuffer buffer = ByteBuffer.allocate(8192); // 8KB buffer

            // Stream data directly to file
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer.array())) != -1) {
                // Prepare buffer for writing
                buffer.limit(bytesRead);
                fileChannel.write(buffer);
                buffer.clear();

                updateProgress(bytesRead);
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            // Close resources in reverse order of creation
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    // Log or handle error
                }
            }
            if (fileChannel != null) {
                try {
                    fileChannel.close();
                } catch (IOException e) {
                    // Log or handle error
                }
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private CompletableFuture<Void> composeRequests(List<CompletableFuture<Void>> requests) {
        return CompletableFuture.allOf(requests.toArray(CompletableFuture[]::new))
                .thenAccept(r -> statusProperty.set(Status.COMPLETED))
                .exceptionally(ex -> {
                    if (ex != null) {
                        log.error("one of the requests failed to complete!!!", ex);
                        throw new DownloadFailedException(ex);
                    }

                    return null;
                });
    }

    /**
     * A method to check whether a download is paused or not. will put the thread in waiting mode using {@link Condition#await()}
     * until it is waked up using a {@link Condition#signalAll()} method when a download is resumed in {@link #resume()}
     * @throws InterruptedException if the threads are interrupted, for example during a thread shutdown
     */
    private void checkIsPausedCondition() throws InterruptedException {
        try {
            lock.lock();
            while(isPaused.get()) {
                log.info("inside the pause loop for item {}, isPaused: {} Thread: {}", nameProperty.get(), isPaused, Thread.currentThread().getName());
                isPausedCondition.await();
            }
        } finally {
            lock.unlock();
        }
    }

    private synchronized void updateProgress(int bytesRead) {
        totalBytesRead += bytesRead;
        double completedPercentage = (double) (totalBytesRead) / getSize();
        log.info("Download progress: {}\n", completedPercentage);
        progressProperty.set(completedPercentage);
    }

    public void waitForDuration(int duration) throws ExecutionException, InterruptedException, TimeoutException {
        downloadTask.get(duration, TimeUnit.MILLISECONDS);
    }
}
