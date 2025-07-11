module com.github.sinakarimi.jdown {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires net.synedra.validatorfx;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.fontawesome5;
    requires org.kordamp.bootstrapfx.core;
    requires java.net.http;
    requires static lombok;
    requires org.xerial.sqlitejdbc;
    requires org.slf4j;
    requires com.fasterxml.jackson.databind;

    opens com.github.sinakarimi.jdown to javafx.fxml;
    exports com.github.sinakarimi.jdown;
    exports com.github.sinakarimi.jdown.common;
    exports com.github.sinakarimi.jdown.configuration;
    exports com.github.sinakarimi.jdown.database;
    exports com.github.sinakarimi.jdown.dataObjects;
    exports com.github.sinakarimi.jdown.download;
    exports com.github.sinakarimi.jdown.exception;
    exports com.github.sinakarimi.jdown.serialization;
}