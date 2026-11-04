package org.unibl.etf.mdp.vservice.supplier.server;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.unibl.etf.mdp.vservice.supplier.model.Part;
import org.unibl.etf.mdp.vservice.supplier.rmi.AccountingInterface;
import org.unibl.etf.mdp.vservice.supplier.util.PropertiesUtil;
import org.unibl.etf.mdp.vservice.supplier.util.AppLogger; 
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level; 
import java.util.logging.Logger;

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket; 

public class SupplierServer {

    private static final int PORT = PropertiesUtil.getIntProperty("SUPPLIER_SERVER_PORT");
    
    private static final String LINK_FILE = PropertiesUtil.getProperty("LINK_FILE");

    // Inicijalizacija logera kao statičko polje klase
    private static final Logger LOGGER = AppLogger.getLogger();

    // Sinhronizovana lista dostupnih dijelova
    private static List<Part> availableParts = Collections.synchronizedList(new ArrayList<>());
    
    // Mapa za čuvanje statusa narudžbi
    public static final Map<String, String> orderStatuses = new ConcurrentHashMap<>();
    
    public static Registry registry;
    public static AccountingInterface accountingService;

    public static void main(String[] args) { // Izmenjeno da ne baca IOException
	System.setProperty("javax.net.ssl.keyStore", PropertiesUtil.getProperty("KEY_STORE_PATH"));
        System.setProperty("javax.net.ssl.keyStorePassword", PropertiesUtil.getProperty("KEY_STORE_PASSWORD"));
        
        System.setProperty("java.security.policy", "policy.txt");
        if(System.getSecurityManager() == null) {
            System.setSecurityManager(new SecurityManager());
            LOGGER.info("Security Manager je postavljen.");
        }
        
        createRMIObject();

        try(SSLServerSocket ss = (SSLServerSocket) SSLServerSocketFactory.getDefault().createServerSocket(PORT)) {
            
//          ServerSocket serverSocket = new ServerSocket(PORT); // Koristi PORT varijablu iz PropertiesUtil
            LOGGER.info("Supplier server pokrenut na portu " + PORT + ". Čeka se konekcija...");
            // Učitavanje dijelova pri pokretanju servera
            LOGGER.info("Supplier Server: Učitavanje delova pri pokretanju...");
            loadPartsFromLink(LINK_FILE); // Pozovi statičku metodu za učitavanje
            LOGGER.info("Inicijalizacija završena. Učitano " + availableParts.size() + " dijelova.");
            
            while (true) {
        	SSLSocket clientSocket = (SSLSocket) ss.accept();
//                Socket clientSocket = serverSocket.accept();
                LOGGER.log(Level.INFO, "Klijent konektovan: {0}", clientSocket.getInetAddress());
                // Prosleđujemo listu delova, RMI servis i instancu logera niti
                new SupplierServerThread(clientSocket, availableParts, accountingService).start();
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Greška pri pokretanju ili radu servera: " + e.getMessage(), e);
        }
    }
    
    public static void createRMIObject() {
        try {
            LOGGER.info("Pokušavam da se povežem na RMI Registry na portu: " + PropertiesUtil.getIntProperty("ACCOUNTING_RMI_PORT"));
            // Ako se RMI server pokrece na drugom racunaru, ovde treba navesti njegovu IP adresu
            registry = LocateRegistry.getRegistry(PropertiesUtil.getIntProperty("ACCOUNTING_RMI_PORT")); // Koristi localhost ako nije eksplicitno naveden host
            LOGGER.info("Pronađen RMI Registry.");

            LOGGER.info("Pokušavam da pronađem RMI objekat sa imenom: " + PropertiesUtil.getProperty("ACCOUNTING_RMI_NAME"));
            accountingService = (AccountingInterface) registry.lookup(PropertiesUtil.getProperty("ACCOUNTING_RMI_NAME"));
            LOGGER.info("RMI servis 'AccountingService' uspješno pronađen.");
        } catch(Exception e) {
            LOGGER.log(Level.SEVERE, "Greška pri povezivanju na RMI Registry ili pronalaženju servisa: " + e.getMessage(), e);
        }
    }
    
    public static void loadPartsFromLink(String url) {
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36")
                    .referrer("https://www.google.com")
                    .timeout(15000)
                    .get();

            Elements products = doc.select("div.brand-products div.item");

            for (Element product : products) {
                // Šifra
                String code = product.select("div.nr span").text().replace("Art. Nr.:", "").trim();
                
                if(code.equals("402B0282P")) {
                    LOGGER.info("Preskačem deo sa šifrom: " + code);
                    continue;
                }

                // Naslov – samo prvi red
                Element titleElement = product.selectFirst("div.prod_link a");
                String title = (titleElement != null) ? titleElement.ownText().trim() : "N/A";
                
                // Manufacturer – zaustavi kada naiđeš na šifru (može imati razmake)
                String manufacturer = "N/A";
                if (!title.equals("N/A") && !code.isEmpty()) {
                    String[] titleWords = title.split("\\s+");
                    String[] codeWords = code.split("\\s+");

                    StringBuilder builder = new StringBuilder();

                    for (int i = 0; i < titleWords.length; i++) {
                        boolean isCodeStart = true;

                        // Provjera: da li od ove pozicije počinje cijela šifra
                        for (int j = 0; j < codeWords.length && i + j < titleWords.length; j++) {
                            if (!titleWords[i + j].equalsIgnoreCase(codeWords[j])) {
                                isCodeStart = false;
                                break;
                            }
                        }

                        if (isCodeStart) {
                            break; // prepoznata šifra – prekini
                        }

                        if (!titleWords[i].equalsIgnoreCase("Bremsbeläge")) {
                            if (builder.length() > 0) builder.append(" ");
                            builder.append(titleWords[i]);
                        }
                    }
                    manufacturer = builder.toString().trim();
                }

                // Cijena – ukloni € i zamijeni zarez sa tackom
                double price = Double.parseDouble(product.select("div.price").text().replace("€", "").replace(",", ".").trim());

                // Slika – uzmi src iz <img>
                Element imgElement = product.selectFirst("img[id^=full_src_]");
                String imageUrl = (imgElement != null) ? imgElement.attr("src").trim() : "N/A";
                Random random = new Random();
                
                int quantity = random.nextInt(91) + 10;
                
                Part part = new Part(code, title, manufacturer, price, quantity, imageUrl);
                availableParts.add(part);

                if (!code.isEmpty() && !title.equals("N/A")) {
                    LOGGER.log(Level.INFO, "Učitan deo: Šifra=''{0}'', Naslov=''{1}'', Proizvođač=''{2}'', Cena={3}, Količina={4}, Slika=''{5}''",
                            new Object[]{code, title, manufacturer, price, quantity, imageUrl});
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Greška pri učitavanju delova sa linka: " + url + " - " + e.getMessage(), e);
        }
    }

    // Metoda za pristup dijelovima iz drugih klasa, npr. iz SupplierServerThread
    public static List<Part> getAvailableParts() {
        return availableParts;
    }
}
