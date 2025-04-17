package download;

import com.github.sinakarimi81.jdown.common.HttpConstants;
import com.github.sinakarimi81.jdown.dataObjects.Item;
import com.github.sinakarimi81.jdown.dataObjects.Status;
import com.github.sinakarimi81.jdown.download.ItemManager;
import com.github.sinakarimi81.jdown.exception.FileDataRequestFailedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.*;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ItemMangerTests {

    private final ItemManager manager = new ItemManager();
    private MockedStatic<HttpClient> mockedHttpClient;

    @BeforeEach
    public void setup() {
        mockedHttpClient = mockStatic(HttpClient.class);
    }

    @AfterEach
    public void teardown() {
        mockedHttpClient.close();
    }

    @Test
    public void Given_validUrlPath_When_ContentDispositionIsEmpty_Expect_ItemCreated(@Mock HttpClient client, @Mock HttpResponse<String> response, @Mock HttpHeaders headers) throws IOException, InterruptedException, FileDataRequestFailedException {
        mockedHttpClient.when(HttpClient::newHttpClient).thenReturn(client);
        when(client.send(any(), eq(HttpResponse.BodyHandlers.ofString()))).thenReturn(response);
        when(response.headers()).thenReturn(headers);
        when(headers.map()).thenReturn(getMockHeaders(List.of("bytes"), List.of("71841045"),
                List.of("application/octet-stream"), List.of()));


        String fileUrl = "https://example.com/someMovie.mkv";
        String savedAddress = "/apt/movies";
        Item item = manager.createItem(fileUrl, savedAddress);

        assertEquals("someMovie.mkv", item.getName());
        assertEquals("application/octet-stream", item.getType());
        assertEquals(savedAddress, item.getSavePath());
        assertEquals(fileUrl, item.getDownloadUrl());
        assertTrue(item.getIsResumable());
        assertEquals(71841045L, item.getSize());
        assertEquals(Status.STOP, item.getStatus());
    }

    @Test
    public void Given_validUrlPath_When_ContentDispositionIsNotEmpty_Expect_ItemCreated(@Mock HttpClient client, @Mock HttpResponse<String> response, @Mock HttpHeaders headers) throws IOException, InterruptedException, FileDataRequestFailedException {
        mockedHttpClient.when(HttpClient::newHttpClient).thenReturn(client);
        when(client.send(any(), eq(HttpResponse.BodyHandlers.ofString()))).thenReturn(response);
        when(response.headers()).thenReturn(headers);
        when(headers.map()).thenReturn(getMockHeaders(List.of("bytes"), List.of("71841045"),
                List.of("application/octet-stream"), List.of("inline; filename=\"bigMovie.mkv\"")));


        String fileUrl = "https://example.com/someMovie.mkv";
        String savedAddress = "/apt/movies";
        Item item = manager.createItem(fileUrl, savedAddress);

        assertEquals("bigMovie.mkv", item.getName());
        assertEquals("application/octet-stream", item.getType());
        assertEquals(savedAddress, item.getSavePath());
        assertEquals(fileUrl, item.getDownloadUrl());
        assertTrue(item.getIsResumable());
        assertEquals(71841045L, item.getSize());
        assertEquals(Status.STOP, item.getStatus());
    }

    @Test
    public void When_StatusCode400s_Expect_ExceptionToBeRaised(@Mock HttpClient client, @Mock HttpResponse<String> response) throws IOException, InterruptedException {
        mockedHttpClient.when(HttpClient::newHttpClient).thenReturn(client);
        when(client.send(any(), eq(HttpResponse.BodyHandlers.ofString()))).thenReturn(response);
        when(response.statusCode()).thenReturn(404);


        String fileUrl = "https://example.com/someMovie.mkv";
        String savedAddress = "/apt/movies";
        assertThrows(FileDataRequestFailedException.class, () -> manager.createItem(fileUrl, savedAddress));
    }

    @Test
    public void When_StatusCodeNot500s_Expect_ExceptionToBeRaised(@Mock HttpClient client, @Mock HttpResponse<String> response) throws IOException, InterruptedException {
        mockedHttpClient.when(HttpClient::newHttpClient).thenReturn(client);
        when(client.send(any(), eq(HttpResponse.BodyHandlers.ofString()))).thenReturn(response);
        when(response.statusCode()).thenReturn(500);


        String fileUrl = "https://example.com/someMovie.mkv";
        String savedAddress = "/apt/movies";
        assertThrows(FileDataRequestFailedException.class, () -> manager.createItem(fileUrl, savedAddress));
    }

    @Test
    public void Given_validUrlPath_Expect_FileDataCreated(@Mock HttpClient client, @Mock HttpResponse<byte[]> response, @Mock HttpHeaders headers) throws IOException, InterruptedException, FileDataRequestFailedException, URISyntaxException {
        Path path = Path.of("src/test/resources/testFile.txt");
        byte[] output = Files.readAllBytes(path);


        mockedHttpClient.when(HttpClient::newHttpClient).thenReturn(client);
        when(client.send(any(), eq(HttpResponse.BodyHandlers.ofByteArray()))).thenReturn(response);
        when(response.headers()).thenReturn(headers);
        when(response.body()).thenReturn(output);
        Map<String, List<String>> mockHeaders = getMockHeaders(List.of("bytes"), List.of("72"),
                List.of("application/octet-stream"), List.of("inline; filename=\"downloadedTestFile.txt\""));

        when(headers.map()).thenReturn(mockHeaders);


        String fileUrl = "https://example.com/testFile.txt";
        String savedAddress = "src/test/resources";
        Item item = manager.createItem(fileUrl, savedAddress);
        manager.download(item);

        File f = new File(savedAddress + "/" + item.getName());
        assertTrue(f.exists());
        assertFalse(f.isDirectory());
        assertThat(Files.lines(f.toPath()))
                .containsExactly(Files.lines(path).toList().toArray(String[]::new));
        assertTrue(f.delete());
        assertEquals(Status.COMPLETED, item.getStatus());
    }

    @Test
    public void Given_NullItem_Expect_ExceptionBeRaised() {
        assertThrows(AssertionError.class, () -> manager.download(null));
        assertThrows(AssertionError.class, () -> manager.download(new Item("test", "", Status.STOP, 1L, null, null, true)));
    }

    @Test
    public void given_DownloadItem_When_StatusCode400s_Expect_ExceptionToBeRaised(@Mock HttpClient client, @Mock HttpResponse<byte[]> response) throws IOException, InterruptedException {
        mockedHttpClient.when(HttpClient::newHttpClient).thenReturn(client);
        when(client.send(any(), eq(HttpResponse.BodyHandlers.ofByteArray()))).thenReturn(response);
        when(response.statusCode()).thenReturn(404);

        String fileUrl = "https://example.com/testFile.txt";
        String savedAddress = "src/test/resources";
        Item test = new Item("test", "", Status.STOP, 1L, savedAddress, fileUrl, true);

        assertThrows(FileDataRequestFailedException.class, () -> manager.download(test));
        assertEquals(Status.ERROR, test.getStatus());
    }

    @Test
    public void given_DownloadItem_When_StatusCodeNot500s_Expect_ExceptionToBeRaised(@Mock HttpClient client, @Mock HttpResponse<byte[]> response) throws IOException, InterruptedException {
        mockedHttpClient.when(HttpClient::newHttpClient).thenReturn(client);
        when(client.send(any(), eq(HttpResponse.BodyHandlers.ofByteArray()))).thenReturn(response);
        when(response.statusCode()).thenReturn(500);

        String fileUrl = "https://example.com/testFile.txt";
        String savedAddress = "src/test/resources";
        Item test = new Item("test", "", Status.STOP, 1L, savedAddress, fileUrl, true);

        assertThrows(FileDataRequestFailedException.class, () -> manager.download(test));
        assertEquals(Status.ERROR, test.getStatus());

    }

    private Map<String, List<String>> getMockHeaders(List<String> ranges, List<String> length, List<String> type, List<String> disposition) {
        return Map.of(
                HttpConstants.ACCEPT_RANGES_HEADER.getValue(), ranges,
                HttpConstants.CONTENT_LENGTH_HEADER.getValue(), length,
                HttpConstants.CONTENT_TYPE_HEADER.getValue(), type,
                HttpConstants.CONTENT_DISPOSITION_HEADER.getValue(), disposition
        );
    }

}
