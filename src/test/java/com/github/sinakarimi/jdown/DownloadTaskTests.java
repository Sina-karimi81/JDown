package com.github.sinakarimi.jdown;

import com.github.sinakarimi.jdown.common.HttpConstants;
import com.github.sinakarimi.jdown.configuration.ConfigurationConstants;
import com.github.sinakarimi.jdown.configuration.ConfigurationUtils;
import com.github.sinakarimi.jdown.dataObjects.Range;
import com.github.sinakarimi.jdown.dataObjects.Status;
import com.github.sinakarimi.jdown.database.TasksDAO;
import com.github.sinakarimi.jdown.download.Download;
import com.github.sinakarimi.jdown.download.DownloadTask;
import com.github.sinakarimi.jdown.exception.DownloadNotResumableException;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import javafx.beans.property.SimpleObjectProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
public class DownloadTaskTests {
    private final TasksDAO dbManager = TasksDAO.getInstance("testDb");
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
        Download downloadTask = Download.builder()
                .name("downloadedTestFile.txt")
                .type("application/octet-stream")
                .statusProperty(new SimpleObjectProperty<>(Status.PAUSED))
                .size(72L)
                .savePath(tempDir.toString())
                .downloadUrl("http://localhost:9090/testFile.txt")
                .resumable(true)
                .build();

        java.lang.reflect.Method createRangesMethod = Download.class
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

        Download downloadTask = Download.builder()
                .name("downloadedTestFile.txt")
                .type("application/octet-stream")
                .statusProperty(new SimpleObjectProperty<>(Status.PAUSED))
                .size(72L)
                .savePath(tempDir.toString())
                .downloadUrl("http://localhost:9090/testFile.txt")
                .resumable(false)
                .tasksDAO(dbManager)
                .build();

        List<Range> ranges = createRanges(downloadTask);
        Map<String, byte[]> data = getStringMap(output, ranges, 72);

