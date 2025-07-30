package com.github.sinakarimi.jdown.download;

import com.github.sinakarimi.jdown.dataObjects.Status;
import com.github.sinakarimi.jdown.database.TasksDAO;
import com.github.sinakarimi.jdown.exception.FileDataRequestFailedException;
import javafx.collections.ObservableList;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.github.sinakarimi.jdown.common.HttpConstants.*;

public class DownloadTaskManager {

    public static DownloadTaskManager INSTANCE = null;

    private final TasksDAO dbManger;

    private DownloadTaskManager(TasksDAO dbManger) {
        this.dbManger = dbManger;
    }

    public static DownloadTaskManager getInstance(TasksDAO dbManger) {
        if (INSTANCE == null) {
            INSTANCE = new DownloadTaskManager(dbManger);
        }

        return INSTANCE;
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
                downloadTask.setSizeProperty(Long.valueOf(contentLength.get(0)));
            }

            List<String> contentType = headers.get(CONTENT_TYPE_HEADER.getValue());
            if (contentType != null && !contentType.isEmpty()) {
                downloadTask.setType(contentType.get(0));
            }

            String fileName = getFileName(url, headers);
            downloadTask.setNameProperty(fileName);
            downloadTask.setStatusProperty(Status.PAUSED);
            downloadTask.setSavePath(savedAddress);
        }

        return downloadTask;
    }

    public void saveTask(DownloadTask downloadTask) {
        dbManger.insert(downloadTask);
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
                    .newBuilder(URI.create(fileUrl))
                    .method(HEAD_METHOD.getValue(), HttpRequest.BodyPublishers.noBody())
                    .build();

            response = client.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            throw new FileDataRequestFailedException("Failed to fetch the requested file data", e);
        }

        return Optional.ofNullable(response);
    }

    public ObservableList<DownloadTask> listAllDownloadTasks() {
        dbManger.loadAllTasks();
        return dbManger.getTasksList();
    }

    public void deleteTask(DownloadTask task) {
        dbManger.delete(task.getName());
    }

}
