package com.github.sinakarimi81.jdown.download;

import com.github.sinakarimi81.jdown.common.HttpUtils;
import com.github.sinakarimi81.jdown.dataObjects.Item;
import com.github.sinakarimi81.jdown.dataObjects.Status;
import com.github.sinakarimi81.jdown.exception.FileDataRequestFailedException;

import java.io.*;
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
        downloadItem.setDownloadUrl(url);
        Optional<HttpResponse<byte[]>> itemData = getItemData(url);

        if (itemData.isPresent()) {
            HttpResponse<byte[]> headRequestResponse = itemData.get();

            if (400 <= headRequestResponse.statusCode()) {
                throw new FileDataRequestFailedException("HEAD request for fetching file data failed with status: " + headRequestResponse.statusCode());
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
            downloadItem.setSavePath(savedAddress);
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

    private Optional<HttpResponse<byte[]>> getItemData(String fileUrl) throws FileDataRequestFailedException {
        HttpResponse<byte[]> response;
        try {
            HttpClient client = HttpClient.newHttpClient();

            HttpRequest request = HttpRequest
                    .newBuilder(new URI(fileUrl))
                    .method(HEAD_METHOD.getValue(), HttpRequest.BodyPublishers.noBody())
                    .build();

            response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (Exception e) {
            throw new FileDataRequestFailedException("Failed to fetch the requested file data", e);
        }

        return Optional.ofNullable(response);
    }

    public void download(Item item) throws FileDataRequestFailedException {
        assert item != null : "Provided Item is null";
        assert item.getDownloadUrl() != null && item.getSavePath() != null: "Provided Item with ID: " + item.getName() + " does not have a URL or Save address";

        HttpClient client = HttpClient.newHttpClient();

        HttpResponse<byte[]> response;
        try {
            HttpRequest request = HttpRequest
                    .newBuilder(new URI(item.getDownloadUrl()))
                    .method(GET_METHOD.getValue(), HttpRequest.BodyPublishers.noBody())
                    .build();

            response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (Exception e) {
            item.setStatus(Status.ERROR);
            throw new FileDataRequestFailedException("Failed to download the requested file: " + item.getName(), e);
        }

        if (HttpUtils.isStatusCode4xx(response) || HttpUtils.isStatusCode5xx(response)) {
            item.setStatus(Status.ERROR);
            throw new FileDataRequestFailedException("Failed to download file data with status code: " + response.statusCode());
        }

        try (FileOutputStream outputStream = new FileOutputStream(item.getSavePath() + "/" + item.getName());
             BufferedOutputStream writer = new BufferedOutputStream(outputStream)) {

            writer.write(response.body());
        } catch (Exception e) {
            item.setStatus(Status.ERROR);
            throw new FileDataRequestFailedException("Failed to write the data into file: " + item.getName(), e);
        }

        item.setStatus(Status.COMPLETED);
    }

}
