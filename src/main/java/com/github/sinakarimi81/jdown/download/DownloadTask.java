package com.github.sinakarimi81.jdown.download;

import com.github.sinakarimi81.jdown.common.HttpUtils;
import com.github.sinakarimi81.jdown.configuration.ConfigurationUtils;
import com.github.sinakarimi81.jdown.dataObjects.DataSegment;
import com.github.sinakarimi81.jdown.dataObjects.Range;
import com.github.sinakarimi81.jdown.dataObjects.Status;
import com.github.sinakarimi81.jdown.exception.CancelationFailedException;
import com.github.sinakarimi81.jdown.exception.DownloadFailedException;
import com.github.sinakarimi81.jdown.exception.DownloadNotResumableException;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static com.github.sinakarimi81.jdown.common.HttpConstants.GET_METHOD;
import static com.github.sinakarimi81.jdown.common.HttpConstants.RANGE;

@Slf4j
public class DownloadTask {

    @Getter
    private final ConcurrentMap<Range, DataSegment> segments;
    @Getter
    @Setter
    private String name;
    @Getter
    @Setter
    private String type;
    @Getter
    @Setter
    private Status status;
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

    private CompletableFuture<Void> downloadTask;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private volatile boolean isPaused = false;

    public DownloadTask() {
        segments = new ConcurrentHashMap<>();
    }

    @Builder
    public DownloadTask(String name, String type, Status status, Long size, String savePath, String downloadUrl, Boolean resumable, Boolean isPaused) {
        this.name = name;
        this.type = type;
        this.status = status;
        this.size = size;
        this.savePath = savePath;
        this.downloadUrl = downloadUrl;
        this.resumable = resumable;
        this.isPaused = isPaused;
        this.segments = new ConcurrentHashMap<>();
    }

    @Builder(builderMethodName = "builderWithSegment", buildMethodName = "buildWithSegments")
    public DownloadTask(String name, String type, Status status, Long size, String savePath, String downloadUrl, Boolean resumable, Boolean isPaused, ConcurrentMap<Range, DataSegment> segments) {
        this.name = name;
        this.type = type;
        this.status = status;
        this.size = size;
        this.savePath = savePath;
        this.downloadUrl = downloadUrl;
        this.resumable = resumable;
        this.isPaused = isPaused;
        this.segments = segments;
    }

    public void cancel() {
        log.info("cancelling the download of file {}", name);
        boolean cancel = downloadTask.cancel(true);
        if (cancel) {
            status = Status.CANCELED;
            log.info("cancelled the download of file {} successfully", name);
        } else {
            log.error("failed to cancel the download of file {} successfully", name);
            throw new CancelationFailedException("Failed to cancel the download!");
        }
    }

    public void pause() {
        lock.writeLock().lock();
        try {
            if (!resumable) {
                String message = String.format("download of file %s cannot be paused!", name);
                throw new DownloadNotResumableException(message);
            }

            isPaused = true;
            status = Status.PAUSED;
            log.info("set item {} to paused status, isPaused: {}", name, isPaused);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void resume() {
        lock.writeLock().lock();
        try {
            isPaused = false;
            status = Status.IN_PROGRESS;
            log.info("set item {} to in progress status, isPaused: {}", name, isPaused);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean isPaused() {
        return isPaused;
    }

    public boolean isCancelled() {
        return downloadTask.isCancelled();
    }

    public void start() throws DownloadFailedException {
        List<CompletableFuture<Void>> requests= new ArrayList<>();

        log.info("started to create a request for the {}", name);
        List<Range> ranges = createRanges(size);

        for (Range range: ranges) {
            CompletableFuture<Void> downloadPartition = createDataSegments(range);
            requests.add(downloadPartition);
        }

        downloadTask = CompletableFuture.allOf(requests.toArray(CompletableFuture[]::new))
                .thenAccept(r -> saveToFile())
                .exceptionally(ex -> {
                    if (ex != null) {
                        log.error("one of the requests failed to complete!!!", ex);
                        throw new DownloadFailedException(ex);
                    }

                    return null;
                });

        status = Status.IN_PROGRESS;
        log.info("Finished creating request for {}", name);
    }

    private CompletableFuture<Void> createDataSegments(Range range) {
        return CompletableFuture.runAsync(() -> {
            try {

                String rangeValues;
                if (range.getTo() >= size) {
                    rangeValues = String.format("bytes=%d-", range.getFrom());
                } else {
                    rangeValues = String.format("bytes=%d-%d", range.getFrom(), range.getTo());
                }

                int size = range.getTo() - range.getFrom() + 1;
                segments.put(range, new DataSegment(size));

                while (isPaused) {
                    log.info("inside the pause loop for item {}, isPaused: {} Thread: {}", name, isPaused, Thread.currentThread().getName());
                    Thread.onSpinWait(); // just used for performance by the JVM, no additional functionality
                }

                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest
                        .newBuilder(new URI(downloadUrl))
                        .method(GET_METHOD.getValue(), HttpRequest.BodyPublishers.noBody())
                        .header(RANGE.getValue(), rangeValues)
                        .build();

                HttpResponse<byte[]> send = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
                saveToArray(range, send);
            } catch (Exception e) {
                log.error("failed create a request for the given item with name {}!!!", name, e);
                status = Status.ERROR;
                throw new DownloadFailedException("Failed to download the requested file: " + name, e);
            }
        });
    }

    private List<Range> createRanges(long size) {
        List<Range> ranges = new ArrayList<>();

        Integer numberOfThreads = ConfigurationUtils.getConfig(ConfigurationUtils.ConfigurationConstants.NUMBER_OF_THREADS, Integer.class);

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

    private void saveToArray(Range range, HttpResponse<byte[]> response) {
        log.info("saving request range {} with status {} to byte array", range.rangeString(), response.statusCode());
        if (!HttpUtils.isStatusCode2xx(response)) {
            String message = String.format("Failed to fetch data for range %s with status code %d", range.rangeString(), response.statusCode());
            throw new DownloadFailedException(message);
        }

        byte[] body = response.body();
        DataSegment dataSegment = segments.get(range);
        dataSegment.setSegment(body);
        dataSegment.setComplete(true);
        segments.put(range, dataSegment);
        log.info("finished saving request range {} ", range.rangeString());
    }

    private void saveToFile() throws DownloadFailedException {
        log.info("saving download result for item {} into a file in path {}", name, savePath);

        byte[] bytes = combineDataSegments();
        try (FileOutputStream outputStream = new FileOutputStream(savePath + "/" + name);
             BufferedOutputStream writer = new BufferedOutputStream(outputStream)) {

            writer.write(bytes);
            status = Status.COMPLETED;
        } catch (Exception e) {
            log.error("failed to save byte array to file!!!", e);
            status = Status.ERROR;
            throw new DownloadFailedException("Failed to write the data into file: " + name, e);
        }
        log.info("finished saving download result for item {} into a file in path {}", name, savePath);
    }

    private byte[] combineDataSegments() {
        int totalLength = 0;
        List<byte[]> byteArrays = segments.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .map(DataSegment::getSegment)
                .toList();

        for (byte[] byteArray : byteArrays) {
            totalLength += byteArray.length;
        }

        ByteBuffer buffer = ByteBuffer.allocate(totalLength);

        for (byte[] byteArray : byteArrays) {
            buffer.put(byteArray);
        }

        return buffer.array();
    }

    public void waitForDuration(int duration) throws ExecutionException, InterruptedException, TimeoutException {
        downloadTask.get(duration, TimeUnit.MILLISECONDS);
    }
}
