package com.github.sinakarimi.jdown;

import com.github.sinakarimi.jdown.common.FileSizeUtil;
import com.github.sinakarimi.jdown.download.DownloadTask;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

public class FilePropertiesController {

    @FXML
    private Label fileNameLabel;

    @FXML
    private Label typeValueLabel;

    @FXML
    private Label statusValueLabel;

    @FXML
    private Label sizeValueLabel;

    @FXML
    private TextField saveToTextField;

    @FXML
    private TextField addressTextField;

    @FXML
    private TextField descriptionTextField;

    public void initValues(DownloadTask task) {
        fileNameLabel.setText(task.getName());
        typeValueLabel.setText(task.getType());
        statusValueLabel.setText(task.getStatus().getValue());
        String size = FileSizeUtil.calculateSize(task.getSize());
        sizeValueLabel.setText(size);
        saveToTextField.setText(task.getSavePath());
        addressTextField.setText(task.getDownloadUrl());
        descriptionTextField.setText(task.getDescription());
    }

}
