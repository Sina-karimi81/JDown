package com.github.sinakarimi.jdown;

import com.github.sinakarimi.jdown.common.HttpConstants;
import com.github.sinakarimi.jdown.dataObjects.DataSegment;
import com.github.sinakarimi.jdown.dataObjects.Range;
import com.github.sinakarimi.jdown.dataObjects.Status;
import com.github.sinakarimi.jdown.database.DatabaseManager;
import com.github.sinakarimi.jdown.download.DownloadTask;
import com.github.sinakarimi.jdown.exception.DownloadNotResumableException;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class DownloadTaskTests {
    private final DatabaseManager dbManager = DatabaseManager.getInstance("testDb");
    private WireMockServer wireMockServer;

    @BeforeEach
    public void setup() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().stubRequestLoggingDisabled(true).port(9090));
        wireMockServer.start();
        configureFor("localhost", 9090);
    }

    @AfterEach
    public void teardown() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
        dbManager.deleteAll();
    }

    @TempDir
    Path tempDir;

    @Test
    @SuppressWarnings("unchecked")
    void createRanges_ShouldCreateCorrectRangePartitions() throws Exception {
        DownloadTask downloadTask = DownloadTask.builder()
                .name("downloadedTestFile.txt")
                .type("application/octet-stream")
                .status(Status.PAUSED)
                .size(72L)
                .savePath(tempDir.toString())
                .downloadUrl("http://localhost:9090/testFile.txt")
                .resumable(true)
                .build();

        java.lang.reflect.Method createRangesMethod = DownloadTask.class
                .getDeclaredMethod("createRanges", long.class);
        createRangesMethod.setAccessible(true);

        List<Range> invoke = (List<Range>) createRangesMethod.invoke(downloadTask, 72L);
        assertEquals(11, invoke.size());
        assertThat(invoke).extracting(Range::getFrom).containsExactly(0,7,14,21,28,35,42,49,56,63,70);
        assertThat(invoke).extracting(Range::getTo).containsExactly(6,13,20,27,34,41,48,55,62,69,71);
    }

    @Test
    public void Given_DownloadItem_Expect_MultipleAsyncRequestsCreated() throws IOException {
        Path path = Path.of("src/test/resources/testFile.txt");
        byte[] output = Files.readAllBytes(path);

        DownloadTask downloadTask = DownloadTask.builder()
                .name("downloadedTestFile.txt")
                .type("application/octet-stream")
                .status(Status.PAUSED)
                .size(72L)
                .savePath(tempDir.toString())
                .downloadUrl("http://localhost:9090/testFile.txt")
                .resumable(false)
                .build();

        List<Range> ranges = createRanges(downloadTask);
        Map<String, byte[]> data = getStringMap(output, ranges, 72);

        int index = 1;
        for (Range range : ranges) {
            String rangeValues;
            if (range.getTo() >= downloadTask.getSizeProperty().get()) {
                rangeValues = String.format("bytes=%d-", range.getFrom());
            } else {
                rangeValues = String.format("bytes=%d-%d", range.getFrom(), range.getTo());
            }

            stubFor(get(urlEqualTo("/testFile.txt"))
                    .withHeader(HttpConstants.RANGE.getValue(), equalTo(rangeValues))
                    .willReturn(aResponse().withBody(data.get("bytes" + index)).withStatus(206))
            );
            index++;
        }

        downloadTask.start();

        File f = new File(downloadTask.getSavePath() + "/" + downloadTask.getNameProperty().get());

        try (Stream<String> lines = Files.lines(path)) {
            downloadTask.waitForDuration(10000);
            assertThat(Files.lines(f.toPath()))
                    .containsExactly(lines.toList().toArray(String[]::new));
            assertTrue(f.delete());
            assertEquals(Status.COMPLETED, downloadTask.getStatusProperty().get());
        } catch (Exception e) {
            fail("test failed because an exception occurred", e);
        }
    }

    @Test
    public void Given_DownloadTask_Expect_TobeInsertedInDB() throws IOException {
        Path path = Path.of("src/test/resources/testFile.txt");
        byte[] output = Files.readAllBytes(path);

        DownloadTask downloadTask = DownloadTask.builder()
                .name("downloadedTestFile.txt")
                .type("application/octet-stream")
                .status(Status.PAUSED)
                .size(72L)
                .savePath(tempDir.toString())
                .downloadUrl("http://localhost:9090/testFile.txt")
                .resumable(true)
                .build();

        List<Range> ranges = createRanges(downloadTask);
        Map<String, byte[]> data = getStringMap(output, ranges, 72);

        int index = 1;
        for (Range range : ranges) {
            String rangeValues;
            if (range.getTo() >= downloadTask.getSizeProperty().get()) {
                rangeValues = String.format("bytes=%d-", range.getFrom());
            } else {
                rangeValues = String.format("bytes=%d-%d", range.getFrom(), range.getTo());
            }

            stubFor(get(urlEqualTo("/testFile.txt"))
                    .withHeader(HttpConstants.RANGE.getValue(), equalTo(rangeValues))
                    .willReturn(aResponse().withBody(data.get("bytes" + index)).withStatus(206))
            );
            index++;
        }

        downloadTask.start();

        assertDoesNotThrow(() -> dbManager.insert(downloadTask));
    }

    @Test
    public void Given_DownloadTask_Expect_TobeInValidState_When_FetchedFromDB() throws IOException {
        Path path = Path.of("src/test/resources/testFile.txt");
        byte[] output = Files.readAllBytes(path);

        DownloadTask downloadTask = DownloadTask.builder()
                .name("downloadedTestFile.txt")
                .type("application/octet-stream")
                .status(Status.PAUSED)
                .size(72L)
                .savePath(tempDir.toString())
                .downloadUrl("http://localhost:9090/testFile.txt")
                .resumable(true)
                .isPaused(true)
                .build();

        List<Range> ranges = createRanges(downloadTask);
        Map<String, byte[]> data = getStringMap(output, ranges, 72);

        int index = 1;
        for (Range range : ranges) {
            String rangeValues;
            if (range.getTo() >= downloadTask.getSizeProperty().get()) {
                rangeValues = String.format("bytes=%d-", range.getFrom());
            } else {
                rangeValues = String.format("bytes=%d-%d", range.getFrom(), range.getTo());
            }

            stubFor(get(urlEqualTo("/testFile.txt"))
                    .withHeader(HttpConstants.RANGE.getValue(), equalTo(rangeValues))
                    .willReturn(aResponse().withBody(data.get("bytes" + index)).withStatus(206))
            );
            index++;
        }

        downloadTask.start();
        downloadTask.pause();

        assertDoesNotThrow(() -> dbManager.insert(downloadTask));
        assertEquals(Status.PAUSED, downloadTask.getStatusProperty().get());
        Optional<DownloadTask> itemByKey = dbManager.getTaskByKey(downloadTask.getNameProperty().get());
        assertTrue(itemByKey.isPresent());
        DownloadTask fetchedDownloadTask = itemByKey.get();
        ConcurrentMap<Range, DataSegment> segments = fetchedDownloadTask.getSegments();
        Collection<DataSegment> values = segments.values();
        assertThat(segments).containsAllEntriesOf(downloadTask.getSegments());
        assertThat(values).extracting(DataSegment::isComplete).allMatch(ic -> ic.equals(Boolean.FALSE));
        assertThat(values).extracting(DataSegment::getSegment).allMatch(s -> s.length != 0);
    }

    @Test
    public void Given_DownloadItem_When_Paused_Expect_DownloadToBePaused() throws IOException {
        Path path = Path.of("src/test/resources/testFile.txt");
        byte[] output = Files.readAllBytes(path);

        DownloadTask downloadTask = DownloadTask.builder()
                .name("downloadedTestFile.txt")
                .type("application/octet-stream")
                .status(Status.PAUSED)
                .size(72L)
                .savePath(tempDir.toString())
                .downloadUrl("http://localhost:9090/testFile.txt")
                .resumable(true)
                .build();

        List<Range> ranges = createRanges(downloadTask);
        Map<String, byte[]> data = getStringMap(output, ranges, 72);

        int index = 1;
        for (Range range : ranges) {
            String rangeValues;
            if (range.getTo() >= downloadTask.getSizeProperty().get()) {
                rangeValues = String.format("bytes=%d-", range.getFrom());
            } else {
                rangeValues = String.format("bytes=%d-%d", range.getFrom(), range.getTo());
            }

            stubFor(get(urlEqualTo("/testFile.txt"))
                    .withHeader(HttpConstants.RANGE.getValue(), equalTo(rangeValues))
                    .willReturn(aResponse().withBody(data.get("bytes" + index)).withStatus(206))
            );
            index++;
        }

        downloadTask.start();
        downloadTask.pause();

        File f = new File(downloadTask.getSavePath() + "/" + downloadTask.getNameProperty());
        assertEquals(Status.PAUSED, downloadTask.getStatusProperty().get());
        assertTrue(downloadTask.isPaused());
        assertFalse(f.exists());

    }

    @Test
    public void Given_DownloadItem_When_Paused_Then_Resumed_Expect_DownloadToComplete() throws IOException {
        Path path = Path.of("src/test/resources/testFile.txt");
        byte[] output = Files.readAllBytes(path);

        DownloadTask downloadTask = DownloadTask.builder()
                .name("downloadedTestFile.txt")
                .type("application/octet-stream")
                .status(Status.PAUSED)
                .size(72L)
                .savePath(tempDir.toString())
                .downloadUrl("http://localhost:9090/testFile.txt")
                .resumable(true)
                .build();

        List<Range> ranges = createRanges(downloadTask);
        Map<String, byte[]> data = getStringMap(output, ranges, 72);

        int index = 1;
        for (Range range : ranges) {
            String rangeValues;
            if (range.getTo() >= downloadTask.getSizeProperty().get()) {
                rangeValues = String.format("bytes=%d-", range.getFrom());
            } else {
                rangeValues = String.format("bytes=%d-%d", range.getFrom(), range.getTo());
            }

            stubFor(get(urlEqualTo("/testFile.txt"))
                    .withHeader(HttpConstants.RANGE.getValue(), equalTo(rangeValues))
                    .willReturn(aResponse().withBody(data.get("bytes" + index)).withStatus(206))
            );
            index++;
        }

        downloadTask.start();

        downloadTask.pause();

        File f = new File(downloadTask.getSavePath() + "/" + downloadTask.getNameProperty().get());
        assertEquals(Status.PAUSED, downloadTask.getStatusProperty().get());
        assertTrue(downloadTask.isPaused());
        assertFalse(f.exists());

        downloadTask.resume();
        assertFalse(downloadTask.isPaused());

        try (Stream<String> lines = Files.lines(path)) {
            downloadTask.waitForDuration(10000);
            assertThat(Files.lines(f.toPath()))
                    .containsExactly(lines.toList().toArray(String[]::new));
            assertTrue(f.delete());
            assertEquals(Status.COMPLETED, downloadTask.getStatusProperty().get());
        } catch (Exception e) {
            fail("test failed because an exception occurred", e);
        }
    }

    @Test
    public void Given_DownloadItem_When_Cancelled_Expect_DownloadToBeCancelled() throws IOException {
        Path path = Path.of("src/test/resources/testFile.txt");
        byte[] output = Files.readAllBytes(path);

        DownloadTask downloadTask = DownloadTask.builder()
                .name("downloadedTestFile.txt")
                .type("application/octet-stream")
                .status(Status.PAUSED)
                .size(72L)
                .savePath(tempDir.toString())
                .downloadUrl("http://localhost:9090/testFile.txt")
                .resumable(true)
                .build();

        List<Range> ranges = createRanges(downloadTask);
        Map<String, byte[]> data = getStringMap(output, ranges, 72);

        int index = 1;
        for (Range range : ranges) {
            String rangeValues;
            if (range.getTo() >= downloadTask.getSizeProperty().get()) {
                rangeValues = String.format("bytes=%d-", range.getFrom());
            } else {
                rangeValues = String.format("bytes=%d-%d", range.getFrom(), range.getTo());
            }

            stubFor(get(urlEqualTo("/testFile.txt"))
                    .withHeader(HttpConstants.RANGE.getValue(), equalTo(rangeValues))
                    .willReturn(aResponse().withBody(data.get("bytes" + index)).withStatus(206))
            );
            index++;
        }

        downloadTask.start();

        assertEquals(Status.IN_PROGRESS, downloadTask.getStatusProperty().get());

        downloadTask.cancel();

        File f = new File(downloadTask.getSavePath() + "/" + downloadTask.getNameProperty().get());
        assertEquals(Status.CANCELED, downloadTask.getStatusProperty().get());
        assertTrue(downloadTask.isCancelled());
        assertFalse(f.exists());
    }

    @Test
    public void Given_DownloadItem_When_NotResumable_Expect_PausingToThrowException() {
        DownloadTask downloadTask = DownloadTask.builder()
                .name("downloadedTestFile.txt")
                .type("application/octet-stream")
                .status(Status.PAUSED)
                .size(72L)
                .savePath(tempDir.toString())
                .downloadUrl("http://localhost:9090/testFile.txt")
                .resumable(false)
                .isPaused(true)
                .build();

        downloadTask.start();

        assertEquals(Status.IN_PROGRESS, downloadTask.getStatusProperty().get());

        assertThrows(DownloadNotResumableException.class, downloadTask::pause);
    }

    private static Map<String, byte[]> getStringMap(byte[] output, List<Range> ranges, int size) {
        Map<String, byte[]> data = new HashMap<>();

        for (int i = 0; i < ranges.size(); i++) {
            Range range = ranges.get(i);
            data.put("bytes"+ (i + 1), Arrays.copyOfRange(output, range.getFrom(), Math.min(range.getTo() + 1, size)));
        }

        return data;
    }

    @SuppressWarnings("unchecked")
    private List<Range> createRanges(DownloadTask downloadTask) {
        try {
            java.lang.reflect.Method createRangesMethod = DownloadTask.class
                    .getDeclaredMethod("createRanges", long.class);
            createRangesMethod.setAccessible(true);

            List<Range> result = (List<Range>) createRangesMethod.invoke(downloadTask, downloadTask.getSizeProperty().get());
            return result;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
