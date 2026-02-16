package cryptoexchange.model;

import java.io.Serializable;

//Potvrda o prihvatanju/odbijanju naloga

public class OrderConfirmation implements Serializable {
    private static final long serialVersionUID = 1L;
    
    public enum Status { ACCEPTED, REJECTED, PARTIALLY_FILLED, FILLED }
    
    private final String orderId;
    private final Status status;
    private final String reason; // razlog odbijanja (ako je REJECTED)
    
    public OrderConfirmation(String orderId, Status status, String reason) {
        this.orderId = orderId;
        this.status = status;
        this.reason = reason;
    }
    
    public String getOrderId() {
        return orderId;
    }
    
    public Status getStatus() {
        return status;
    }
    
    public String getReason() {
        return reason;
    }
}