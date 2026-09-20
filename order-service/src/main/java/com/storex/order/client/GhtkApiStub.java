package com.storex.order.client;
import com.storex.order.exception.WaybillRejectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Gia lap GHTK - dac thu "chop nhay" mang:
 *   failFirstCalls lan goi dau bi TimeoutException, sau do thi thanh cong.
 */
@Component
public class GhtkApiStub implements GhtkApi {
    private static final Logger log = LoggerFactory.getLogger(GhtkApiStub.class);

    private final AtomicInteger attempts = new AtomicInteger(0);
    private volatile int failFirstCalls = 0;
    private volatile boolean alwaysTimeout = false;
    private volatile boolean businessError = false;

    public void reset(int failFirstCalls, boolean alwaysTimeout, boolean businessError) {
        this.attempts.set(0);
        this.failFirstCalls = failFirstCalls;
        this.alwaysTimeout = alwaysTimeout;
        this.businessError = businessError;
    }

    public int getAttempts() {
        return attempts.get();
    }

    @Override
    public String createWaybill(String orderId) throws TimeoutException {
        int n = attempts.incrementAndGet();
        if (businessError) {
            log.warn("[GHTK] goi lan {} -> loi nghiep vu (KHONG the retry)", n);
            throw new WaybillRejectedException("GHTK: ma don hang khong hop le");
        }
        if (alwaysTimeout || n <= failFirstCalls) {
            log.warn("[GHTK] goi lan {} -> TIMEOUT (mang chop nhay)", n);
            throw new TimeoutException("GHTK timeout lan " + n);
        }
        log.info("[GHTK] goi lan {} -> THANH CONG, tao van don cho {}", n, orderId);
        return "WAYBILL-" + orderId;
    }
}
