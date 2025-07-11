package com.github.sinakarimi.jdown;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;

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
     * Handles the action of the "Ok" button.
     * This method name must match the onAction attribute in the FXML.
     */
    @FXML
    private void handleOkAction(ActionEvent event) {
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
