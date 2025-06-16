package download;

import com.github.sinakarimi81.jdown.common.HttpConstants;
import com.github.sinakarimi81.jdown.dataObjects.Item;
import com.github.sinakarimi81.jdown.dataObjects.ItemInfo;
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
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
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
    public void Given_validUrlPath_When_ContentDispositionIsEmpty_Expect_ItemCreated(@Mock HttpClient client, @Mock HttpResponse<Void> response, @Mock HttpHeaders headers) throws IOException, InterruptedException, FileDataRequestFailedException {
        mockedHttpClient.when(HttpClient::newHttpClient).thenReturn(client);
        when(client.send(any(), eq(HttpResponse.BodyHandlers.discarding()))).thenReturn(response);
        when(response.headers()).thenReturn(headers);
        when(headers.map()).thenReturn(getMockHeaders(List.of("bytes"), List.of("71841045"), List.of("application/octet-stream"), List.of()));


        String fileUrl = "https://example.com/someMovie.mkv";
        String savedAddress = "/apt/movies";
        Item item = manager.createItem(fileUrl, savedAddress);
        ItemInfo info = item.getItemInfo();

        assertEquals("someMovie.mkv", info.getName());
        assertEquals("application/octet-stream", info.getType());
        assertEquals(savedAddress, info.getSavePath());
        assertEquals(fileUrl, info.getDownloadUrl());
        assertTrue(info.getResumable());
        assertEquals(71841045L, info.getSize());
        assertEquals(Status.PAUSED, info.getStatus());
    }

    @Test
    public void Given_validUrlPath_When_ContentDispositionIsNotEmpty_Expect_ItemCreated(@Mock HttpClient client, @Mock HttpResponse<Void> response, @Mock HttpHeaders headers) throws IOException, InterruptedException, FileDataRequestFailedException {
        mockedHttpClient.when(HttpClient::newHttpClient).thenReturn(client);
        when(client.send(any(), eq(HttpResponse.BodyHandlers.discarding()))).thenReturn(response);
        when(response.headers()).thenReturn(headers);
        when(headers.map()).thenReturn(getMockHeaders(List.of("bytes"), List.of("71841045"), List.of("application/octet-stream"), List.of("inline; filename=\"bigMovie.mkv\"")));


        String fileUrl = "https://example.com/someMovie.mkv";
        String savedAddress = "/apt/movies";
        Item item = manager.createItem(fileUrl, savedAddress);
        ItemInfo info = item.getItemInfo();

        assertEquals("bigMovie.mkv", info.getName());
        assertEquals("application/octet-stream", info.getType());
        assertEquals(savedAddress, info.getSavePath());
        assertEquals(fileUrl, info.getDownloadUrl());
        assertTrue(info.getResumable());
        assertEquals(71841045L, info.getSize());
        assertEquals(Status.PAUSED, info.getStatus());
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

    private Map<String, List<String>> getMockHeaders(List<String> ranges, List<String> length, List<String> type, List<String> disposition) {
        return Map.of(
                HttpConstants.ACCEPT_RANGES_HEADER.getValue(), ranges,
                HttpConstants.CONTENT_LENGTH_HEADER.getValue(), length,
                HttpConstants.CONTENT_TYPE_HEADER.getValue(), type,
                HttpConstants.CONTENT_DISPOSITION_HEADER.getValue(), disposition
        );
    }

}
