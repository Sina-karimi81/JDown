import com.github.sinakarimi81.jdown.dataObjects.Item;
import com.github.sinakarimi81.jdown.dataObjects.ItemInfo;
import com.github.sinakarimi81.jdown.dataObjects.Status;
import com.github.sinakarimi81.jdown.database.DatabaseManager;
import com.github.sinakarimi81.jdown.download.DownloadTask;
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
        ItemInfo itemInfo1 = new ItemInfo("ITEM1" , "BINARY" , Status.PAUSED , 80L , "opt/test/test" , "localhost:9090" , true);
        DownloadTask downloadTask1 = new DownloadTask(itemInfo1);

        ItemInfo itemInfo2 = new ItemInfo("ITEM2" , "TEXT" , Status.ERROR , 80L , "opt/test/test" , "localhost:9090" , true);
        DownloadTask downloadTask2 = new DownloadTask(itemInfo2);

        ItemInfo itemInfo3 = new ItemInfo("ITEM3" , "IMAGE" , Status.CANCELED , 80L , "opt/test/test" , "localhost:9090" , true);
        DownloadTask downloadTask3 = new DownloadTask(itemInfo3);

        List<Item> items = List.of(new Item(itemInfo1, downloadTask1), new Item(itemInfo2, downloadTask2), new Item(itemInfo3, downloadTask3));
        manager.insertAll(items);
    }
    
    @AfterEach
    public void tearDown() {
        manager.deleteAll();
    }

    @Test
    void fetchAllData() {
        List<Item> allItems = manager.getAllItems();
        assertThat(allItems).extracting(Item::getItemInfo).extracting(ItemInfo::getName).containsExactly("ITEM1", "ITEM2", "ITEM3");
        assertThat(allItems).extracting(Item::getItemInfo).extracting(ItemInfo::getStatus).containsExactly(Status.PAUSED, Status.ERROR, Status.CANCELED);
        System.out.println(allItems);
    }

    @Test
    void insertNewItem() {
        ItemInfo itemInfo = new ItemInfo("item4", "video", Status.COMPLETED, 72L, "opt/test/test", "localhost:9090", false);
        DownloadTask downloadTask = new DownloadTask(itemInfo);
        Item item = new Item(itemInfo, downloadTask);

        assertDoesNotThrow(() -> manager.insert(item));
        List<Item> allItems = manager.getAllItems();
        assertEquals(4, allItems.size());
    }

    @Test
    void deleteFromDatabase() {
        List<Item> allItems = manager.getAllItems();

        String key = allItems.get(0).getItemInfo().getName();
        assertDoesNotThrow(() -> manager.delete(key));

        List<Item> allItemsAfterDelete = manager.getAllItems();
        assertEquals(2, allItemsAfterDelete.size());
        assertThat(allItemsAfterDelete).extracting(Item::getItemInfo).extracting(ItemInfo::getName).doesNotContain(key);
    }

    @Test
    void updateAnItem() {
        List<Item> allItems = manager.getAllItems();
        Item item = allItems.get(0);

        String oldValue = item.getItemInfo().getSavePath();
        String newValue = "opt/sina/test/";
        item.getItemInfo().setSavePath(newValue);

        assertDoesNotThrow(() -> manager.update(item));
        List<Item> allItemsAfterUpdate = manager.getAllItems();
        Item newItem = allItemsAfterUpdate.get(0);
        assertNotEquals(oldValue, newItem.getItemInfo().getSavePath());
        assertEquals(newValue, newItem.getItemInfo().getSavePath());
        assertEquals(item.getItemInfo().getName(), newItem.getItemInfo().getName());
    }
}
