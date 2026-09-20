package com.storex.order;
import com.storex.order.client.GhtkApiStub;
import com.storex.order.service.OrderService;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class GhtkRetryTest {

    private static final String RETRY_NAME = "ghtkClient";

    @Autowired
    private OrderService orderService;

    @Autowired
    private GhtkApiStub ghtkApiStub;

    @Autowired
    private RetryRegistry retryRegistry;

    @Test
    void cauHinhRetryDuocNapDung() {
        Retry retry = retryRegistry.retry(RETRY_NAME);
        System.out.println(">>> [CONFIG] maxAttempts=" + retry.getRetryConfig().getMaxAttempts()
                + " | (waitDuration=2s, retryExceptions=TimeoutException - khai bao trong application.yml)");
        assertThat(retry.getRetryConfig().getMaxAttempts()).isEqualTo(3);
    }

    @Test
    void retryKhiGapTimeoutRoiThanhCong() throws Exception {
        // 2 lan dau timeout, lan thu 3 thanh cong
        ghtkApiStub.reset(2, false, false);

        long start = System.currentTimeMillis();
        String result = orderService.createWaybill("ORD-100");
        long elapsed = System.currentTimeMillis() - start;

        System.out.println(">>> [TRANSIENT] Ket qua=" + result + " | so lan goi GHTK=" + ghtkApiStub.getAttempts()
                + " | thoi gian=" + elapsed + "ms (2 lan cho x 2s)");

        assertThat(result).isEqualTo("WAYBILL-ORD-100");
        assertThat(ghtkApiStub.getAttempts()).isEqualTo(3);
        assertThat(elapsed).isGreaterThanOrEqualTo(4000L);
    }

    @Test
    void khongRetryLoiNghiepVu() throws Exception {
        ghtkApiStub.reset(0, false, true);

        String result = orderService.createWaybill("ORD-200");

        System.out.println(">>> [BUSINESS ERROR] Ket qua=" + result + " | so lan goi GHTK=" + ghtkApiStub.getAttempts());
        assertThat(ghtkApiStub.getAttempts()).isEqualTo(1);
    }

    @Test
    void hetLuotRetryThiTraVeFallback() throws Exception {
        ghtkApiStub.reset(0, true, false);   // timeout mai mai

        String result = orderService.createWaybill("ORD-300");

        System.out.println(">>> [HET LUOT] Ket qua=" + result + " | so lan goi GHTK=" + ghtkApiStub.getAttempts());
        assertThat(result).startsWith("GHTP-FAILED");
        assertThat(ghtkApiStub.getAttempts()).isEqualTo(3);
    }
}
