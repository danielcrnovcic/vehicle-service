package org.unibl.etf.mdp.vservice.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap; // Koristimo ConcurrentHashMap za thread-safe mapu
import java.util.logging.Level; // Import Level for logging
import java.util.logging.Logger; // Import Logger for logging

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;

import org.unibl.etf.mdp.vservice.util.PropertiesUtil;
import org.unibl.etf.mdp.vservice.util.AppLogger; 

public class ChatServer {

    private static final Logger LOGGER = AppLogger.getLogger();

    // Mapa za praćenje povezanih klijenata po njihovom korisničkom imenu
    // Omogućava unicast slanje poruka po imenu
    private static Map<String, ChatServerThread> connectedClientsMap = new ConcurrentHashMap<>();

    // Opciono: Mapa za grupne chatove (za multicast na specifične grupe)
    // Ključ: Ime grupe, Vrijednost: Lista korisničkih imena u toj grupi
    private static Map<String, List<String>> chatGroups = new ConcurrentHashMap<>();

    // Metoda za dodavanje nove klijentske niti u listu i mapu
    public static void addClient(ChatServerThread clientThread, String username) {
        connectedClientsMap.put(username, clientThread);
        LOGGER.log(Level.INFO, "Novi klijent ''{0}'' povezan. Ukupno povezanih: {1}", new Object[]{username, connectedClientsMap.size()});
        // Obavijesti sve da se novi klijent pridruzio
        broadcastMessage(username + " se pridruzio caskanju.", null);
    }

    // Metoda za uklanjanje klijentske niti iz liste i mape
    public static void removeClient(ChatServerThread clientThread, String username) {
        if (username != null) {
            connectedClientsMap.remove(username);
            LOGGER.log(Level.INFO, "Klijent ''{0}'' se odjavio. Ukupno povezanih: {1}", new Object[]{username, connectedClientsMap.size()});
            // Obavijesti sve da je klijent napustio chat
            broadcastMessage(username + " je napustio caskanju.", null);
        } else {
            LOGGER.info("Nepoznati klijent se odjavio. Ukupno povezanih: " + connectedClientsMap.size());
        }
    }

    // Metoda za slanje poruke svim klijentima (broadcast)
    // Sender je opcionalan, ako je null, poruka ide svima
    public static void broadcastMessage(String message, ChatServerThread sender) {
        LOGGER.info("Server broadcastuje: " + message);
        connectedClientsMap.values().forEach(client -> {
            if (client != sender && client.isOutputReady()) {
                client.sendMessage(message);
            }
        });
    }

    // Metoda za slanje poruke specifičnom klijentu (unicast)
    public static void unicastMessage(String recipientUsername, String message, String senderUsername) {
        ChatServerThread recipientThread = connectedClientsMap.get(recipientUsername);
        if (recipientThread != null && recipientThread.isOutputReady()) {
            LOGGER.log(Level.INFO, "Server šalje unicast poruku od ''{0}'' ka ''{1}'': {2}", new Object[]{senderUsername, recipientUsername, message});
            recipientThread.sendMessage("[PRIVATNO OD " + senderUsername + "]: " + message);
        } else {
            LOGGER.log(Level.WARNING, "Klijent ''{0}'' nije pronađen ili nije aktivan za unicast poruku.", recipientUsername);
            // Opciono: Obavijestiti pošiljaoca da primalac nije dostupan
            ChatServerThread senderThread = connectedClientsMap.get(senderUsername);
            if (senderThread != null && senderThread.isOutputReady()) {
                senderThread.sendMessage("[SERVER]: Korisnik " + recipientUsername + " nije dostupan.");
            }
        }
    }

