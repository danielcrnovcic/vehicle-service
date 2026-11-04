package org.unibl.etf.mdp.vservice.supplier.rmi;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID; 
import java.util.logging.Level; 
import java.util.logging.Logger; 

import org.unibl.etf.mdp.vservice.supplier.model.Bill;
import org.unibl.etf.mdp.vservice.supplier.util.PropertiesUtil;
import org.unibl.etf.mdp.vservice.supplier.util.AppLogger; 

public class AccountingServer implements AccountingInterface {

    private static final long serialVersionUID = 1L;
    private static final String BILL_FILE = PropertiesUtil.getProperty("BILL_FILE");
    private List<Bill> storedBills;
    private static final Logger LOGGER = AppLogger.getLogger(); 

    protected AccountingServer() throws RemoteException {
        super();
        storedBills = loadBills();
        LOGGER.info("KnjigovodstvoService started."); 
    }
    
    @Override
    public double processBill(Bill bill) throws RemoteException {
        LOGGER.info("Received bills for processing:"); 
        LOGGER.info(bill.toString()); 

        // Generate unique ID for the bill
        String billId = "INV-" + UUID.randomUUID().toString();
        bill.setId(billId);

        // Calculate VAT (17%)
        double vatRate = 0.17;
        double vatAmount = bill.getTotalAmount() * vatRate;

        LOGGER.info("Generated Bill ID: " + billId); 
        LOGGER.info("Calculated VAT (17%): " + String.format("%.2f", vatAmount)); 

        // Store the bill
        storedBills.add(bill);
        saveBills(storedBills);
        LOGGER.info("Bill stored. Total invoices: " + storedBills.size()); 

        return vatAmount;
    }

    // Method to save bills to a file (serialization)
    private void saveBills(List<Bill> bills) {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(BILL_FILE))) {
            oos.writeObject(bills);
            LOGGER.info("Bills saved to " + BILL_FILE); 
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error saving bills: " + e.getMessage(), e); 
        }
    }

    // Method to load bills from a file (deserialization)
    private List<Bill> loadBills() {
        File file = new File(BILL_FILE);
        if (!file.exists() || file.length() == 0) {
            LOGGER.info("Bill file not found or is empty. Starting with empty list."); 
            return new ArrayList<>();
        }

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(BILL_FILE))) {
            List<Bill> bills = (List<Bill>) ois.readObject();
            LOGGER.info("Loaded " + bills.size() + " bills from " + BILL_FILE); 
            return bills;
        } catch (IOException | ClassNotFoundException e) {
            LOGGER.log(Level.SEVERE, "Error loading bills: " + e.getMessage(), e); 
            return new ArrayList<>(); // Return empty list on error
        }
    }

    // Method to get all stored bills 
    public List<Bill> getStoredBills() {
        return storedBills;
    }
    
    //Standard Java RMI server initialization
    @SuppressWarnings({ "removal", "deprecation" })
    public static void main(String args[]) {
        System.setProperty("java.security.policy", "policy.txt");
        if(System.getSecurityManager() == null)
            System.setSecurityManager(new SecurityManager());
        
        try {
            
            AccountingServer server = new AccountingServer();
            AccountingInterface stub = (AccountingInterface) UnicastRemoteObject.exportObject(server, 0);
            Registry registry = LocateRegistry.createRegistry(PropertiesUtil.getIntProperty("ACCOUNTING_RMI_PORT"));
            LOGGER.info("RMI Registry created on port " + PropertiesUtil.getIntProperty("ACCOUNTING_RMI_PORT")); // Logovano sa INFO nivoom
            registry.rebind(PropertiesUtil.getProperty("ACCOUNTING_RMI_NAME"), stub);
            LOGGER.info("Supplier bound in registry."); 
            LOGGER.info("Accounting Server is running...");
        }
        catch(Exception e) {
            LOGGER.log(Level.SEVERE, "Error starting Accounting Server", e);
        }
    }
}
