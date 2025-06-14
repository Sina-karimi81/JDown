package download;

import com.github.sinakarimi81.jdown.common.HttpConstants;
import com.github.sinakarimi81.jdown.dataObjects.Item;
import com.github.sinakarimi81.jdown.dataObjects.ItemInfo;
import com.github.sinakarimi81.jdown.dataObjects.Range;
import com.github.sinakarimi81.jdown.dataObjects.Status;
import com.github.sinakarimi81.jdown.download.DownloadTask;
import com.github.sinakarimi81.jdown.exception.DownloadFailedException;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class DownloadTaskTests {
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
    }

    @TempDir
    Path tempDir;

    @Test
    void saveToArray_ShouldHandleSuccessfulResponse(@Mock HttpResponse<byte[]> response) throws Exception {
        // Arrange

        DownloadTask downloadTask = new DownloadTask(72);
        Path path = Path.of("src/test/resources/testFile.txt");
        byte[] output = Files.readAllBytes(path);

        byte[] responseData = Arrays.copyOfRange(output, 0, 8);
        Range testRange = new Range(0, 8);

        when(response.body()).thenReturn(responseData);
        when(response.statusCode()).thenReturn(206); // Partial Content

        try {
            // Use reflection to test private method
            java.lang.reflect.Method saveToArrayMethod = DownloadTask.class.getDeclaredMethod("saveToArray", Range.class, HttpResponse.class);
            saveToArrayMethod.setAccessible(true);

            // Act
            assertDoesNotThrow(() -> saveToArrayMethod.invoke(downloadTask, testRange, response));
        } catch (Exception e) {
            fail("test failed because of exception", e);
        }
    }

    @Test
    void saveToArray_ShouldThrowExceptionOnBadStatusCode(@Mock HttpResponse<byte[]> response) throws Exception {
        // Arrange
        DownloadTask downloadTask = new DownloadTask(72);
        Range testRange = new Range(0, 10);
        when(response.statusCode()).thenReturn(404);

        try {

            // Use reflection to test private method
            java.lang.reflect.Method saveToArrayMethod = DownloadTask.class
                    .getDeclaredMethod("saveToArray", Range.class, HttpResponse.class);
            saveToArrayMethod.setAccessible(true);

            // Act & Assert
            Exception exception = assertThrows(Exception.class, () -> {
                saveToArrayMethod.invoke(downloadTask, testRange, response);
            });

            assertInstanceOf(DownloadFailedException.class, exception.getCause());
        } catch (Exception e) {
            fail("test failed because of exception", e);
        }
    }

    @Test
    void saveToFile_ShouldCreateFileSuccessfully(@Mock ItemInfo mockItemInfo) throws Exception {
        // Arrange
        String savePath = tempDir.toString();
        DownloadTask downloadTask = new DownloadTask(72);
        Path path = Path.of("src/test/resources/testFile.txt");
        byte[] output = Files.readAllBytes(path);

        when(mockItemInfo.getSavePath()).thenReturn(savePath);
        when(mockItemInfo.getName()).thenReturn("resultTestFile.txt");

        // Use reflection to test private method
        java.lang.reflect.Method saveToFileMethod = DownloadTask.class
                .getDeclaredMethod("saveToFile", ItemInfo.class, byte[].class);
        saveToFileMethod.setAccessible(true);

        // Act
        assertDoesNotThrow(() -> saveToFileMethod.invoke(downloadTask, mockItemInfo, output));

        // Assert
        File createdFile = new File(savePath, "resultTestFile.txt");
        assertTrue(createdFile.exists());
        assertEquals(output.length, createdFile.length());
        verify(mockItemInfo).setStatus(Status.COMPLETED);

        // Verify file content
        byte[] fileContent = Files.readAllBytes(createdFile.toPath());
        assertArrayEquals(output, fileContent);
    }

    @Test
    void saveToFile_ShouldSetErrorStatusOnIOException(@Mock ItemInfo mockItemInfo) throws Exception {
        // Arrange
        DownloadTask downloadTask = new DownloadTask(72);
        String invalidPath = "/invalid/path/that/does/not/exist";
        byte[] testData = "test data".getBytes();

        when(mockItemInfo.getSavePath()).thenReturn(invalidPath);
        when(mockItemInfo.getName()).thenReturn("resultTestFile.txt");

        // Use reflection to test private method
        java.lang.reflect.Method saveToFileMethod = DownloadTask.class
                .getDeclaredMethod("saveToFile", ItemInfo.class, byte[].class);
        saveToFileMethod.setAccessible(true);

        // Act & Assert
        Exception exception = assertThrows(Exception.class, () -> saveToFileMethod.invoke(downloadTask, mockItemInfo, testData));

        assertInstanceOf(DownloadFailedException.class, exception.getCause());
        verify(mockItemInfo).setStatus(Status.ERROR);
    }

    @Test
    void createRanges_ShouldCreateCorrectRangePartitions() throws Exception {
        DownloadTask downloadTask = new DownloadTask(72);

        java.lang.reflect.Method createRangesMethod = DownloadTask.class
                .getDeclaredMethod("createRanges", long.class);
        createRangesMethod.setAccessible(true);

        List<Range> invoke = (List<Range>) createRangesMethod.invoke(downloadTask, 72L);
        assertEquals(11, invoke.size());
        assertThat(invoke).extracting(Range::getFrom).containsExactly(0,7,14,21,28,35,42,49,56,63,70);
        assertThat(invoke).extracting(Range::getTo).containsExactly(6,13,20,27,34,41,48,55,62,69,71);
    }

    @Test
    public void Given_DownloadItem_Expect_MultipleAsyncRequestsCreated() throws IOException, InterruptedException, ExecutionException {
        Path path = Path.of("src/test/resources/testFile.txt");
        byte[] output = Files.readAllBytes(path);

        Item item = new Item();
        ItemInfo info = new ItemInfo("downloadedTestFile.txt", "application/octet-stream", Status.STOP, 72L, tempDir.toString(), "http://localhost:9090/testFile.txt", true);
        item.setItemInfo(info);
        DownloadTask downloadTask = new DownloadTask(72);

        List<Range> ranges = createRanges(info.getSize(), downloadTask);
        Map<String, byte[]> data = getStringMap(output, ranges, 72);

        int index = 1;
        for (Range range : ranges) {
            String rangeValues;
            if (range.getTo() >= info.getSize()) {
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

        CompletableFuture<Void> asyncRangeRequests = downloadTask.createAsyncRangeRequests(item.getItemInfo());

        File f = new File(info.getSavePath() + "/" + item.getItemInfo().getName());

        try (Stream<String> lines = Files.lines(path)) {
            asyncRangeRequests.get(10000, TimeUnit.MILLISECONDS);
            assertThat(Files.lines(f.toPath()))
                    .containsExactly(lines.toList().toArray(String[]::new));
            assertTrue(f.delete());
            assertEquals(Status.COMPLETED, item.getItemInfo().getStatus());
        } catch (Exception e) {
            fail("test failed because an exception occurred", e);
        }
    }

    private static Map<String, byte[]> getStringMap(byte[] output, List<Range> ranges, int size) {
        Map<String, byte[]> data = new HashMap<>();

        for (int i = 0; i < ranges.size(); i++) {
            Range range = ranges.get(i);
            data.put("bytes"+ (i + 1), Arrays.copyOfRange(output, range.getFrom(), Math.min(range.getTo() + 1, size)));
        }

        return data;
    }

    private List<Range> createRanges(long size, DownloadTask downloadTask) {
        try {
            java.lang.reflect.Method createRangesMethod = DownloadTask.class
                    .getDeclaredMethod("createRanges", long.class);
            createRangesMethod.setAccessible(true);

            List<Range> result = (List<Range>) createRangesMethod.invoke(downloadTask, size);
            return result;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
