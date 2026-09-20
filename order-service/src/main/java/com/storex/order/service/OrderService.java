package com.storex.order.service;
import com.storex.order.client.GhtkApi;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.stereotype.Service;
import java.util.concurrent.TimeoutException;

@Service
public class OrderService {

    private final GhtkApi ghtkApi;

    public OrderService(GhtkApi ghtkApi) {
        this.ghtkApi = ghtkApi;
    }

    /**
     * Goi GHTK de tao van don, duoc bao ve boi Retry ten "ghtkClient".
     * Cau hinh trong application.yml:
     *   maxAttempts: 3, waitDuration: 2s, retryExceptions: TimeoutException
     * => chi retry khi gap loi TAM THOI (timeout), khong retry loi nghiep vu.
     */
    @Retry(name = "ghtkClient", fallbackMethod = "fallbackCreateWaybill")
    public String createWaybill(String orderId) throws TimeoutException {
        return ghtkApi.createWaybill(orderId);
    }

    public String fallbackCreateWaybill(String orderId, Throwable t) {
        return "GHTP-FAILED:" + orderId + " (" + t.getClass().getSimpleName() + ")";
    }
}
