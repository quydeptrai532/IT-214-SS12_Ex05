package com.storex.order.exception;

/** LOI NGHIEP VU tu GHTK (VD: ma don hang khong hop le) -> KHONG duoc retry. */
public class WaybillRejectedException extends RuntimeException {
    public WaybillRejectedException(String message) {
        super(message);
    }
}
