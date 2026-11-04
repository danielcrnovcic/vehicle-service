package org.unibl.etf.mdp.vservice.client.main;

import java.io.IOException;
import java.util.logging.Level; 
import java.util.logging.Logger; 

import org.unibl.etf.mdp.vservice.client.util.PropertiesUtil;
import org.unibl.etf.mdp.vservice.client.util.AppLogger;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {
	
    private static final Logger LOGGER = AppLogger.getLogger();

	public static String PATH;
	@Override
	public void start(Stage stage) {
		
		try {
			Parent root = FXMLLoader.load(getClass().getResource("/org/unibl/etf/mdp/vservice/client/view/LoginView.fxml"));
			Scene scene = new Scene(root);
			stage.setScene(scene);
			stage.show();
            LOGGER.info("Klijentska aplikacija uspješno pokrenuta i prikazan LoginView.");
		} catch (IOException e) { 
		    LOGGER.log(Level.SEVERE, "Greška pri učitavanju LoginView.fxml: " + e.getMessage(), e);
		} catch (Exception e) { 
		    LOGGER.log(Level.SEVERE, "Neočekivana greška prilikom pokretanja klijentske aplikacije: " + e.getMessage(), e);
		}

	}
	
	public static void main(String[] args) {

	   	PATH = PropertiesUtil.getProperty("BASE_URL");
                LOGGER.info("Konfiguracione postavke uspješno učitane. BASE_URL: " + PATH);
	    
		
		launch(args);
	}
}
