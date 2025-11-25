module com.example.xmleditorapp {
    requires javafx.controls;
    requires javafx.fxml;

    requires com.almasb.fxgl.all;
    requires javafx.web;

    opens com.example.xmleditorapp to javafx.fxml;
    exports com.example.xmleditorapp;
    exports com.example.xmleditorapp.xml;
}