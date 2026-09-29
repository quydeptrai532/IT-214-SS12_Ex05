# BÀI TẬP 5 (SS12) — CIRCUIT BREAKER vs RETRY (GHTK)

Xem phân tích + bảng so sánh + Idempotency tại `Ex05_Analysis.md`, bằng chứng tại `Ex05_TestEvidence.txt`.

## Cấu trúc

```
Ex05/
├── Ex05_Analysis.md          # bang so sanh 2 giai phap + phan tich Idempotency
├── Ex05_TestEvidence.txt
├── README.md
└── order-service/
    ├── build.gradle
    └── src/main/
        ├── java/com/storex/order/
        │   ├── service/OrderService.java          # @Retry(name="ghtkClient")
        │   ├── client/GhtkApiStub.java            # gia lap GHTK "chop nhay" mang
        │   └── exception/WaybillRejectedException.java
        └── resources/application.yml              # ★ maxAttempts / waitDuration 2s / retryExceptions
```

## Chạy test

```bash
cd order-service
./gradlew test
```

## Kết quả

```
>>> [CONFIG] maxAttempts=3 | (waitDuration=2s, retryExceptions=TimeoutException)
>>> [TRANSIENT] Ket qua=WAYBILL-ORD-100 | so lan goi GHTK=3 | thoi gian=4049ms
>>> [BUSINESS ERROR] Ket qua=GHTP-FAILED:ORD-200 (WaybillRejectedException) | so lan goi GHTK=1
>>> [HET LUOT] Ket qua=GHTP-FAILED:ORD-300 (TimeoutException) | so lan goi GHTK=3
```

> `maxAttempts: 3` = 1 lần gọi đầu + 2 lần retry (Resilience4j đếm cả lần đầu).
> Muốn đúng 3 lần **retry** thì đặt `maxAttempts: 4`.
a