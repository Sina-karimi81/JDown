package com.github.sinakarimi.jdown.download;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.sinakarimi.jdown.common.HttpUtils;
import com.github.sinakarimi.jdown.dataObjects.Range;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static com.github.sinakarimi.jdown.common.HttpConstants.GET_METHOD;
import static com.github.sinakarimi.jdown.common.HttpConstants.RANGE;

@Slf4j
@ToString
@EqualsAndHashCode
public class DownloadTask {

    @JsonIgnore
    private PausableInputStream pausableInputStream;
    @Getter
    @Setter
    private Range range;
    @Getter
    @Setter
    private boolean completed = false;

    @JsonIgnore
    private Consumer<Integer> updateProgressConsumer;
    @JsonIgnore
    private Consumer<Exception> updateStatusConsumer;

    public DownloadTask() {
    }

    public DownloadTask(Range range, Consumer<Integer> updateProgressConsumer, Consumer<Exception> updateStatusConsumer) {
        this.range = range;
        this.updateProgressConsumer = updateProgressConsumer;
        this.updateStatusConsumer = updateStatusConsumer;
    }

    public void start(String downloadUrl, String path) {
        CompletableFuture.runAsync(() -> {
            HttpURLConnection connection = null;
            FileChannel fileChannel = null;

            try {
                // Open HTTP connection
                URL url = new URL(downloadUrl);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod(GET_METHOD.getValue());
                // set a header
                connection.setRequestProperty(RANGE.getValue(), createRangeHeader());

                // Check for successful response
                int responseCode = connection.getResponseCode();
                if (!HttpUtils.isStatusCode2xx(responseCode)) {
                    String exceptionMessage = String.format("Server returned HTTP response code: %d for range %s", responseCode, range.rangeString());
                    throw new IOException(exceptionMessage);
                }

                // Open file channel in READ_WRITE mode (create if needed)
                Path filePath = Path.of(path);
                fileChannel = FileChannel.open(filePath, StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE);

                // Set the file position where writing will start
                fileChannel.position(range.getFrom());

                // Get input stream from the connection
                pausableInputStream = new PausableInputStream(connection.getInputStream());

                // Buffer for reading data
                ByteBuffer buffer = ByteBuffer.allocate(8192); // 8KB buffer

                // Stream data directly to file
                int bytesRead;
                while ((bytesRead = pausableInputStream.read(buffer.array())) != -1) {
                    // Prepare buffer for writing
                    buffer.limit(bytesRead);
                    fileChannel.write(buffer);
                    buffer.clear();

                    updateProgressConsumer.accept(bytesRead);
                }

                completed = true;
                updateStatusConsumer.accept(null);
            } catch (Exception e) {
                log.error("encountered an error when trying to complete task {}", range.rangeString(), e);
                throw new RuntimeException(e);
            } finally {
                // Close resources in reverse order of creation
                if (pausableInputStream != null) {
                    try {
                        pausableInputStream.close();
                    } catch (IOException e) {
                        // Log or handle error
                        log.error("error occurred while trying to close the input stream!!!", e);
                        updateStatusConsumer.accept(e);
                    }
                }
                if (fileChannel != null) {
                    try {
                        fileChannel.close();
                    } catch (IOException e) {
                        // Log or handle error
                        log.error("error occurred while trying to close the file channel!!!", e);
                        updateStatusConsumer.accept(e);
                    }
                }
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    public void pause() {
        pausableInputStream.pause();
    }

    public void resume() {
        pausableInputStream.resume();
    }

    public void cancel() {
        try {
            pausableInputStream.close();
        } catch (IOException e) {
            log.error("could not cancel the download due to an IO exception when closing input stream", e);
            throw new RuntimeException(e);
        }
    }

    private String createRangeHeader() {
        String rangeValues;
        if (range.getTo() == -1) {
            rangeValues = String.format("bytes=%d-", range.getFrom());
        } else {
            rangeValues = String.format("bytes=%d-%d", range.getFrom(), range.getTo());
        }
        return rangeValues;
    }

}
