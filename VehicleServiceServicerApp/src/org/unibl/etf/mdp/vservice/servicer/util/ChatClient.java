package org.unibl.etf.mdp.vservice.servicer.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress; 
import java.net.Socket;
import java.util.Properties; 
import java.util.function.Consumer;
import java.util.logging.Level; 
import java.util.logging.Logger; 

import javax.net.ssl.SSLContext; 
//import javax.net.ssl.SSLSocket; 
//import javax.net.ssl.SSLSocketFactory; 
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import org.unibl.etf.mdp.vservice.servicer.util.AppLogger; 

public class ChatClient {

    private static final Logger LOGGER = AppLogger.getLogger();

    private SSLSocket socket; // Promijenjeno u SSLSocket 
//    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String username;
    private Consumer<String> messageHandler;


    static { 

            System.setProperty("javax.net.ssl.trustStore", PropertiesUtil.getProperty("KEY_STORE_PATH"));
            System.setProperty("javax.net.ssl.trustStorePassword", PropertiesUtil.getProperty("KEY_STORE_PASSWORD"));


    }

    public ChatClient(String username, Consumer<String> messageHandler) {
        this.username = username;
        this.messageHandler = messageHandler;
        LOGGER.log(Level.INFO, "ChatClient kreiran za korisnika: {0}", username); 
    }

    public void connect(String serverAddress, int serverPort) throws IOException {
        LOGGER.log(Level.INFO, "Pokušavam se povezati na {0}:{1} kao {2}", new Object[]{serverAddress, serverPort, this.username}); // Zamijenjeno System.out.println

        SSLSocketFactory socketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault(); 

        try {
            socket = (SSLSocket) socketFactory.createSocket(serverAddress, serverPort); 
//            socket.setEnabledProtocols(new String[]{"TLSv1.2", "TLSv1.3"}); 
//            socket.setEnabledCipherSuites(socket.getSupportedCipherSuites()); 
//            socket = new Socket(serverAddress,serverPort);

            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            out.println(this.username); // Šalje korisničko ime serveru

            LOGGER.info("Uspješno povezan na chat server kao " + this.username + "."); 

            new Thread(this::listenForMessages).start();

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Greška pri povezivanju na chat server: " + e.getMessage(), e); 
            throw e; // Ponovo baci izuzetak, jer GUI aplikacija treba da reaguje na neuspjeh povezivanja
        }
    }

    private void listenForMessages() {
        String message;
        try {
            while ((message = in.readLine()) != null) {
                if (messageHandler != null) {
                    messageHandler.accept(message);
                }
            }
            LOGGER.info("Server zatvorio konekciju."); 

        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Greška pri čitanju poruka od servera: " + e.getMessage(), e); 
        } finally {
            LOGGER.info("Završeno slušanje poruka."); 
            close(); // Zatvori resurse
            if (messageHandler != null) {
                messageHandler.accept("--- Odspojeni ste sa chata ---");
            }
        }
    }

    // Metoda za slanje broadcast poruke
    public void sendBroadcastMessage(String message) {
        LOGGER.log(Level.INFO, "Slanje broadcast poruke: {0}", message); 
        sendMessage(message); // Broadcast je default, nema prefiks
    }

    // Metoda za slanje unicast poruke
    public void sendUnicastMessage(String recipientUsername, String message) {
        if (recipientUsername != null && !recipientUsername.trim().isEmpty()) {
            LOGGER.log(Level.INFO, "Slanje unicast poruke korisniku {0}: {1}", new Object[]{recipientUsername, message}); 
            sendMessage("TO:" + recipientUsername + ":" + message);
        } else {
            LOGGER.warning("Neispravno korisničko ime primaoca za unicast poruku."); 
        }
    }

    // Metoda za slanje multicast poruke grupi
    public void sendMulticastMessage(String groupName, String message) {
        if (groupName != null && !groupName.trim().isEmpty()) {
            LOGGER.log(Level.INFO, "Slanje multicast poruke grupi {0}: {1}", new Object[]{groupName, message}); 
            sendMessage("GROUP:" + groupName + ":" + message);
        } else {
            LOGGER.warning("Neispravno ime grupe za multicast poruku."); 
        }
    }
    
