package com.github.sinakarimi81.jdown.database;

import com.github.sinakarimi81.jdown.dataObjects.Item;
import com.github.sinakarimi81.jdown.dataObjects.ItemInfo;
import com.github.sinakarimi81.jdown.dataObjects.Status;
import com.github.sinakarimi81.jdown.download.DownloadTask;
import com.github.sinakarimi81.jdown.exception.DatabaseException;
import lombok.extern.slf4j.Slf4j;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class DatabaseManager {

    private String DB_URL = "jdbc:sqlite:";
    private static DatabaseManager INSTANCE = null;

    public static DatabaseManager getInstance(String dbName) {
        if (INSTANCE == null) {
            INSTANCE = new DatabaseManager(dbName);
        }

        return INSTANCE;
    }

    private DatabaseManager(String dbName) {
        DB_URL += dbName;
    }

    public void createTable() {
        String sql = """
                CREATE TABLE IF NOT EXISTS ITEMS (
                    NAME TEXT PRIMARY KEY,
                    TYPE TEXT,
                    STATUS TEXT,
                    SIZE LONG,
                    SAVEPATH TEXT,
                    URL TEXT,
                    RESUMABLE INTEGER,
                    DATA BLOB
                );
                """;

        try (Connection connection = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.execute();
        } catch (Exception e) {
            log.error("failed to create items table in the database", e);
            throw new DatabaseException("failed to create items table in the database", e);
        }
    }

    public void insert(Item item) {
        log.info("inserting item {} into the database", item.getItemInfo().getName());
        String sql = """
                INSERT INTO ITEMS(NAME, TYPE, STATUS, SIZE, SAVEPATH, URL, RESUMABLE, DATA) VALUES (? , ? , ? , ? , ? , ? , ? , ?);
                """;

        try (Connection connection = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setString(1, item.getItemInfo().getName());
            ps.setString(2, item.getItemInfo().getType());
            ps.setString(3, item.getItemInfo().getStatus().name());
            ps.setLong(4, item.getItemInfo().getSize());
            ps.setString(5, item.getItemInfo().getSavePath());
            ps.setString(6, item.getItemInfo().getDownloadUrl());
            ps.setBoolean(7, item.getItemInfo().getResumable());
            ps.setBytes(8, item.getDownloadTask().getData());

            int i = ps.executeUpdate();
            log.info("{} record inserted for item {}", i, item.getItemInfo().getName());

        } catch (Exception e) {
            log.error("failed to insert item {} in to the database", item, e);
            throw new DatabaseException("failed to insert an item in to the database", e);
        }
    }

    public void insertAll(List<Item> items) {
        for (Item item: items) {
            insert(item);
        }
    }

    public void update(Item item) {
        log.info("updating item {} into the database", item.getItemInfo().getName());
        String sql = """
                UPDATE ITEMS SET NAME = ?, TYPE = ? , STATUS = ? , SIZE = ? , SAVEPATH = ? , URL = ? , RESUMABLE = ? , DATA = ? WHERE NAME = ?;
                """;

        try (Connection connection = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setString(1, item.getItemInfo().getName());
            ps.setString(2, item.getItemInfo().getType());
            ps.setString(3, item.getItemInfo().getStatus().name());
            ps.setLong(4, item.getItemInfo().getSize());
            ps.setString(5, item.getItemInfo().getSavePath());
            ps.setString(6, item.getItemInfo().getDownloadUrl());
            ps.setBoolean(7, item.getItemInfo().getResumable());
            ps.setBytes(8, item.getDownloadTask().getData());
            ps.setString(9, item.getItemInfo().getName());

            int i = ps.executeUpdate();
            log.info("{} record updated for item {}", i, item.getItemInfo().getName());

        } catch (Exception e) {
            log.error("failed to insert item {} in to the database", item, e);
            throw new DatabaseException("failed to insert an item in to the database", e);
        }
    }

    public void delete(String key) {
        log.info("deleting item {} from database", key);
        String sql = """
                DELETE FROM ITEMS WHERE NAME = ?;
                """;

        try (Connection connection = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setString(1, key);
            int i = ps.executeUpdate();
            log.info("{} record deleted from items, item {} was deleted", i, key);

        } catch (Exception e) {
            log.error("failed to delete item {} in to the database", key, e);
            throw new DatabaseException("failed to delete an item in to the database", e);
        }
    }

    public void deleteAll() {
        log.info("deleting all item from database");
        String sql = """
                DELETE FROM ITEMS;
                """;

        try (Connection connection = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = connection.prepareStatement(sql)) {

            int i = ps.executeUpdate();
            log.info("{} record deleted from items, all item were deleted", i);

        } catch (Exception e) {
            log.error("failed to delete all item in the database", e);
            throw new DatabaseException("failed to delete all items in the database", e);
        }
    }

    public List<Item> getAllItems() {
        log.info("getting all items from the database");

        List<Item> result = new ArrayList<>();

        String sql = """
                SELECT * FROM ITEMS;
                """;

        try (Connection connection = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ResultSet resultSet = ps.executeQuery();
            while (resultSet.next()) {
                String name = resultSet.getString("NAME");
                String type = resultSet.getString("TYPE");
                String status = resultSet.getString("STATUS");
                long size = resultSet.getLong("SIZE");
                String savepath = resultSet.getString("SAVEPATH");
                String url = resultSet.getString("URL");
                int resumable = resultSet.getInt("RESUMABLE");
                byte[] data = resultSet.getBytes("DATA");
                ItemInfo itemInfo = new ItemInfo(name, type, Status.valueOf(status), size, savepath, url, resumable == 1);
                DownloadTask downloadTask = new DownloadTask(itemInfo, data);
                Item item = new Item(itemInfo, downloadTask);
                result.add(item);
            }

            log.info("finished getting all items from the database, number of records fetched: {}", result.size());
            return result;
        } catch (Exception e) {
            log.error("failed to fetch all items from database", e);
            throw new DatabaseException("failed to fetch all items from database", e);
        }
    }

}
