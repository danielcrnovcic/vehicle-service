module VehicleServiceClientApp {
    requires java.logging;
    
    exports org.unibl.etf.mdp.vservice.client.main;
    exports org.unibl.etf.mdp.vservice.client.controller;
	
    opens org.unibl.etf.mdp.vservice.client.controller;
    exports org.unibl.etf.mdp.vservice.client.model;
    opens org.unibl.etf.mdp.vservice.client.model;
    requires com.google.gson;
    requires java.net.http;
    requires javafx.base;
    requires javafx.controls;
    requires javafx.media;
    requires javafx.web;
    requires javafx.fxml;
    requires javafx.swing;
    requires javafx.graphics;
}