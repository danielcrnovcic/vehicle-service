package org.unibl.etf.mdp.vservice.supplier.rmi;
import java.rmi.Remote;
import java.rmi.RemoteException;

import org.unibl.etf.mdp.vservice.supplier.model.Bill;

public interface AccountingInterface extends Remote {
    /**
     * obradjuje racun, izracunava iznos PDV-a i cuva racun
     *
     * @param invoice objekat racuna koji se treba obraditi
     * @return 17% ukupnog iznosa
     * @throws RemoteException ako se dogodi RMI greska
     */
    double processBill(Bill bill) throws RemoteException;

}

