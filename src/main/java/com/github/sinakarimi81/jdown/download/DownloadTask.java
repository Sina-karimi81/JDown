package com.github.sinakarimi81.jdown.download;

import com.github.sinakarimi81.jdown.common.HttpUtils;
import com.github.sinakarimi81.jdown.configuration.ConfigurationUtils;
import com.github.sinakarimi81.jdown.dataObjects.ItemInfo;
import com.github.sinakarimi81.jdown.dataObjects.Range;
import com.github.sinakarimi81.jdown.dataObjects.Status;
import com.github.sinakarimi81.jdown.exception.CancelationFailedException;
import com.github.sinakarimi81.jdown.exception.DownloadFailedException;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.github.sinakarimi81.jdown.common.HttpConstants.GET_METHOD;
import static com.github.sinakarimi81.jdown.common.HttpConstants.RANGE;

@Slf4j
public class DownloadTask {

    private CompletableFuture<Void> downloadTask;
    private final ItemInfo itemInfo;
    private final byte[] data;
    private final Object lock = new Object();
    private volatile boolean isPaused = false;

    public DownloadTask(ItemInfo itemInfo) {
        this.itemInfo = itemInfo;
        data = new byte[itemInfo.getSize().intValue()];
    }

    public void cancel() {
        log.info("cancelling the download of file {}", itemInfo.getName());
        boolean cancel = downloadTask.cancel(true);
        if (cancel) {
            itemInfo.setStatus(Status.CANCELED);
            log.info("cancelled the download of file {} successfully", itemInfo.getName());
        } else {
            log.error("failed to cancel the download of file {} successfully", itemInfo.getName());
            throw new CancelationFailedException("Failed to cancel the download!");
        }
    }

    public void pause() {
        synchronized (lock) {
            isPaused = true;
            itemInfo.setStatus(Status.PAUSED);
            log.info("set item {} to paused status, isPaused: {}", itemInfo.getName(), isPaused);
        }
    }

    public void resume() {
        isPaused = false;
        itemInfo.setStatus(Status.IN_PROGRESS);
        log.info("set item {} to in progress status, isPaused: {}", itemInfo.getName(), isPaused);
    }

    public boolean isPaused() {
        return isPaused;
    }

    public boolean isCancelled() {
        return downloadTask.isCancelled();
    }

    public void start() throws DownloadFailedException {
        List<CompletableFuture<Void>> requests= new ArrayList<>();

        log.info("started to create a request for the {}", itemInfo.getName());
        List<Range> ranges = createRanges(itemInfo.getSize());

        for (Range range: ranges) {
            CompletableFuture<Void> downloadPartition = createPartitionedDownloadTasks(range);
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

        itemInfo.setStatus(Status.IN_PROGRESS);
        log.info("Finished creating request for {}", itemInfo.getName());
    }

    private CompletableFuture<Void> createPartitionedDownloadTasks(Range range) {
        return CompletableFuture.runAsync(() -> {
            try {

                String rangeValues;
                if (range.getTo() >= itemInfo.getSize()) {
                    rangeValues = String.format("bytes=%d-", range.getFrom());
                } else {
                    rangeValues = String.format("bytes=%d-%d", range.getFrom(), range.getTo());
                }

                while (isPaused) {
                    log.info("inside the pause loop for item {}, isPaused: {} Thread: {}", itemInfo.getName(), isPaused, Thread.currentThread().getName());
                    Thread.onSpinWait(); // just used for performance by the JVM, no additional functionality
                }

                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest
                        .newBuilder(new URI(itemInfo.getDownloadUrl()))
                        .method(GET_METHOD.getValue(), HttpRequest.BodyPublishers.noBody())
                        .header(RANGE.getValue(), rangeValues)
                        .build();

                HttpResponse<byte[]> send = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
                saveToArray(range, send);
            } catch (Exception e) {
                log.error("failed create a request for the given item with name {}!!!", itemInfo.getName(), e);
                itemInfo.setStatus(Status.ERROR);
                throw new DownloadFailedException("Failed to download the requested file: " + itemInfo.getName(), e);
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
        log.info("saving request range {} with status {} to byte array", range.toString(), response.statusCode());
        if (!HttpUtils.isStatusCode2xx(response)) {
            String message = String.format("Failed to fetch data for range %d to %d with status code %d", range.getFrom(), range.getTo(), response.statusCode());
            throw new DownloadFailedException(message);
        }

        byte[] body = response.body();
        System.arraycopy(body, 0, data, range.getFrom(), (range.getTo()-range.getFrom()+1));
        log.info("finished saving request range {} ", range);
    }

    private void saveToFile() throws DownloadFailedException {
        log.info("saving download result for item {} into a file in path {}", itemInfo.getName(), itemInfo.getSavePath());
        try (FileOutputStream outputStream = new FileOutputStream(itemInfo.getSavePath() + "/" + itemInfo.getName());
             BufferedOutputStream writer = new BufferedOutputStream(outputStream)) {

            writer.write(data);
            itemInfo.setStatus(Status.COMPLETED);
        } catch (Exception e) {
            log.error("failed to save byte array to file!!!", e);
            itemInfo.setStatus(Status.ERROR);
            throw new DownloadFailedException("Failed to write the data into file: " + itemInfo.getName(), e);
        }
        log.info("finished saving download result for item {} into a file in path {}", itemInfo.getName(), itemInfo.getSavePath());
    }

    public void waitForDuration(int duration) throws ExecutionException, InterruptedException, TimeoutException {
        downloadTask.get(duration, TimeUnit.MILLISECONDS);
    }
}
