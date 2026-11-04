package org.unibl.etf.mdp.vservice.servicer.main;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.unibl.etf.mdp.vservice.servicer.util.PropertiesUtil;
import org.unibl.etf.mdp.vservice.servicer.util.AppLogger; 

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
			Parent root = FXMLLoader.load(getClass().getResource("/org/unibl/etf/mdp/vservice/servicer/view/ServicerDashboardView.fxml"));
			Scene scene = new Scene(root);
			stage.setScene(scene);
			stage.show();
            LOGGER.info("Serviser aplikacija uspješno pokrenuta i prikazan ServicerDashboardView.");
		} catch (IOException e) { 
		    LOGGER.log(Level.SEVERE, "Greška pri učitavanju ServicerDashboardView.fxml: " + e.getMessage(), e);
		} catch (Exception e) { 
		    LOGGER.log(Level.SEVERE, "Neočekivana greška prilikom pokretanja Serviser aplikacije: " + e.getMessage(), e);
		}
	}
	
	public static void main(String[] args) { 
	    if (!SingleInstanceSocketApp.acquireLock()) {
	        LOGGER.info("Serviser aplikacija je već pokrenuta. Izlazim.");
	        System.exit(0);
	    }
	               		
	    launch(args);
	}
}
