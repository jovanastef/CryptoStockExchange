package cryptoexchange.rmi;

import cryptoexchange.model.*;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

//RMI interfejs za komunikaciju klijent-server

public interface ExchangeServiceInterface extends Remote {
    
    /**
     * Dohvata pocetni snapshot trzista sa svim instrumentima
     * @return MarketSnapshot sa trenutnim stanjem svih instrumenata
     * @throws RemoteException ako dodje do greske u komunikaciji
     */
    MarketSnapshot getMarketSnapshot() throws RemoteException;
    
    /**
     * Dohvata order book za odredjeni instrument i stranu (BID/ASK)
     * @param symbol simbol instrumenta (npr. "BTC")
     * @param side BID za kupovinu, ASK za prodaju
     * @return OrderBookData sa sortiranim nalozima
     * @throws RemoteException ako dodje do greske u komunikaciji
     */
    OrderBookData getOrderBook(String symbol, OrderBookData.Side side) throws RemoteException;
    
    /**
     * Salje nalog za kupovinu/prodaju
     * @param order zahtev sa podacima o nalogu
     * @return OrderConfirmation sa statusom i ID-jem naloga
     * @throws RemoteException ako dodje do greske u komunikaciji
     */
    OrderConfirmation placeOrder(OrderRequest order) throws RemoteException;
    
    /**
     * Dohvata istoriju trgovina za instrument i dan (simulaciono vreme)
     * @param symbol simbol instrumenta
     * @param simulationDayStart pocetak dana u minutima (npr. 0 za ponedeljak 00:00)
     * @return lista izvršenih trgovina
     * @throws RemoteException ako dodje do greske u komunikaciji
     */
    List<Trade> getTradeHistory(String symbol, long simulationDayStart) throws RemoteException;
    
    /**
     * Registruje novog klijenta i vraca jedinstveni ID
     * @param clientName ime klijenta za identifikaciju
     * @return jedinstveni ID klijenta
     * @throws RemoteException ako dodje do greske u komunikaciji
     */
    String registerClient(String clientName) throws RemoteException;
}