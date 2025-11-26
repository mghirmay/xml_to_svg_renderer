module com.example.xmleditorapp {
    requires javafx.controls;
    requires javafx.fxml;

    requires com.almasb.fxgl.all;
    requires javafx.web;
    requires org.json;
    requires jdk.jsobject;

    opens com.example.xmleditorapp to javafx.fxml;
    exports com.example.xmleditorapp;
    exports com.example.xmleditorapp.xml;
}