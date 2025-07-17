package com.github.sinakarimi.jdown;

import javafx.beans.binding.Bindings;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class AddUrlController {

    @FXML
    private TextField addressTextField;

    @FXML
    private Button okButton;

    private static final String URL_REGEX = "^(https?|ftp)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;\\[\\]]*[-a-zA-Z0-9+&@#/%=~_|]";
    private static final Pattern URL_PATTERN = Pattern.compile(URL_REGEX);

    /**
     * This method is called by the FXMLLoader after the FXML file has been loaded.
     * Used to set up bindings for disabling buttons based on text fields being empty.
     */
    @FXML
    public void initialize() {
        // binds the disable property of this button to the emptiness of the text field
        okButton.disableProperty().bind(Bindings.isEmpty(addressTextField.textProperty()));
    }

    /**
     * Handles the action of the "Ok" button.
     * This method name must match the onAction attribute in the FXML.
     */
    @FXML
    private void handleOkAction(ActionEvent event) throws IOException {
        log.info("In Add URL Scene OK button clicked!");
        String potentialUrl = addressTextField.getText();

        if (potentialUrl == null || potentialUrl.trim().isEmpty()) {
            log.info("Validation Error, Address field cannot be empty.");
            return;
        }

        Matcher matcher = URL_PATTERN.matcher(potentialUrl.trim());

        if (matcher.matches()) {
            log.info("entered url is {}", potentialUrl.trim());
            closeDialog();

            /*
             * This here is the OG way of loading a FXML file, with this we can access the controller of the way along
             * with the root
             */
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("DownloadFileInfoView.fxml"));

            // you always have to call #load() first, then you can access fxml elements, otherwise NullPointers!!!
            Parent root = fxmlLoader.load();

            DownloadFileInfoController downloadFileInfoController = fxmlLoader.getController();
            downloadFileInfoController.setUrl(potentialUrl);

            Stage viewDownloadFileInfo = new Stage();
            // stops the user from interacting with any other window other than the one currently visible
            viewDownloadFileInfo.initModality(Modality.APPLICATION_MODAL);
            viewDownloadFileInfo.setResizable(false);
            viewDownloadFileInfo.setTitle("Download File info");
            viewDownloadFileInfo.setScene(new Scene(root));
            viewDownloadFileInfo.show();

            downloadFileInfoController.createTask(potentialUrl);
        } else {
            log.info("Validation Error, The entered URL is not valid. Please enter a valid URL (e.g., http://example.com).");
        }
    }

    /**
     * Handles the action of the "Cancel" button.
     * This method name must match the onAction attribute in the FXML.
     */
    @FXML
    private void handleCancelAction(ActionEvent event) {
        log.info("In Add URL Scene Cancel button clicked!");
        closeDialog();
    }

    /**
     * A helper method to close the dialog window.
     */
    private void closeDialog() {
        // Get the stage (window) that this controller's view is in and close it.
        Stage stage = (Stage) okButton.getScene().getWindow();
        stage.close();
    }

}
