module VehicleServiceServicerApp {
    requires java.logging;
    
    exports org.unibl.etf.mdp.vservice.servicer.main;
    exports org.unibl.etf.mdp.vservice.servicer.controller;
	
    opens org.unibl.etf.mdp.vservice.servicer.controller;
    exports org.unibl.etf.mdp.vservice.servicer.model;
    opens org.unibl.etf.mdp.vservice.servicer.model;
    requires java.net.http;
    requires javafx.base;
    requires javafx.controls;
    requires javafx.media;
    requires javafx.web;
    requires javafx.fxml;
    requires javafx.swing;
    requires javafx.graphics;
    requires com.google.gson;
}