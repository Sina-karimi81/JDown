package com.github.sinakarimi.jdown;

import com.github.sinakarimi.jdown.dataObjects.Status;
import com.github.sinakarimi.jdown.download.DownloadTask;
import com.github.sinakarimi.jdown.download.DownloadTaskManager;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.ProgressBarTableCell;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;

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

    private DownloadTaskManager downloadTaskManager;

    /**
     * This method is called by the FXMLLoader after the FXML file has been loaded.
     * It's the perfect place to initialize the table columns and set up bindings.
     */
    @FXML
    public void initialize() {
        // Set up the cell value factories for each column.
        // This tells the table how to get the data for each cell from the DownloadTask object.
        nameColumn.setCellValueFactory(cellData -> cellData.getValue().getNameProperty());
        sizeColumn.setCellValueFactory(cellData -> cellData.getValue().getSizeProperty().asObject());
        statusColumn.setCellValueFactory(cellData -> cellData.getValue().getStatusProperty());

        // For the progress column, we want to display a progress bar.
//        progressColumn.setCellValueFactory(cellData -> cellData.getValue().getProgressProperty().asObject());
        progressColumn.setCellFactory(ProgressBarTableCell.forTableColumn());

        downloadTaskManager = ClassManager.getDownloadTaskManager();

        ObservableList<DownloadTask> downloadTasks = downloadTaskManager.listAllDownloadTasks();
        downloadsTable.setItems(downloadTasks);
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
    private void handleResumeAction(ActionEvent event) {
        // TODO: Iterate through the download tasks and resume them.
        log.info("Resume button clicked!");
        downloadsTable.getItems().forEach(DownloadTask::resume);
    }

    /**
     * Handles the action of the "Pause" button.
     */
    @FXML
    private void handlePauseAction(ActionEvent event) {
        // TODO: Iterate through the download tasks and pause them.
        log.info("Pause button clicked!");
        downloadsTable.getItems().forEach(DownloadTask::pause);
    }

    /**
     * Handles the action of the "Pause All" button.
     */
    @FXML
    private void handlePauseAllAction(ActionEvent event) {
        // TODO: Iterate through the download tasks and pause them.
        log.info("Pause All button clicked!");
        downloadsTable.getItems().forEach(DownloadTask::pause);
    }

    /**
     * Handles the action of the "Cancel" button.
     */
    @FXML
    private void handleCancelAction(ActionEvent event) {
        // TODO: Iterate through the download tasks and cancel them.
        log.info("Cancel button clicked!");
        downloadsTable.getItems().forEach(DownloadTask::cancel);
    }

}