    // Genericka metoda za slanje poruke serveru, koja će interno dodati prefiks
    // Ova metoda je sada privatna ili za internu upotrebu unutar ove klase
    private void sendMessage(String formattedMessage) {
        if (out != null && !out.checkError()) { // Provera da li PrintWriter ima greške
            if (formattedMessage != null && !formattedMessage.trim().isEmpty()) {
                out.println(formattedMessage);
                LOGGER.log(Level.FINE, "Poslata poruka serveru: {0}", formattedMessage); 
            }
        } else {
            LOGGER.log(Level.SEVERE, "Ne mogu poslati poruku: Klijent nije povezan ili stream je zatvoren. Poruka: {0}", formattedMessage); 
        }
    }
    
    /*
     * Možda bi bilo jasnije preimenovati je u sendRawMessage kako bi se razlikovala od javnih sendBroadcastMessage, sendUnicastMessage, sendMulticastMessage
     * metoda koje formatiraju poruku prije pozivanja ove interne metode. Logika je ispravna, samo je stvar čitljivosti.
     */
    
    //GUI provjerava status konekcije prije slanja poruka
    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }
    
// Metoda za slanje komande pridruživanja grupi
    public void joinGroup(String groupName) {
        if (groupName != null && !groupName.trim().isEmpty()) {
            sendMessage("JOIN:" + groupName);
            LOGGER.log(Level.INFO, "Poslato serveru: JOIN:{0}", groupName);
        } else {
            LOGGER.warning("Neispravno ime grupe za pridruživanje."); 
        }
    }

    // Metoda za slanje komande napuštanja grupe
    public void leaveGroup(String groupName) {
        if (groupName != null && !groupName.trim().isEmpty()) {
            sendMessage("LEAVE:" + groupName);
            LOGGER.log(Level.INFO, "Poslato serveru: LEAVE:{0}", groupName); 
        } else {
            LOGGER.warning("Neispravno ime grupe za napuštanje."); 
        }
    }


    public void close() {
        LOGGER.info("Zatvaranje klijentske konekcije za chat."); 
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
                LOGGER.info("Klijentski socket uspješno zatvoren.");
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Greška pri zatvaranju klijentskih resursa: " + e.getMessage(), e); 
        }
    }
    //nije potrebna za GUI aplikaciju
    public static void main(String[] args) {
        try {
            Consumer<String> consoleMessageHandler = msg -> System.out.println("PRIMLJENO: " + msg);

            // Kreiranje klijentske instance sa korisničkim imenom
            // Ovdje se moze unijeti ime iz komandne linije ili GUI-a
            String clientUsername = args.length > 0 ? args[0] : "TestKorisnik"; 
            ChatClient client = new ChatClient(clientUsername, consoleMessageHandler);

            String serverAddress = PropertiesUtil.getProperty("CHAT_SERVER_HOST");
            int serverPort = Integer.parseInt(PropertiesUtil.getProperty("CHAT_SERVER_PORT"));

            client.connect(serverAddress, serverPort);

            System.out.println("Povezan kao '" + clientUsername + "'. Unesi poruku:");
            System.out.println("Za privatnu poruku: TO:<ime_primaoca>:<poruka>");
            System.out.println("Za grupnu poruku: GROUP:<ime_grupe>:<poruka>");
            System.out.println("Za broadcast poruku: <poruka>");
            System.out.println("Unesi 'izlaz' za prekid.");

            BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));
            String input;
            while ((input = consoleReader.readLine()) != null && !input.equalsIgnoreCase("izlaz")) {
                if (input.startsWith("TO:") || input.startsWith("GROUP:")) {
                    // Već formatirano, poziva generičku sendMessage koja je sada privatna
                    client.sendMessage(input); 
                } else {
                    // Standardni broadcast
                    client.sendBroadcastMessage(input);
                }
            }

            client.close();

        } catch (IOException e) {
            System.err.println("Klijent se nije uspio povezati ili je doslo do greske tokom rada: " + e.getMessage());
            e.printStackTrace();
        } catch (NumberFormatException e) {
            System.err.println("Neispravan format porta u properties fajlu: " + e.getMessage());
        }
    }
}