        int index = 1;
        for (Range range : ranges) {
            String rangeValues;
            if (range.getTo() >= downloadTask.getSize()) {
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

        File f = new File(downloadTask.getSavePath() + "/" + downloadTask.getName());

        try (Stream<String> lines = Files.lines(path)) {
            Thread.sleep(5000);
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

        Download downloadTask = Download.builder()
                .name("downloadedTestFile.txt")
                .type("application/octet-stream")
                .statusProperty(new SimpleObjectProperty<>(Status.PAUSED))
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
            if (range.getTo() >= downloadTask.getSize()) {
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
//        MockedStatic<ConfigurationUtils> configurationUtilsMockedStatic = Mockito.mockStatic(ConfigurationUtils.class);
//        configurationUtilsMockedStatic.when(() -> ConfigurationUtils.getConfig(eq(ConfigurationConstants.NUMBER_OF_THREADS), eq(Integer.class))).thenReturn(1);

        Path path = Path.of("src/test/resources/testFile.txt");
        byte[] output = Files.readAllBytes(path);

        Download downloadTask = Download.builder()
                .name("downloadedTestFile.txt")
                .type("application/octet-stream")
                .statusProperty(new SimpleObjectProperty<>())
                .size(72L)
                .savePath(tempDir.toString())
                .downloadUrl("http://localhost:9090/testFile.txt")
                .resumable(true)
                .tasksDAO(dbManager)
                .build();

        List<Range> ranges = createRanges(downloadTask);
        Map<String, byte[]> data = getStringMap(output, ranges, 72);

        int index = 1;
        for (Range range : ranges) {
            String rangeValues;
            if (range.getTo() >= downloadTask.getSize()) {
                rangeValues = String.format("bytes=%d-", range.getFrom());
            } else {
                rangeValues = String.format("bytes=%d-%d", range.getFrom(), range.getTo());
            }

            stubFor(get(urlEqualTo("/testFile.txt"))
                    .withHeader(HttpConstants.RANGE.getValue(), equalTo(rangeValues))
                    .willReturn(aResponse()
                            .withBody(data.get("bytes" + index))
                            .withStatus(206)
                    )
            );
            index++;
        }

        downloadTask.start();
        downloadTask.pause();

        assertDoesNotThrow(() -> dbManager.insert(downloadTask));
        assertEquals(Status.PAUSED, downloadTask.getStatusProperty().get());
        Optional<Download> itemByKey = dbManager.getTaskByKey(downloadTask.getName());
        assertTrue(itemByKey.isPresent());
        Download fetchedDownload = itemByKey.get();

        List<DownloadTask> downloadTasks = fetchedDownload.getDownloadTasks();

        List<Boolean> bools = downloadTask.getDownloadTasks().stream().map(DownloadTask::isCompleted).toList();
        assertThat(downloadTasks).extracting(DownloadTask::isCompleted).containsExactly(bools.toArray(new Boolean[0]));
        assertThat(downloadTasks).allMatch(ic -> ic.isCompleted() == Boolean.FALSE);
    }

    @Test
    public void Given_DownloadItem_When_Paused_Expect_DownloadToBePaused() throws IOException {
        Path path = Path.of("src/test/resources/testFile.txt");
        byte[] output = Files.readAllBytes(path);

        Download downloadTask = Download.builder()
                .name("downloadedTestFile.txt")
                .type("application/octet-stream")
                .statusProperty(new SimpleObjectProperty<>(Status.PAUSED))
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
            if (range.getTo() >= downloadTask.getSize()) {
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

        File f = new File(downloadTask.getSavePath() + "/" + downloadTask.getName());
        assertEquals(Status.PAUSED, downloadTask.getStatusProperty().get());
        assertFalse(f.exists());

    }

    @Test
    public void Given_DownloadItem_When_Paused_Then_Resumed_Expect_DownloadToComplete() throws IOException {
        Path path = Path.of("src/test/resources/testFile.txt");
        byte[] output = Files.readAllBytes(path);

        Download downloadTask = Download.builder()
                .name("downloadedTestFile.txt")
                .type("application/octet-stream")
                .statusProperty(new SimpleObjectProperty<>(Status.PAUSED))
                .size(72L)
                .savePath(tempDir.toString())
                .downloadUrl("http://localhost:9090/testFile.txt")
                .resumable(true)
                .tasksDAO(dbManager)
                .build();

        List<Range> ranges = createRanges(downloadTask);
        Map<String, byte[]> data = getStringMap(output, ranges, 72);

        int index = 1;
        for (Range range : ranges) {
            String rangeValues;
            if (range.getTo() >= downloadTask.getSize()) {
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

        File f = new File(downloadTask.getSavePath() + "/" + downloadTask.getName());
        assertEquals(Status.PAUSED, downloadTask.getStatusProperty().get());
        assertFalse(f.exists());

        downloadTask.resume();
        assertEquals(Status.IN_PROGRESS, downloadTask.getStatusProperty().get());

        try (Stream<String> lines = Files.lines(path)) {
            Thread.sleep(5000);
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

        Download downloadTask = Download.builder()
                .name("downloadedTestFile.txt")
                .type("application/octet-stream")
                .statusProperty(new SimpleObjectProperty<>(Status.PAUSED))
                .size(72L)
                .savePath(tempDir.toString())
                .downloadUrl("http://localhost:9090/testFile.txt")
                .resumable(true)
                .tasksDAO(dbManager)
                .build();

        List<Range> ranges = createRanges(downloadTask);
        Map<String, byte[]> data = getStringMap(output, ranges, 72);

        int index = 1;
        for (Range range : ranges) {
            String rangeValues;
            if (range.getTo() >= downloadTask.getSize()) {
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

        File f = new File(downloadTask.getSavePath() + "/" + downloadTask.getName());
        assertEquals(Status.CANCELED, downloadTask.getStatusProperty().get());
        assertFalse(f.exists());
    }

    @Test
    public void Given_DownloadItem_When_NotResumable_Expect_PausingToThrowException() {
        Download downloadTask = Download.builder()
                .name("downloadedTestFile.txt")
                .type("application/octet-stream")
                .statusProperty(new SimpleObjectProperty<>(Status.PAUSED))
                .size(72L)
                .savePath(tempDir.toString())
                .downloadUrl("http://localhost:9090/testFile.txt")
                .resumable(false)
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
    private List<Range> createRanges(Download downloadTask) {
        try {
            java.lang.reflect.Method createRangesMethod = Download.class
                    .getDeclaredMethod("createRanges", long.class);
            createRangesMethod.setAccessible(true);

            List<Range> result = (List<Range>) createRangesMethod.invoke(downloadTask, downloadTask.getSize());
            return result;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
