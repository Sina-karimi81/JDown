package com.github.sinakarimi81.jdown.download;

import com.github.sinakarimi81.jdown.dataObjects.Item;
import com.github.sinakarimi81.jdown.dataObjects.ItemInfo;
import com.github.sinakarimi81.jdown.dataObjects.Status;
import com.github.sinakarimi81.jdown.database.DatabaseManager;
import com.github.sinakarimi81.jdown.exception.FileDataRequestFailedException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.github.sinakarimi81.jdown.common.HttpConstants.*;

public class ItemManager {

    private final DatabaseManager dbManger;

    public ItemManager(DatabaseManager dbManger) {
        this.dbManger = dbManger;
    }

    public Item createItem(String url, String savedAddress) throws FileDataRequestFailedException {
        ItemInfo itemInfo = new ItemInfo();
        itemInfo.setDownloadUrl(url);
        Optional<HttpResponse<Void>> itemData = getItemData(url);

        if (itemData.isPresent()) {
            HttpResponse<Void> headRequestResponse = itemData.get();

            if (400 <= headRequestResponse.statusCode()) {
                throw new FileDataRequestFailedException("HEAD request for fetching file data failed with status: " + headRequestResponse.statusCode());
            }

            Map<String, List<String>> headers = headRequestResponse.headers().map();

            List<String> acceptRanges = headers.get(ACCEPT_RANGES_HEADER.getValue());
            if (acceptRanges != null && !acceptRanges.isEmpty()) {
                itemInfo.setResumable(true);
            }

            List<String> contentLength = headers.get(CONTENT_LENGTH_HEADER.getValue());
            if (contentLength != null && !contentLength.isEmpty()) {
                itemInfo.setSize(Long.valueOf(contentLength.get(0)));
            }

            List<String> contentType = headers.get(CONTENT_TYPE_HEADER.getValue());
            if (contentType != null && !contentType.isEmpty()) {
                itemInfo.setType(contentType.get(0));
            }

            String fileName = getFileName(url, headers);
            itemInfo.setName(fileName);
            itemInfo.setStatus(Status.PAUSED);
            itemInfo.setSavePath(savedAddress);
        }

        DownloadTask downloadTask = new DownloadTask(itemInfo);
        Item downloadItem = new Item(itemInfo, downloadTask);

        dbManger.insert(downloadItem);

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

    private Optional<HttpResponse<Void>> getItemData(String fileUrl) throws FileDataRequestFailedException {
        HttpResponse<Void> response;
        try {
            HttpClient client = HttpClient.newHttpClient();

            HttpRequest request = HttpRequest
                    .newBuilder(new URI(fileUrl))
                    .method(HEAD_METHOD.getValue(), HttpRequest.BodyPublishers.noBody())
                    .build();

            response = client.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            throw new FileDataRequestFailedException("Failed to fetch the requested file data", e);
        }

        return Optional.ofNullable(response);
    }

    public List<Item> listAllItems() {
        return dbManger.getAllItems();
    }

}
