package com.github.sinakarimi.jdown;

import com.github.sinakarimi.jdown.dataObjects.Status;
import com.github.sinakarimi.jdown.database.DatabaseManager;
import com.github.sinakarimi.jdown.download.DownloadTask;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ObservableObjectValue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class DatabaseManagerTests {

    private final DatabaseManager manager = DatabaseManager.getInstance("testDb");

    @BeforeEach()
    public void setup() {
        DownloadTask downloadTask1 = DownloadTask.builder()
                .name("ITEM1")
                .type("BINARY")
                .status(Status.PAUSED)
                .size(80L)
                .savePath("opt/test/test")
                .downloadUrl("localhost:9090")
                .resumable(true)
                .build();

        DownloadTask downloadTask2 = DownloadTask.builder()
                .name("ITEM2")
                .type("TEXT")
                .status(Status.ERROR)
                .size(80L)
                .savePath("opt/test/test")
                .downloadUrl("localhost:9090")
                .resumable(true)
                .build();

        DownloadTask downloadTask3 = DownloadTask.builder()
                .name("ITEM3")
                .type("IMAGE")
                .status(Status.CANCELED)
                .size(80L)
                .savePath("opt/test/test")
                .downloadUrl("localhost:9090")
                .resumable(true)
                .build();

        List<DownloadTask> downloadTasks = List.of(downloadTask1, downloadTask2, downloadTask3);
        manager.insertAll(downloadTasks);
    }
    
    @AfterEach
    public void tearDown() {
        manager.deleteAll();
    }

    @Test
    void fetchAllData() {
        List<DownloadTask> allItems = manager.getAllTasks();
        assertThat(allItems).extracting(DownloadTask::getNameProperty).extracting(SimpleStringProperty::get).containsExactly("ITEM1", "ITEM2", "ITEM3");
        assertThat(allItems).extracting(DownloadTask::getStatusProperty).extracting(ObservableObjectValue::get).containsExactly(Status.PAUSED, Status.ERROR, Status.CANCELED);
        System.out.println(allItems);
    }

    @Test
    void insertNewItem() {
        DownloadTask downloadTask = DownloadTask.builder()
                .name("item4")
                .type("video")
                .status(Status.COMPLETED)
                .size(72L)
                .savePath("opt/test/test")
                .downloadUrl("localhost:9090")
                .resumable(false)
                .build();

        assertDoesNotThrow(() -> manager.insert(downloadTask));
        List<DownloadTask> allItems = manager.getAllTasks();
        assertEquals(4, allItems.size());
    }

    @Test
    void deleteFromDatabase() {
        List<DownloadTask> allItems = manager.getAllTasks();

        String key = allItems.get(0).getNameProperty().get();
        assertDoesNotThrow(() -> manager.delete(key));

        List<DownloadTask> allItemsAfterDelete = manager.getAllTasks();
        assertEquals(2, allItemsAfterDelete.size());
        assertThat(allItemsAfterDelete).extracting(DownloadTask::getNameProperty).extracting(SimpleStringProperty::get).doesNotContain(key);
    }

    @Test
    void updateAnItem() {
        List<DownloadTask> allItems = manager.getAllTasks();
        DownloadTask downloadTask = allItems.get(0);

        String oldValue = downloadTask.getSavePath();
        String newValue = "opt/sina/test/";
        downloadTask.setSavePath(newValue);

        assertDoesNotThrow(() -> manager.update(downloadTask));
        List<DownloadTask> allItemsAfterUpdate = manager.getAllTasks();
        DownloadTask newDownloadTask = allItemsAfterUpdate.get(0);
        assertNotEquals(oldValue, newDownloadTask.getSavePath());
        assertEquals(newValue, newDownloadTask.getSavePath());
        assertEquals(downloadTask.getNameProperty().get(), newDownloadTask.getNameProperty().get());
    }
}
