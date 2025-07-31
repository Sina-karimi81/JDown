package com.github.sinakarimi.jdown;

import com.github.sinakarimi.jdown.dataObjects.Status;
import com.github.sinakarimi.jdown.download.DownloadTask;
import com.github.sinakarimi.jdown.download.DownloadTaskManager;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.ProgressBarTableCell;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;

import java.awt.event.MouseEvent;
import java.io.IOException;

@Slf4j
public class DownloadManagerController {

    // The table that displays the download tasks. The fx:id in the FXML file must match this variable name.
    @FXML
    private TableView<DownloadTask> downloadsTable;

    // Table columns that will be populated with data from DownloadTask objects.
    @FXML
    private TableColumn<DownloadTask, String> nameColumn;

    @FXML
    private TableColumn<DownloadTask, Long> sizeColumn;

    @FXML
    private TableColumn<DownloadTask, Status> statusColumn;

    @FXML
    private TableColumn<DownloadTask, Double> progressColumn;

    @FXML
    private Button resumeButton;

    @FXML
    private Button pauseButton;

    @FXML
    private Button cancelButton;

    private final DownloadTaskManager downloadTaskManager = ClassManager.getDownloadTaskManager();;

    /**
     * This method is called by the FXMLLoader after the FXML file has been loaded.
     * It's the perfect place to initialize the table columns and set up bindings.
     */
    @FXML
    public void initialize() {
        setupTableCellFactories();
        setupContextMenuForTable();
        setupButtonsBindings();
        fetchTasks();
    }

    private void setupTableCellFactories() {
        // Set up the cell value factories for each column.
        // This tells the table how to get the data for each cell from the DownloadTask object.
        nameColumn.setCellValueFactory(cellData -> cellData.getValue().getNameProperty());
        sizeColumn.setCellValueFactory(cellData -> cellData.getValue().getSizeProperty().asObject());
        statusColumn.setCellValueFactory(cellData -> cellData.getValue().getStatusProperty());

        // For the progress column, we want to display a progress bar.
        progressColumn.setCellValueFactory(cellData -> cellData.getValue().getProgressProperty().asObject());
        progressColumn.setCellFactory(ProgressBarTableCell.forTableColumn());
    }

    private void setupContextMenuForTable() {
        // creates the menu that you see when you right-click on a rwo
        ContextMenu contextMenu = new ContextMenu();

        // items in the context menu
        MenuItem delete = new MenuItem("delete");
        delete.setOnAction(event -> {
            DownloadTask selectedItem = downloadsTable.getSelectionModel().getSelectedItem();
            downloadsTable.getItems().remove(selectedItem);
            downloadTaskManager.deleteTask(selectedItem);
        });

        MenuItem properties = new MenuItem("properties");
        properties.setOnAction(event -> {
            DownloadTask selectedItem = downloadsTable.getSelectionModel().getSelectedItem();
            handlePropertiesContextAction(selectedItem);
        });

        // adding items to context menu
        contextMenu.getItems().addAll(delete, properties);

        downloadsTable.setContextMenu(contextMenu);
    }

    private void setupButtonsBindings() {
        // we disable the three main buttons until an item is selected from the table
        // selectedItemProperty() returns the properties of the selected item
        resumeButton.disableProperty().bind(downloadsTable.getSelectionModel().selectedItemProperty().isNull());
        pauseButton.disableProperty().bind(downloadsTable.getSelectionModel().selectedItemProperty().isNull());
        cancelButton.disableProperty().bind(downloadsTable.getSelectionModel().selectedItemProperty().isNull());
    }

    private void fetchTasks() {
        ObservableList<DownloadTask> downloadTasks = downloadTaskManager.listAllDownloadTasks();
        downloadsTable.setItems(downloadTasks);
    }

    private void handlePropertiesContextAction(DownloadTask task) {
        log.info("properties called on {}", task.getName());
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("FilePropertiesView.fxml"));
        Parent root;

        try {
            // you always have to call #load() first, then you can access fxml elements, otherwise NullPointers!!!
            root = fxmlLoader.load();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        FilePropertiesController filePropertiesController = fxmlLoader.getController();
        filePropertiesController.initValues(task);

        Stage viewFileProperties = new Stage();
        // stops the user from interacting with any other window other than the one currently visible
        viewFileProperties.initModality(Modality.APPLICATION_MODAL);
        viewFileProperties.setResizable(false);
        viewFileProperties.setTitle("File Properties");
        viewFileProperties.setScene(new Scene(root));
        viewFileProperties.show();
    }

    /**
     * Handles the action of the "Add URL" button.
     * This method name must match the onAction attribute in the FXML.
     */
    @FXML
    private void handleAddUrlAction(ActionEvent event) {
        log.info("Add URL button clicked!");
        try {

            /*
             * The newer and cleaner way to load a FXML file, this static method only returns the root of the view,
             * and we cannot access the controller or other such classes
             */
            Parent root = FXMLLoader.load(DownloadManagerController.class.getResource("AddUrlView.fxml"));
            Stage addUrlStage = new Stage();
            addUrlStage.initModality(Modality.APPLICATION_MODAL);
            addUrlStage.setResizable(false);
            addUrlStage.setTitle("Download a URL");
            addUrlStage.setScene(new Scene(root));
            addUrlStage.show();
        } catch (IOException e) {
            log.error("error occurred while trying to open the add url dialog", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Handles the action of the "Resume" button.
     */
    @FXML
    private void handleResumeAction() {
        log.info("Resume button clicked!");
        DownloadTask selectedItem = downloadsTable.getSelectionModel().getSelectedItem();
        selectedItem.resume();
    }

    /**
     * Handles the action of the "Pause" button.
     */
    @FXML
    private void handlePauseAction() {
        log.info("Pause button clicked!");
        DownloadTask selectedItem = downloadsTable.getSelectionModel().getSelectedItem();
        selectedItem.pause();
    }

    /**
     * Handles the action of the "Pause All" button.
     */
    @FXML
    private void handlePauseAllAction() {
        log.info("Pause All button clicked!");
        downloadsTable.getItems().forEach(DownloadTask::pause);
    }

    /**
     * Handles the action of the "Cancel" button.
     */
    @FXML
    private void handleCancelAction() {
        log.info("Cancel button clicked!");
        DownloadTask selectedItem = downloadsTable.getSelectionModel().getSelectedItem();
        selectedItem.cancel();
    }

}
