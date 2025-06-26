package com.github.sinakarimi81.jdown.download;

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

public class DownloadTaskManager {

    private final DatabaseManager dbManger;

    public DownloadTaskManager(DatabaseManager dbManger) {
        this.dbManger = dbManger;
    }

    public DownloadTask createTask(String url, String savedAddress) throws FileDataRequestFailedException {
        DownloadTask downloadTask = new DownloadTask();
        downloadTask.setDownloadUrl(url);
        Optional<HttpResponse<Void>> itemData = getItemData(url);

        if (itemData.isPresent()) {
            HttpResponse<Void> headRequestResponse = itemData.get();

            if (400 <= headRequestResponse.statusCode()) {
                throw new FileDataRequestFailedException("HEAD request for fetching file data failed with status: " + headRequestResponse.statusCode());
            }

            Map<String, List<String>> headers = headRequestResponse.headers().map();

            List<String> acceptRanges = headers.get(ACCEPT_RANGES_HEADER.getValue());
            if (acceptRanges != null && !acceptRanges.isEmpty()) {
                downloadTask.setResumable(true);
            }

            List<String> contentLength = headers.get(CONTENT_LENGTH_HEADER.getValue());
            if (contentLength != null && !contentLength.isEmpty()) {
                downloadTask.setSize(Long.valueOf(contentLength.get(0)));
            }

            List<String> contentType = headers.get(CONTENT_TYPE_HEADER.getValue());
            if (contentType != null && !contentType.isEmpty()) {
                downloadTask.setType(contentType.get(0));
            }

            String fileName = getFileName(url, headers);
            downloadTask.setName(fileName);
            downloadTask.setStatus(Status.PAUSED);
            downloadTask.setSavePath(savedAddress);
        }

        dbManger.insert(downloadTask);

        return downloadTask;
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

    public List<DownloadTask> listAllDownloadTasks() {
        return dbManger.getAllItems();
    }

}
