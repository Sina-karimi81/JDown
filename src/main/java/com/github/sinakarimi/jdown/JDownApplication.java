package com.github.sinakarimi.jdown;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class JDownApplication extends Application {
    @Override
    public void start(Stage stage) throws IOException {

        ClassManager.load();

        FXMLLoader fxmlLoader = new FXMLLoader(JDownApplication.class.getResource("DownloadManagerView.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 800, 600);
        stage.setTitle("JDown");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}