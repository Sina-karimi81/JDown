package com.github.sinakarimi.jdown.database;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.github.sinakarimi.jdown.dataObjects.Range;
import com.github.sinakarimi.jdown.dataObjects.Status;
import com.github.sinakarimi.jdown.download.Download;
import com.github.sinakarimi.jdown.download.DownloadTask;
import com.github.sinakarimi.jdown.exception.DatabaseException;
import com.github.sinakarimi.jdown.serialization.RangeDeserializer;
import com.github.sinakarimi.jdown.serialization.RangeSerializer;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.sql.*;
import java.util.List;
import java.util.Optional;

@Slf4j
public class TasksDAO {

    private String DB_URL = "jdbc:sqlite:";
    private static TasksDAO INSTANCE = null;
    private final ObjectMapper mapper;

    /**
     * utility class used to create Collection Framework classes. provides the same functionalities
     */
    @Getter
    private final ObservableList<Download> tasksList = FXCollections.observableArrayList();

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
                    PROGRESSION REAL,
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

    public void insert(Download download) {
        log.info("inserting download task {} into the database", download.getName());
        String sql = """
                INSERT INTO TASKS(NAME, TYPE, STATUS, SIZE, SAVEPATH, URL, RESUMABLE, PROGRESSION, DESCRIPTION, DATA) VALUES (? , ? , ? , ? , ? , ? , ? , ? , ? , ?);
                """;

        try (Connection connection = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setString(1, download.getName());
            ps.setString(2, download.getType());
            ps.setString(3, download.getStatusProperty().get().name());
            ps.setLong(4, download.getSize());
            ps.setString(5, download.getSavePath());
            ps.setString(6, download.getDownloadUrl());
            ps.setBoolean(7, download.getResumable());
            ps.setDouble(8, 0);

            // to avoid further exceptions
            String valueSafe = download.getDescriptionProperty() != null ? download.getDescriptionProperty().getValueSafe() : "";
            ps.setString(9, valueSafe);

            String data = mapper.writeValueAsString(download.getDownloadTasks());
            ps.setString(10, data);

            int i = ps.executeUpdate();
            log.info("{} record inserted for task {}", i, download.getName());

            tasksList.add(download);
        } catch (Exception e) {
            log.error("failed to insert download task {} in to the database", download, e);
            throw new DatabaseException("failed to insert a download task in to the database", e);
        }
    }

    public void insertAll(List<Download> downloads) {
        for (Download download: downloads) {
            insert(download);
        }
    }

    public void updateStatus(String pk, Status status) {
        log.info("updating status of task with pk {} into the database", pk);
        String sql = """
                UPDATE TASKS SET STATUS = ? WHERE NAME = ?;
                """;

        try (Connection connection = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setString(1, status.getValue());
            ps.setString(2, pk);

            int i = ps.executeUpdate();

            for (Download download : tasksList) {
                if (download.getName().equals(pk)) {
                    download.setStatus(status);
                }
            }

            log.info("{} record updated for task with pk {}", i, pk);
        } catch (Exception e) {
            log.error("failed to update task with id {} in to the database", pk, e);
            throw new DatabaseException("failed to update a task in to the database", e);
        }
    }

    public void updateDescription(String pk, String description) {
        log.info("updating description of task with pk {} into the database", pk);
        String sql = """
                UPDATE TASKS SET DESCRIPTION = ? WHERE NAME = ?;
                """;

        try (Connection connection = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setString(1, description);
            ps.setString(2, pk);

            int i = ps.executeUpdate();

            for (Download download : tasksList) {
                if (download.getName().equals(pk)) {
                    download.setDescription(description);
                }
            }

            log.info("{} record updated for task with pk {}", i, pk);
        } catch (Exception e) {
            log.error("failed to update task with id {} in to the database", pk, e);
            throw new DatabaseException("failed to update a task in to the database", e);
        }
    }

    public void updateProgression(String pk, double progression) {
        log.info("updating progression of task with pk {} into the database", pk);
        String sql = """
                UPDATE TASKS SET PROGRESSION = ? WHERE NAME = ?;
                """;

        try (Connection connection = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setDouble(1, progression);
            ps.setString(2, pk);

            int i = ps.executeUpdate();

            for (Download download : tasksList) {
                if (download.getName().equals(pk)) {
                    download.setProgress(progression);
                }
            }

            log.info("{} record updated for task with pk {}", i, pk);
        } catch (Exception e) {
            log.error("failed to update task with id {} in to the database", pk, e);
            throw new DatabaseException("failed to update a task in to the database", e);
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
                Download download = getDownloadTask(resultSet);
                download.reloadSegments();
                tasksList.add(download);
            }

            log.info("finished getting all tasks from the database, number of records fetched: {}", tasksList.size());
        } catch (Exception e) {
            log.error("failed to fetch all tasks from database", e);
            throw new DatabaseException("failed to fetch all tasks from database", e);
        }
    }

    public Optional<Download> getTaskByKey(String key) {
        log.info("getting a specific task with name {} from database", key);
        Download result = null;

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

    private Download getDownloadTask(ResultSet resultSet) throws SQLException, JsonProcessingException {
        Download result;
        String name = resultSet.getString("NAME");
        String type = resultSet.getString("TYPE");
        String status = resultSet.getString("STATUS");
        long size = resultSet.getLong("SIZE");
        String savePath = resultSet.getString("SAVEPATH");
        String url = resultSet.getString("URL");
        int resumable = resultSet.getInt("RESUMABLE");
        String description = resultSet.getString("DESCRIPTION");
        double progress = resultSet.getDouble("PROGRESSION");

        String data = resultSet.getString("DATA");
        List<DownloadTask> downloadTasks = mapper.readValue(data, new TypeReference<>() {});

        result = Download.builder()
                .name(name)
                .type(type)
                .statusProperty(new SimpleObjectProperty<>(Status.valueOf(status)))
                .size(size)
                .savePath(savePath)
                .downloadUrl(url)
                .resumable(resumable == 1)
                .descriptionProperty(new SimpleStringProperty(description))
                .progressProperty(new SimpleDoubleProperty(progress))
                .downloadTasks(downloadTasks)
                .build();
        return result;
    }

}
