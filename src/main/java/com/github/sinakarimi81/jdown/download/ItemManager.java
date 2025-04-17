package com.github.sinakarimi81.jdown.download;

import com.github.sinakarimi81.jdown.dataObjects.Item;
import com.github.sinakarimi81.jdown.dataObjects.Status;
import com.github.sinakarimi81.jdown.db.ItemDAO;
import com.github.sinakarimi81.jdown.exception.FileDataRequestFailedException;

import java.lang.ref.PhantomReference;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.github.sinakarimi81.jdown.common.HttpConstants.*;

public class ItemManager {

    public Item createItem(String url, String savedAddress) throws FileDataRequestFailedException {
        Item downloadItem = new Item();
        downloadItem.setDownloadAddress(url);
        Optional<HttpResponse<String>> itemData = getItemData(url);

        if (itemData.isPresent()) {
            HttpResponse<String> headRequestResponse = itemData.get();

            if (400 <= headRequestResponse.statusCode()) {
                throw new FileDataRequestFailedException("HEAD request for fetching file data failed with status: " + headRequestResponse.statusCode() + "\nWith body of: " + headRequestResponse.body());
            }

            Map<String, List<String>> headers = headRequestResponse.headers().map();

            List<String> acceptRanges = headers.get(ACCEPT_RANGES_HEADER.getValue());
            if (acceptRanges != null && !acceptRanges.isEmpty()) {
                downloadItem.setIsResumable(true);
            }

            List<String> contentLength = headers.get(CONTENT_LENGTH_HEADER.getValue());
            if (contentLength != null && !contentLength.isEmpty()) {
                downloadItem.setSize(Long.valueOf(contentLength.get(0)));
            }

            List<String> contentType = headers.get(CONTENT_TYPE_HEADER.getValue());
            if (contentType != null && !contentType.isEmpty()) {
                downloadItem.setType(contentType.get(0));
            }

            String fileName = getFileName(url, headers);
            downloadItem.setName(fileName);
            downloadItem.setStatus(Status.STOP);
            downloadItem.setSavedAddress(savedAddress);
        }

        return downloadItem;
    }

    private String getFileName(String url, Map<String, List<String>> headers) {
        List<String> contentDisposition = headers.get(CONTENT_DISPOSITION_HEADER.getValue());
        if (contentDisposition != null && !contentDisposition.isEmpty() && contentDisposition.get(0).contains(FILENAME_TAG.getValue())) {
            String[] split = contentDisposition.get(0).split(FILENAME_TAG.getValue());
            return split[1].substring(1).replace("\"", "");
        } else {
            String[] split = url.split("/");
            return split[split.length - 1];
        }
    }

    private Optional<HttpResponse<String>> getItemData(String fileUrl) {
        HttpResponse<String> response;
        try {
            HttpClient client = HttpClient.newHttpClient();

            HttpRequest request = HttpRequest
                    .newBuilder(new URI(fileUrl))
                    .method(HEAD_METHOD.getValue(), HttpRequest.BodyPublishers.noBody())
                    .build();

            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return Optional.ofNullable(response);
    }

}
