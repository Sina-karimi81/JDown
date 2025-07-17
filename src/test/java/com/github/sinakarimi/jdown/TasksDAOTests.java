package com.github.sinakarimi.jdown;

import com.github.sinakarimi.jdown.dataObjects.Status;
import com.github.sinakarimi.jdown.database.TasksDAO;
import com.github.sinakarimi.jdown.download.DownloadTask;
import javafx.collections.ObservableList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class TasksDAOTests {

    private final TasksDAO manager = TasksDAO.getInstance("testDb");

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
        // since we are inserting data before each test, to make it consistent we have to clear the tasksList
        ObservableList<DownloadTask> allItems = manager.getTasksList();
        allItems.clear();

        manager.loadAllTasks();
        allItems = manager.getTasksList();
        assertThat(allItems).extracting(DownloadTask::getName).containsExactly("ITEM1", "ITEM2", "ITEM3");
        assertThat(allItems).extracting(DownloadTask::getStatus).containsExactly(Status.PAUSED, Status.ERROR, Status.CANCELED);
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
        ObservableList<DownloadTask> allItems = manager.getTasksList();
        assertEquals(4, allItems.size());
    }

    @Test
    void deleteFromDatabase() {
        ObservableList<DownloadTask> allItems = manager.getTasksList();

        String key = allItems.get(0).getName();
        assertDoesNotThrow(() -> manager.delete(key));

        ObservableList<DownloadTask> allItemsAfterDelete = manager.getTasksList();
        assertEquals(2, allItemsAfterDelete.size());
        assertThat(allItemsAfterDelete).extracting(DownloadTask::getName).doesNotContain(key);
    }

    @Test
    void updateAnItem() {
        manager.loadAllTasks();
        ObservableList<DownloadTask> allItems = manager.getTasksList();
        DownloadTask downloadTask = allItems.get(0);

        String pk = downloadTask.getName();

        String oldValue = downloadTask.getSavePath();
        String newValue = "opt/sina/test/";
        downloadTask.setSavePath(newValue);

        assertDoesNotThrow(() -> manager.update(pk, downloadTask));
        manager.loadAllTasks();
        ObservableList<DownloadTask> allItemsAfterUpdate = manager.getTasksList();
        DownloadTask newDownloadTask = allItemsAfterUpdate.get(0);
        assertNotEquals(oldValue, newDownloadTask.getSavePath());
        assertEquals(newValue, newDownloadTask.getSavePath());
        assertEquals(downloadTask.getName(), newDownloadTask.getName());
    }
}
