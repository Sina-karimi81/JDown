package com.github.sinakarimi.jdown.database;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.github.sinakarimi.jdown.configuration.ConfigurationConstants;
import com.github.sinakarimi.jdown.configuration.ConfigurationUtils;
import com.github.sinakarimi.jdown.dataObjects.DataSegment;
import com.github.sinakarimi.jdown.dataObjects.Range;
import com.github.sinakarimi.jdown.dataObjects.Status;
import com.github.sinakarimi.jdown.download.DownloadTask;
import com.github.sinakarimi.jdown.exception.DatabaseException;
import com.github.sinakarimi.jdown.serialization.RangeDeserializer;
import com.github.sinakarimi.jdown.serialization.RangeSerializer;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.sql.*;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentMap;

@Slf4j
public class TasksDAO {

    private String DB_URL = "jdbc:sqlite:";
    private static TasksDAO INSTANCE = null;
    private final ObjectMapper mapper;

    /**
     * utility class used to create Collection Framework classes. provides the same functionalities
     */
    @Getter
    private final ObservableList<DownloadTask> tasksList = FXCollections.observableArrayList();

    public static TasksDAO getInstance(String dbName) {
        if (INSTANCE == null) {
            INSTANCE = new TasksDAO(dbName);
            INSTANCE.createTable();
        }

        return INSTANCE;
    }

    private TasksDAO(String dbName) {
        DB_URL += dbName;
        SimpleModule module = new SimpleModule();

        // used for serializing and deserializing Map Objects keys
        module.addKeySerializer(Range.class, new RangeSerializer());
        module.addKeyDeserializer(Range.class, new RangeDeserializer());
        mapper = new ObjectMapper();
        mapper.registerModule(module);
    }

    private void createTable() {
        String sql = """
                CREATE TABLE IF NOT EXISTS TASKS (
                    NAME TEXT PRIMARY KEY,
                    TYPE TEXT,
                    STATUS TEXT,
                    SIZE LONG,
                    SAVEPATH TEXT,
                    URL TEXT,
                    RESUMABLE INTEGER,
                    DESCRIPTION TEXT,
                    DATA TEXT
                );
                """;

        try (Connection connection = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.execute();
        } catch (Exception e) {
            log.error("failed to create tasks table in the database", e);
            throw new DatabaseException("failed to create tasks table in the database", e);
        }
    }

    public void insert(DownloadTask downloadTask) {
        log.info("inserting download task {} into the database", downloadTask.getName());
        String sql = """
                INSERT INTO TASKS(NAME, TYPE, STATUS, SIZE, SAVEPATH, URL, RESUMABLE, DESCRIPTION, DATA) VALUES (? , ? , ? , ? , ? , ? , ? , ? , ?);
                """;

        try (Connection connection = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setString(1, downloadTask.getName());
            ps.setString(2, downloadTask.getType());
            ps.setString(3, downloadTask.getStatus().name());
            ps.setLong(4, downloadTask.getSize());
            ps.setString(5, downloadTask.getSavePath());
            ps.setString(6, downloadTask.getDownloadUrl());
            ps.setBoolean(7, downloadTask.getResumable());
            ps.setString(8, downloadTask.getDescription());
            String data = mapper.writeValueAsString(downloadTask.getSegments());
            ps.setString(9, data);

            int i = ps.executeUpdate();
            log.info("{} record inserted for task {}", i, downloadTask.getName());

            tasksList.add(downloadTask);
        } catch (Exception e) {
            log.error("failed to insert download task {} in to the database", downloadTask, e);
            throw new DatabaseException("failed to insert a download task in to the database", e);
        }
    }

    public void insertAll(List<DownloadTask> downloadTasks) {
        for (DownloadTask downloadTask: downloadTasks) {
            insert(downloadTask);
        }
    }

