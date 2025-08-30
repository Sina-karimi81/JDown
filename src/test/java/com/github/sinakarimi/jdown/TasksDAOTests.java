package com.github.sinakarimi.jdown;

import com.github.sinakarimi.jdown.dataObjects.Range;
import com.github.sinakarimi.jdown.dataObjects.Status;
import com.github.sinakarimi.jdown.database.TasksDAO;
import com.github.sinakarimi.jdown.download.Download;
import com.github.sinakarimi.jdown.download.DownloadTask;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
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
        DownloadTask $1 = new DownloadTask(Range.valueOf("0_2"), null, null);
        DownloadTask $2 = new DownloadTask(Range.valueOf("0_2"), null, null);
        DownloadTask $3 = new DownloadTask(Range.valueOf("0_2"), null, null);

        List<DownloadTask> tasks = List.of($1, $2, $3);

        Download downloadTask1 = Download.builder()
                .name("ITEM1")
                .type("BINARY")
                .statusProperty(new SimpleObjectProperty<>(Status.PAUSED))
                .size(80L)
                .savePath("opt/test/test")
                .downloadUrl("localhost:9090")
                .resumable(true)
                .descriptionProperty(new SimpleStringProperty("test-1"))
                .downloadTasks(tasks)
                .build();

        Download downloadTask2 = Download.builder()
                .name("ITEM2")
                .type("TEXT")
                .statusProperty(new SimpleObjectProperty<>(Status.ERROR))
                .size(80L)
                .savePath("opt/test/test")
                .downloadUrl("localhost:9090")
                .resumable(true)
                .descriptionProperty(new SimpleStringProperty("test-2"))
                .downloadTasks(tasks)
                .build();

        Download downloadTask3 = Download.builder()
                .name("ITEM3")
                .type("IMAGE")
                .statusProperty(new SimpleObjectProperty<>(Status.CANCELED))
                .size(80L)
                .savePath("opt/test/test")
                .downloadUrl("localhost:9090")
                .resumable(true)
                .descriptionProperty(new SimpleStringProperty("test-3"))
                .downloadTasks(tasks)
                .build();

        List<Download> downloadTasks = List.of(downloadTask1, downloadTask2, downloadTask3);
        manager.insertAll(downloadTasks);
    }
    
    @AfterEach
    public void tearDown() {
        manager.deleteAll();
    }

    @Test
    void fetchAllData() {
        // since we are inserting data before each test, to make it consistent we have to clear the tasksList
        ObservableList<Download> allItems = manager.getTasksList();
        allItems.clear();

        manager.loadAllTasks();
        allItems = manager.getTasksList();

        assertThat(allItems).extracting(Download::getName)
                .containsExactly("ITEM1", "ITEM2", "ITEM3");

        assertThat(allItems).extracting(Download::getStatusProperty)
                .extracting(ObjectProperty::get)
                .containsExactly(Status.PAUSED, Status.ERROR, Status.CANCELED);

        System.out.println(allItems);
    }

    @Test
    void insertNewItem() {
        Download downloadTask = Download.builder()
                .name("item4")
                .type("video")
                .statusProperty(new SimpleObjectProperty<>(Status.COMPLETED))
                .size(72L)
                .savePath("opt/test/test")
                .downloadUrl("localhost:9090")
                .resumable(false)
                .descriptionProperty(new SimpleStringProperty("this is a test"))
                .build();

        assertDoesNotThrow(() -> manager.insert(downloadTask));
        ObservableList<Download> allItems = manager.getTasksList();
        assertEquals(4, allItems.size());
    }

    @Test
    void deleteFromDatabase() {
        ObservableList<Download> allItems = manager.getTasksList();

        String key = allItems.get(0).getName();
        assertDoesNotThrow(() -> manager.delete(key));

        ObservableList<Download> allItemsAfterDelete = manager.getTasksList();
        assertEquals(2, allItemsAfterDelete.size());
        assertThat(allItemsAfterDelete).extracting(Download::getName).doesNotContain(key);
    }

    @Test
    void updateAnItem() {
        manager.loadAllTasks();
        ObservableList<Download> allItems = manager.getTasksList();
        Download downloadTask = allItems.get(0);

        String pk = downloadTask.getName();

        String oldValue = downloadTask.getDescriptionProperty().get();
        String newValue = "this is a test";
        downloadTask.setSavePath(newValue);

        assertDoesNotThrow(() -> manager.updateDescription(pk, newValue));
        manager.loadAllTasks();
        ObservableList<Download> allItemsAfterUpdate = manager.getTasksList();
        Download newDownloadTask = allItemsAfterUpdate.get(0);
        assertNotEquals(oldValue, newDownloadTask.getDescriptionProperty().get());
        assertEquals(newValue, newDownloadTask.getDescriptionProperty().get());
        assertEquals(downloadTask.getName(), newDownloadTask.getName());
    }
}