    // Metoda za slanje poruke grupi klijenata (multicast)
    public static void multicastMessage(String groupName, String message, String senderUsername) {
        List<String> groupMembers = chatGroups.get(groupName);
        if (groupMembers != null && !groupMembers.isEmpty()) {
            LOGGER.log(Level.INFO, "Server šalje multicast poruku u grupu ''{0}'' od ''{1}'': {2}", new Object[]{groupName, senderUsername, message});
            for (String memberUsername : groupMembers) {
                ChatServerThread memberThread = connectedClientsMap.get(memberUsername);
                if (memberThread != null && memberThread.isOutputReady() && !memberUsername.equals(senderUsername)) {
                    memberThread.sendMessage("[GRUPA " + groupName + " OD " + senderUsername + "]: " + message);
                }
            }
        } else {
            LOGGER.log(Level.WARNING, "Grupa ''{0}'' nije pronađena ili nema članova.", groupName);
            ChatServerThread senderThread = connectedClientsMap.get(senderUsername);
            if (senderThread != null && senderThread.isOutputReady()) {
                senderThread.sendMessage("[SERVER]: Grupa " + groupName + " nije pronađena ili nema članova.");
            }
        }
    }
    
    // Metoda za dodavanje klijenta u grupu (potrebno za implementaciju grupnog chata)
    public static void addClientToGroup(String username, String groupName) {
        chatGroups.computeIfAbsent(groupName, k -> Collections.synchronizedList(new ArrayList<>())).add(username);
        LOGGER.log(Level.INFO, "Korisnik {0} dodan u grupu {1}", new Object[]{username, groupName});
    }

    // Metoda za uklanjanje klijenta iz grupe
    public static void removeClientFromGroup(String username, String groupName) {
        List<String> groupMembers = chatGroups.get(groupName);
        if (groupMembers != null) {
            groupMembers.remove(username);
            LOGGER.log(Level.INFO, "Korisnik {0} uklonjen iz grupe {1}", new Object[]{username, groupName});
            if (groupMembers.isEmpty()) {
                chatGroups.remove(groupName); // Ukloni grupu ako je prazna
                LOGGER.log(Level.INFO, "Grupa {0} uklonjena jer je prazna.", groupName);
            }
        }
    }


    public static void main(String[] args) throws Exception {


        System.setProperty("javax.net.ssl.keyStore", PropertiesUtil.getProperty("KEY_STORE_PATH"));
        System.setProperty("javax.net.ssl.keyStorePassword", PropertiesUtil.getProperty("KEY_STORE_PASSWORD"));
//        System.setProperty("javax.net.debug", "ssl,handshake,trustmanager,keymanager");

        SSLServerSocketFactory ssf = (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
        int port = PropertiesUtil.getIntProperty("CHAT_SERVER_PORT"); // Default port ako nema u propertijima
        SSLServerSocket ss = null;
//    ServerSocket ss = null;

        try {
            ss = (SSLServerSocket) ssf.createServerSocket(port);
//            ss.setEnabledProtocols(new String[]{"TLSv1.2", "TLSv1.3"});
//            ss.setEnabledCipherSuites(ss.getSupportedCipherSuites()); // Omogucava sve podržane cipher suite-ove
//            ss = new ServerSocket(PropertiesUtil.getIntProperty("CHAT_SERVER_PORT"));
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Greska pri kreiranju server socketa na portu " + PropertiesUtil.getIntProperty("CHAT_SERVER_PORT") + ": " + e.getMessage(), e);
            return;
        }

        LOGGER.info("Sigurni Cet Server pokrenut na portu " + PropertiesUtil.getIntProperty("CHAT_SERVER_PORT"));
        while (true) {
            try {
                SSLSocket s = (SSLSocket) ss.accept();
//            Socket s = ss.accept();
                LOGGER.info("Prihvacena nova konekcija od klijenta: " + s.getInetAddress().getHostAddress());

                ChatServerThread thread = new ChatServerThread(s);
                thread.start();

            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Greska pri prihvatanju klijentske konekcije: " + e.getMessage(), e);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Neocekivana greska u glavnoj petlji servera: " + e.getMessage(), e);
            }
        }
    }
}
