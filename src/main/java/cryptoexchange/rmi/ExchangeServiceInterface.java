package cryptoexchange.rmi;

import cryptoexchange.model.*;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface ExchangeServiceInterface extends Remote {
    MarketSnapshot getMarketSnapshot() throws RemoteException;
    OrderBookData getOrderBook(String symbol, OrderBookData.Side side) throws RemoteException;
    OrderConfirmation placeOrder(OrderRequest order) throws RemoteException;
    List<Trade> getTradeHistory(String symbol, long simulationDayStart) throws RemoteException;
    String registerClient(String clientName) throws RemoteException;
}