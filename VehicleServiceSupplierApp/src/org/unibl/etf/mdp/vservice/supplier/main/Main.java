package org.unibl.etf.mdp.vservice.supplier.main;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.unibl.etf.mdp.vservice.supplier.controller.SupplierDashboardViewController;
import org.unibl.etf.mdp.vservice.supplier.util.PropertiesUtil;
import org.unibl.etf.mdp.vservice.supplier.util.AppLogger; 

public class Main extends Application{
	
    private static final Logger LOGGER = AppLogger.getLogger();

	@Override
	public void start(Stage stage) {
		try {
			Parent root = FXMLLoader.load(getClass().getResource("/org/unibl/etf/mdp/vservice/supplier/view/LoginView.fxml"));
			Scene scene = new Scene(root);
			stage.setScene(scene);
			stage.show();
            LOGGER.info("Aplikacija uspješno pokrenuta i prikazan LoginView.");
		} catch (IOException e) { 
		    LOGGER.log(Level.SEVERE, "Greška pri učitavanju LoginView.fxml: " + e.getMessage(), e);
		} catch (Exception e) { 
		    LOGGER.log(Level.SEVERE, "Neočekivana greška prilikom pokretanja aplikacije: " + e.getMessage(), e);
		}
	}

	public static void main(String[] args) { 
		// Inicijalizacija QUEUE_NAME za kontroler
		SupplierDashboardViewController.QUEUE_NAME = PropertiesUtil.getProperty("QUEUE_NAME");
		LOGGER.info("QUEUE_NAME postavljen na: " + SupplierDashboardViewController.QUEUE_NAME);
		
		launch(args);
	}
}
