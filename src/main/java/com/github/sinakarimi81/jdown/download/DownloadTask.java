package com.github.sinakarimi81.jdown.download;

import com.github.sinakarimi81.jdown.common.HttpUtils;
import com.github.sinakarimi81.jdown.configuration.ConfigurationUtils;
import com.github.sinakarimi81.jdown.dataObjects.ItemInfo;
import com.github.sinakarimi81.jdown.dataObjects.Range;
import com.github.sinakarimi81.jdown.dataObjects.Status;
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

import static com.github.sinakarimi81.jdown.common.HttpConstants.GET_METHOD;
import static com.github.sinakarimi81.jdown.common.HttpConstants.RANGE;

@Slf4j
public class DownloadTask {

    private final List<CompletableFuture<Void>> requests;
    private final byte[] data;

    public DownloadTask(int size) {
        this.requests = new ArrayList<>();
        data = new byte[size];
    }

    public CompletableFuture<Void> createAsyncRangeRequests(ItemInfo itemInfo) throws DownloadFailedException {
        log.info("started to create a request for the {}", itemInfo.getName());
        List<Range> ranges = createRanges(itemInfo.getSize());

        HttpClient client = HttpClient.newHttpClient();
        try {
            for (Range range: ranges) {

                String rangeValues;
                if (range.getTo() >= itemInfo.getSize()) {
                    rangeValues = String.format("bytes=%d-", range.getFrom());
                } else {
                    rangeValues = String.format("bytes=%d-%d", range.getFrom(), range.getTo());
                }

                HttpRequest request = HttpRequest
                        .newBuilder(new URI(itemInfo.getDownloadUrl()))
                        .method(GET_METHOD.getValue(), HttpRequest.BodyPublishers.noBody())
                        .header(RANGE.getValue(), rangeValues)
                        .build();

                CompletableFuture<Void> downloadResponse = client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                        .thenAccept(r -> saveToArray(range, r))
                        .exceptionally(ex -> {
                            if (ex != null) {
                                log.error("failed to complete the request!!!", ex);
                                throw new DownloadFailedException(ex);
                            }

                            return null;
                        });

                itemInfo.setStatus(Status.IN_PROGRESS);
                requests.add(downloadResponse);
            }
        } catch (Exception e) {
            log.error("failed create a request for the given item with name {}!!!", itemInfo.getName(), e);
            itemInfo.setStatus(Status.ERROR);
            throw new DownloadFailedException("Failed to download the requested file: " + itemInfo.getName(), e);
        }

        CompletableFuture<Void> resultFuture = CompletableFuture.allOf(requests.toArray(CompletableFuture[]::new))
                .thenAccept(r -> saveToFile(itemInfo, data))
                .exceptionally(ex -> {
                    if (ex != null) {
                        log.error("one of the requests failed to complete!!!", ex);
                        throw new DownloadFailedException(ex);
                    }

                    return null;
                });

        log.info("Finished creating request for {}", itemInfo.getName());
        return resultFuture;
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

    private void saveToFile(ItemInfo itemInfo, byte[] data) throws DownloadFailedException {
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

}
