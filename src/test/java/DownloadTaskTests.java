import com.github.sinakarimi81.jdown.common.HttpConstants;
import com.github.sinakarimi81.jdown.dataObjects.*;
import com.github.sinakarimi81.jdown.database.DatabaseManager;
import com.github.sinakarimi81.jdown.download.DownloadTask;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
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
    void createRanges_ShouldCreateCorrectRangePartitions() throws Exception {
        Item item = new Item();
        ItemInfo info = new ItemInfo("downloadedTestFile.txt", "application/octet-stream", Status.PAUSED, 72L, tempDir.toString(), "http://localhost:9090/testFile.txt", true);
        item.setItemInfo(info);
        DownloadTask downloadTask = new DownloadTask(item.getItemInfo());

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
        ItemInfo info = new ItemInfo("downloadedTestFile.txt", "application/octet-stream", Status.PAUSED, 72L, tempDir.toString(), "http://localhost:9090/testFile.txt", true);
        item.setItemInfo(info);
        DownloadTask downloadTask = new DownloadTask(item.getItemInfo(), false);

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

        downloadTask.start();

        File f = new File(info.getSavePath() + "/" + item.getItemInfo().getName());

        try (Stream<String> lines = Files.lines(path)) {
            downloadTask.waitForDuration(10000);
            assertThat(Files.lines(f.toPath()))
                    .containsExactly(lines.toList().toArray(String[]::new));
            assertTrue(f.delete());
            assertEquals(Status.COMPLETED, item.getItemInfo().getStatus());
        } catch (Exception e) {
            fail("test failed because an exception occurred", e);
        }
    }

    @Test
    public void Given_DownloadTask_Expect_TobeInsertedInDB() throws IOException, InterruptedException, ExecutionException {
        Path path = Path.of("src/test/resources/testFile.txt");
        byte[] output = Files.readAllBytes(path);

        Item item = new Item();
        ItemInfo info = new ItemInfo("downloadedTestFile.txt", "application/octet-stream", Status.PAUSED, 72L, tempDir.toString(), "http://localhost:9090/testFile.txt", true);
        item.setItemInfo(info);
        DownloadTask downloadTask = new DownloadTask(item.getItemInfo());

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

        downloadTask.start();

        item.setDownloadTask(downloadTask);

        assertDoesNotThrow(() -> dbManager.insert(item));
    }

    @Test
    public void Given_DownloadTask_Expect_TobeInValidState_When_FetchedFromDB() throws IOException, InterruptedException, ExecutionException {
        Path path = Path.of("src/test/resources/testFile.txt");
        byte[] output = Files.readAllBytes(path);

        Item item = new Item();
        ItemInfo info = new ItemInfo("downloadedTestFile.txt", "application/octet-stream", Status.PAUSED, 72L, tempDir.toString(), "http://localhost:9090/testFile.txt", true);
        item.setItemInfo(info);
        DownloadTask downloadTask = new DownloadTask(item.getItemInfo());

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

        downloadTask.start();
        downloadTask.pause();

        item.setDownloadTask(downloadTask);

        assertDoesNotThrow(() -> dbManager.insert(item));
        assertEquals(Status.PAUSED, item.getItemInfo().getStatus());
        Optional<Item> itemByKey = dbManager.getItemByKey(item.getItemInfo().getName());
        assertTrue(itemByKey.isPresent());
        Item fetchedItem = itemByKey.get();
        ConcurrentMap<Range, DataSegment> segments = fetchedItem.getDownloadTask().getSegments();
        Collection<DataSegment> values = segments.values();
        assertThat(segments).containsAllEntriesOf(downloadTask.getSegments());
        assertThat(values).extracting(DataSegment::isComplete).allMatch(ic -> ic.equals(Boolean.FALSE));
        assertThat(values).extracting(DataSegment::getSegment).allMatch(s -> s.length != 0);
    }

    @Test
    public void Given_DownloadItem_When_Paused_Expect_DownloadToBePaused() throws IOException, InterruptedException, ExecutionException, TimeoutException {
        Path path = Path.of("src/test/resources/testFile.txt");
        byte[] output = Files.readAllBytes(path);

        Item item = new Item();
        ItemInfo info = new ItemInfo("downloadedTestFile.txt", "application/octet-stream", Status.PAUSED, 72L, tempDir.toString(), "http://localhost:9090/testFile.txt", true);
        item.setItemInfo(info);
        DownloadTask downloadTask = new DownloadTask(item.getItemInfo());

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

        downloadTask.start();
        downloadTask.pause();

        File f = new File(info.getSavePath() + "/" + item.getItemInfo().getName());
        assertEquals(Status.PAUSED, item.getItemInfo().getStatus());
        assertTrue(downloadTask.isPaused());
        assertFalse(f.exists());

    }

    @Test
    public void Given_DownloadItem_When_Paused_Then_Resumed_Expect_DownloadToComplete() throws IOException, InterruptedException, ExecutionException {
        Path path = Path.of("src/test/resources/testFile.txt");
        byte[] output = Files.readAllBytes(path);

        Item item = new Item();
        ItemInfo info = new ItemInfo("downloadedTestFile.txt", "application/octet-stream", Status.PAUSED, 72L, tempDir.toString(), "http://localhost:9090/testFile.txt", true);
        item.setItemInfo(info);
        DownloadTask downloadTask = new DownloadTask(item.getItemInfo());

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

        downloadTask.start();

        downloadTask.pause();

        File f = new File(info.getSavePath() + "/" + item.getItemInfo().getName());
        assertEquals(Status.PAUSED, item.getItemInfo().getStatus());
        assertTrue(downloadTask.isPaused());
        assertFalse(f.exists());

        downloadTask.resume();
        assertFalse(downloadTask.isPaused());

        try (Stream<String> lines = Files.lines(path)) {
            downloadTask.waitForDuration(10000);
            assertThat(Files.lines(f.toPath()))
                    .containsExactly(lines.toList().toArray(String[]::new));
            assertTrue(f.delete());
            assertEquals(Status.COMPLETED, item.getItemInfo().getStatus());
        } catch (Exception e) {
            fail("test failed because an exception occurred", e);
        }
    }

    @Test
    public void Given_DownloadItem_When_Cancelled_Expect_DownloadToBeCancelled() throws IOException, InterruptedException, ExecutionException, TimeoutException {
        Path path = Path.of("src/test/resources/testFile.txt");
        byte[] output = Files.readAllBytes(path);

        Item item = new Item();
        ItemInfo info = new ItemInfo("downloadedTestFile.txt", "application/octet-stream", Status.PAUSED, 72L, tempDir.toString(), "http://localhost:9090/testFile.txt", true);
        item.setItemInfo(info);
        DownloadTask downloadTask = new DownloadTask(item.getItemInfo());

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

        downloadTask.start();

        assertEquals(Status.IN_PROGRESS, item.getItemInfo().getStatus());

        downloadTask.cancel();

        File f = new File(info.getSavePath() + "/" + item.getItemInfo().getName());
        assertEquals(Status.CANCELED, item.getItemInfo().getStatus());
        assertTrue(downloadTask.isCancelled());
        assertFalse(f.exists());

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