    public void update(String pk, DownloadTask downloadTask) {
        log.info("updating task {} into the database", downloadTask.getName());
        String sql = """
                UPDATE TASKS SET NAME = ?, TYPE = ? , STATUS = ? , SIZE = ? , SAVEPATH = ? , URL = ? , RESUMABLE = ? , DATA = ? , DESCRIPTION = ? WHERE NAME = ?;
                """;

        try (Connection connection = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setString(1, downloadTask.getName());
            ps.setString(2, downloadTask.getType());
            ps.setString(3, downloadTask.getStatus().name());
            ps.setLong(4, downloadTask.getSize());
            ps.setString(5, downloadTask.getSavePath());
            ps.setString(6, downloadTask.getDownloadUrl());
            ps.setBoolean(7, downloadTask.getResumable());
            String data = mapper.writeValueAsString(downloadTask.getSegments());
            ps.setString(8, data);
            ps.setString(9, downloadTask.getDescription());
            ps.setString(10, pk);

            int i = ps.executeUpdate();

            int indexToUpdate = -1;
            for (int idx = 0; idx < tasksList.size(); idx++) {
                if (tasksList.get(idx).getName().equals(pk)) {
                    indexToUpdate = idx;
                    break;
                }
            }

            tasksList.set(indexToUpdate, downloadTask);

            log.info("{} record updated for task {}", i, downloadTask.getName());
        } catch (Exception e) {
            log.error("failed to insert task {} in to the database", downloadTask, e);
            throw new DatabaseException("failed to insert an task in to the database", e);
        }
    }

    public void delete(String key) {
        log.info("deleting task {} from database", key);
        String sql = """
                DELETE FROM TASKS WHERE NAME = ?;
                """;

        try (Connection connection = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setString(1, key);
            int i = ps.executeUpdate();
            log.info("{} record deleted from tasks, task {} was deleted", i, key);
            tasksList.removeIf(d -> d.getName().equals(key));
        } catch (Exception e) {
            log.error("failed to delete task {} in to the database", key, e);
            throw new DatabaseException("failed to delete an task in to the database", e);
        }
    }

    public void deleteAll() {
        log.info("deleting all task from database");
        String sql = """
                DELETE FROM TASKS;
                """;

        try (Connection connection = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = connection.prepareStatement(sql)) {

            int i = ps.executeUpdate();
            log.info("{} record deleted from tasks, all task were deleted", i);

        } catch (Exception e) {
            log.error("failed to delete all task in the database", e);
            throw new DatabaseException("failed to delete all tasks in the database", e);
        }
    }

    public void loadAllTasks() {
        log.info("getting all tasks from the database");
        String sql = """
                SELECT * FROM TASKS;
                """;

        try (Connection connection = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ResultSet resultSet = ps.executeQuery();
            while (resultSet.next()) {
                DownloadTask downloadTask = getDownloadTask(resultSet);
                downloadTask.reloadSegments();
                tasksList.add(downloadTask);
            }

            log.info("finished getting all tasks from the database, number of records fetched: {}", tasksList.size());
        } catch (Exception e) {
            log.error("failed to fetch all tasks from database", e);
            throw new DatabaseException("failed to fetch all tasks from database", e);
        }
    }

    public Optional<DownloadTask> getTaskByKey(String key) {
        log.info("getting a specific task with name {} from database", key);
        DownloadTask result = null;

        String sql = """
                SELECT * FROM TASKS WHERE NAME = ?
                """;

        try (Connection connection = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setString(1, key);

            ResultSet resultSet = ps.executeQuery();
            while (resultSet.next()) {
                result = getDownloadTask(resultSet);
            }

            log.info("finished getting task with name {} from the database, record fetched: {}", key, result);
            return Optional.ofNullable(result);
        } catch (Exception e) {
            log.error("failed to fetch all tasks from database", e);
            throw new DatabaseException("failed to fetch all tasks from database", e);
        }
    }

    private DownloadTask getDownloadTask(ResultSet resultSet) throws SQLException, JsonProcessingException {
        DownloadTask result;
        String name = resultSet.getString("NAME");
        String type = resultSet.getString("TYPE");
        String status = resultSet.getString("STATUS");
        long size = resultSet.getLong("SIZE");
        String savePath = resultSet.getString("SAVEPATH");
        String url = resultSet.getString("URL");
        int resumable = resultSet.getInt("RESUMABLE");
        String description = resultSet.getString("DESCRIPTION");

        String data = resultSet.getString("DATA");
        ConcurrentMap<Range, DataSegment> rangeDataSegmentConcurrentMap = mapper.readValue(data, new TypeReference<>() {
        });

        result = DownloadTask.builderWithSegment()
                .name(name)
                .type(type)
                .status(Status.valueOf(status))
                .size(size)
                .savePath(savePath)
                .downloadUrl(url)
                .resumable(resumable == 1)
                .isPaused(true)
                .description(description)
                .segments(rangeDataSegmentConcurrentMap)
                .buildWithSegments();
        return result;
    }

}
