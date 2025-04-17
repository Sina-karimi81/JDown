module com.github.sinakarimi81.jdown {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires net.synedra.validatorfx;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.bootstrapfx.core;
    requires java.net.http;
    requires static lombok;
    requires org.xerial.sqlitejdbc;

    opens com.github.sinakarimi81.jdown to javafx.fxml;
    exports com.github.sinakarimi81.jdown;
}