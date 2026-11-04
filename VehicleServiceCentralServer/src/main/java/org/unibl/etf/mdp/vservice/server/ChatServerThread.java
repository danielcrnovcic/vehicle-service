package org.unibl.etf.mdp.vservice.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLSocket;

import org.unibl.etf.mdp.vservice.util.AppLogger; 

public class ChatServerThread extends Thread {

    private static final Logger LOGGER = AppLogger.getLogger(); 

    private BufferedReader in;
    private PrintWriter out;
    private SSLSocket socket;
//    private Socket socket;
    private String clientName = "N/A";

    public String getClientName() {
        return clientName;
    }

    public ChatServerThread(SSLSocket socket) {
        this.socket = socket;

        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);

           // KLJUČNA PROVERA OVDE:
            // Server očekuje korisničko ime ODMAH po uspostavljanju konekcije.
            // Ako klijent ne pošalje ništa ili pošalje praznu liniju, readLine() može vratiti null.
            LOGGER.info("ServerThread: Pokusavam procitati korisnicko ime od klijenta " + socket.getInetAddress().getHostAddress());
            this.clientName = in.readLine();
            if (this.clientName == null || this.clientName.trim().isEmpty()) {
                // Dodajte jasniju poruku ako ime nije primljeno
                LOGGER.severe("Klijent sa adrese " + socket.getInetAddress().getHostAddress() + " nije poslao validno ime. Zatvaram konekciju.");
                this.clientName = "AnonimniKlijent-" + socket.getInetAddress().getHostAddress(); // Postavite default, ali bi bilo bolje zatvoriti konekciju
                throw new IOException("Klijent nije poslao korisničko ime."); // Odmah prekinite ako ime nije primljeno
            } else {
                this.clientName = this.clientName.trim();
                LOGGER.info("Klijent povezan sa imenom: " + this.clientName);
            }
            
            ChatServer.addClient(this, this.clientName);

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Greska pri inicijalizaciji streamova ili citanju imena za klijenta " + (socket != null ? socket.getInetAddress().getHostAddress() : "N/A") + ": " + e.getMessage(), e);
            try {
                if (socket != null) socket.close();
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, "Greska pri zatvaranju soketa nakon inicijalizacione greske: " + ex.getMessage(), ex);
            }
        }
    }

    public boolean isOutputReady() {
        return out != null && !out.checkError();
    }

    public void sendMessage(String message) {
        if (isOutputReady()) {
            out.println(message);
        } else {
            LOGGER.warning("Pokusaj slanja poruke na zatvoren izlazni stream za klijenta: " + this.clientName);
        }
    }

    @Override
    public void run() {
        if (in == null || out == null) {
            LOGGER.severe("Nit pokrenuta, ali streamovi nisu inicijalizovani za klijenta: " + this.clientName);
            ChatServer.removeClient(this, this.clientName);
            return;
        }

        String message;
        try {
            while ((message = in.readLine()) != null) {
                LOGGER.info("Primljeno od " + this.clientName + ": " + message);
                
                // JEDINSTVENI if-else if LANAC ZA OBRADU SVIH PORUKA
                if (message.startsWith("JOIN:")) {
                    String groupName = message.substring(5).trim();
                    if (!groupName.isEmpty()) {
                        ChatServer.addClientToGroup(this.clientName, groupName);
                        sendMessage("[SERVER]: Pridružili ste se grupi: " + groupName);
                        // Ne broadcastujemo JOIN/LEAVE svima kao chat poruku, već samo obavijest sa servera
                        ChatServer.broadcastMessage("[SERVER]: Klijent " + this.clientName + " se pridružio grupi " + groupName + ".", null);
                    } else {
                        sendMessage("[SERVER]: Neispravan format za pridruživanje grupi. Koristite: JOIN:<groupName>");
                    }
                } else if (message.startsWith("LEAVE:")) {
                    String groupName = message.substring(6).trim();
                    if (!groupName.isEmpty()) {
                        ChatServer.removeClientFromGroup(this.clientName, groupName);
                        sendMessage("[SERVER]: Napustili ste grupu: " + groupName);
                        // Ne broadcastujemo JOIN/LEAVE svima kao chat poruku, već samo obavijest sa servera
                        ChatServer.broadcastMessage("[SERVER]: Klijent " + this.clientName + " je napustio grupu " + groupName + ".", null);
                    } else {
                        sendMessage("[SERVER]: Neispravan format za napuštanje grupe. Koristite: LEAVE:<groupName>");
                    }
                } else if (message.startsWith("TO:")) {
                    // Unicast poruka
                    String[] parts = message.substring(3).split(":", 2); // TO:recipient:message
                    if (parts.length == 2) {
                        String recipient = parts[0];
                        String msgContent = parts[1];
                        ChatServer.unicastMessage(recipient, msgContent, this.clientName);
                    } else {
                        sendMessage("[SERVER]: Neispravan format unicast poruke. Koristite: TO:<username>:<message>");
                    }
                } else if (message.startsWith("GROUP:")) {
                    // Multicast poruka grupi
                    String[] parts = message.substring(6).split(":", 2); // GROUP:groupname:message
                    if (parts.length == 2) {
                        String groupName = parts[0];
                        String msgContent = parts[1];
                        ChatServer.multicastMessage(groupName, msgContent, this.clientName);
                    } else {
                        sendMessage("[SERVER]: Neispravan format grupne poruke. Koristite: GROUP:<groupname>:<message>");
                    }
                } else {
                    // Standardni broadcast (ako nema specifičnog prefiksa)
                    ChatServer.broadcastMessage(this.clientName + ": " + message, this);
                }
            }
            LOGGER.info("Klijent " + this.clientName + " je prekinuo konekciju (readLine vratio null).");

        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Greska u komunikaciji sa klijentom " + this.clientName + ": " + e.getMessage(), e);
        } finally {
            LOGGER.info("Zatvaranje konekcije i resursa za klijenta: " + this.clientName);
            try {
                if (in != null) in.close();
                if (out != null) out.close();
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Greska pri zatvaranju resursa za klijenta " + this.clientName + ": " + e.getMessage(), e);
            }
            ChatServer.removeClient(this, this.clientName);
        }
    }
}
