module VehicleServiceSupplierApp {
    
    requires java.logging;
    requires amqp.client;

    
    exports org.unibl.etf.mdp.vservice.supplier.main;
    exports org.unibl.etf.mdp.vservice.supplier.controller;
    exports org.unibl.etf.mdp.vservice.supplier.rmi;

	
    opens org.unibl.etf.mdp.vservice.supplier.controller;
    exports org.unibl.etf.mdp.vservice.supplier.model;
    opens org.unibl.etf.mdp.vservice.supplier.model;
    requires java.net.http;
    requires javafx.base;
    requires javafx.controls;
    requires javafx.media;
    requires javafx.web;
    requires javafx.fxml;
    requires javafx.swing;
    requires javafx.graphics;
    requires java.rmi;
    requires com.google.gson;
    requires org.jsoup;
}