package com.github.sinakarimi.jdown;

import com.github.sinakarimi.jdown.download.DownloadTask;
import com.github.sinakarimi.jdown.download.DownloadTaskManager;
import com.github.sinakarimi.jdown.exception.FileDataRequestFailedException;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;
import org.kordamp.ikonli.javafx.FontIcon;

import javax.swing.filechooser.FileSystemView;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Paths;

@Slf4j
public class DownloadFileInfoController {

    // FXML injected fields for UI controls
    @FXML private TextField urlTextField;
    @FXML private TextField saveAsTextField;
    @FXML private Button cancelButton;
    @FXML private TextField descriptionTextField;
    @FXML private TextField nameTextField;
    private DownloadTaskManager downloadTaskManager;
    private DownloadTask downloadTask = null;

    /**
     * Initializes the controller class. This method is automatically called
     * after the fxml file has been loaded.
     */
    @FXML
    private void initialize() {
        downloadTaskManager = ClassManager.getDownloadTaskManager();
    }

    /**
     * Sets the URL to be displayed in the non-editable URL text field.
     * This method would be called by the code that opens this dialog.
     * @param url The download URL.
     */
    public void setUrl(String url) {
        urlTextField.setText(url);
    }

    public void createTask(String url) {
        try {
            downloadTask = downloadTaskManager.createTask(url, null);
        } catch (FileDataRequestFailedException e) {
            log.error("failed to fetch file data");
            throw new RuntimeException(e);
        }
    }

    @FXML
    private void handleBrowse(ActionEvent event) {
        // to let the user select a save location and file name.
        log.info("Browse button clicked.");
        Stage stage = (Stage) cancelButton.getScene().getWindow();

        // part of the JavaFX API, calls the operating system Directory Chooser API
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose a directory");

        File defaultDirectory = Paths.get(System.getProperty("user.home")).toFile();
        chooser.setInitialDirectory(defaultDirectory);

        File selectedDirectory = chooser.showDialog(stage); // sets the parent stage which will be shown after you close the Directory Chooser
        saveAsTextField.setText(selectedDirectory.getAbsolutePath()); // set the path of the selected directory
    }

    @FXML
    private void handleDownloadLater(ActionEvent event) {
        // TODO: Logic to create the DownloadTask but with a PENDING or PAUSED status.
        System.out.println("Download Later button clicked.");
        downloadTask.setSavePath(saveAsTextField.getText());

        if (!descriptionTextField.getText().isEmpty()) {
            downloadTask.setDescriptionProperty(descriptionTextField.getText());
        }

        if (!nameTextField.getText().isEmpty()) {
            downloadTask.setNameProperty(nameTextField.getText());
        }

        downloadTaskManager.saveTask(downloadTask);
        closeDialog();
    }

    @FXML
    private void handleStartDownload(ActionEvent event) {
        // TODO: Logic to create the DownloadTask and start it immediately.
        System.out.println("Start Download button clicked.");
        downloadTask.setSavePath(saveAsTextField.getText());

        if (!descriptionTextField.getText().isEmpty()) {
            downloadTask.setDescriptionProperty(descriptionTextField.getText());
        }

        if (!nameTextField.getText().isEmpty()) {
            downloadTask.setNameProperty(nameTextField.getText());
        }

        downloadTaskManager.saveTask(downloadTask);
        closeDialog();
        downloadTask.start();
    }

    @FXML
    private void handleCancel(ActionEvent event) {
        // TODO: Logic to simply close the dialog with no action.
        System.out.println("Cancel button clicked.");
        downloadTask = null;
        closeDialog();
    }

    /**
     * Helper method to close the dialog window.
     */
    private void closeDialog() {
        Stage stage = (Stage) cancelButton.getScene().getWindow();
        stage.close();
    }

}
