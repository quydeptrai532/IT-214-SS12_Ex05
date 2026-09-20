package com.storex.order.client;
import java.util.concurrent.TimeoutException;

/** Cong goi ra he thong van chuyen Giao Hang Nhanh (GHTK). */
public interface GhtkApi {
    String createWaybill(String orderId) throws TimeoutException;
}
