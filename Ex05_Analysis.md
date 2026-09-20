# BÀI TẬP 5 (SS12) — TRADE-OFF: CIRCUIT BREAKER HAY RETRY PATTERN?

**Bối cảnh:** Order-Service gọi GHTK tạo vận đơn. GHTK hay bị **"chớp nháy" mạng**: thỉnh thoảng 1 request bị Timeout, nhưng gọi lại ngay thì **thành công** (Transient Failure).

---

## PHẦN 1 — ĐỀ XUẤT ĐA GIẢI PHÁP

### Giải pháp 1 — Circuit Breaker thuần túy

```yaml
resilience4j:
  circuitbreaker:
    instances:
      ghtkClient:
        slidingWindowSize: 10
        minimumNumberOfCalls: 5
        failureRateThreshold: 50
        waitDurationInOpenState: 30s
```

**Bản chất:** Đếm tỷ lệ lỗi. Khi vượt ngưỡng → **ngắt mạch**, không gọi GHTK nữa trong `waitDurationInOpenState`.

### Giải pháp 2 — Retry + Exponential Backoff *(được chọn)*

```yaml
resilience4j:
  retry:
    instances:
      ghtkClient:
        maxAttempts: 3
        waitDuration: 2s
        retryExceptions:
          - java.util.concurrent.TimeoutException
```

**Bản chất:** Gặp lỗi tạm thời → **chờ một chút rồi gọi lại**. Vì lỗi là "chớp nháy", lần gọi lại thường thành công ngay.

---

## PHẦN 2 — BẢNG SO SÁNH

| Tiêu chí | Giải pháp 1: Circuit Breaker thuần | Giải pháp 2: Retry + Backoff |
|---|---|---|
| **Cơ chế** | Đếm tỷ lệ lỗi trong cửa sổ → ngắt mạch khi vượt ngưỡng | Gọi lại N lần, mỗi lần chờ một khoảng |
| **Xử lý Transient Failure (chớp nháy)** | ❌ **Kém**: 1 lần timeout bị tính là lỗi. Vài lần timeout rải rác → failureRate vượt ngưỡng → **ngắt mạch oan**, chặn cả request đang khoẻ mạnh | ✅ **Tốt**: lần gọi lại thường thành công ⇒ **không tính là lỗi**, tỷ lệ lỗi thấp |
| **Xử lý System Crash (sập hẳn)** | ✅ **Tốt**: phát hiện service chết → ngắt mạch → **không gọi vào service đã chết**, cho nó thời gian hồi phục | ❌ **Kém**: retry vào service đã chết chỉ làm **tăng tải** lên service đang sập (càng retry càng chết), đồng thời **giữ thread của caller lâu hơn** |
| **Độ trễ khi lỗi** | Thấp khi đã OPEN (fail-fast ngay) | **Cao**: mỗi lần retry là 1 lần chờ (2s × 2 = 4s) ⇒ request của người dùng bị chậm |
| **Tải lên hệ thống đích** | Giảm mạnh khi OPEN (bảo vệ service đích) | **Tăng** tải (gấp `maxAttempts` lần) — nguy hiểm nếu service đang quá tải |
| **Khi lỗi kéo dài** | Tự động phục hồi qua HALF_OPEN (thử lại vài request) | Retry mãi nhưng không có cơ chế "ngừng gọi" ⇒ không tự bảo vệ |
| **Phù hợp nhất với** | Lỗi **hệ thống sập hẳn**, cần bảo vệ service đích | Lỗi **tạm thời, chớp nháy**, gọi lại là được |

### Kết luận so sánh

- **Transient Failure** → Retry thắng rõ rệt (đúng bản chất "gọi lại là được").
- **System Crash** → Circuit Breaker thắng rõ rệt (không gọi vào service đã chết, tự phục hồi).
- Trong thực tế: **kết hợp cả hai** — Retry xử lý chớp nháy, Circuit Breaker xử lý khi lỗi kéo dài (đúng chuẩn "Resilience4j combo" của Spring Boot).

---

## PHẦN 3 — TRIỂN KHAI GIẢI PHÁP ĐƯỢC CHỌN

**Chốt: Giải pháp 2 — Retry + Backoff** (vì bối cảnh là lỗi *chớp nháy*, gọi lại là thành công).

`order-service/src/main/resources/application.yml`:

```yaml
resilience4j:
  retry:
    instances:
      ghtkClient:
        maxAttempts: 3               # 3 lần gọi (1 lần đầu + 2 lần thử lại)
        waitDuration: 2s             # mỗi lần thử lại cách nhau 2 giây
        enableExponentialBackoff: false
        exponentialBackoffMultiplier: 2   # bật flag trên để dùng backoff tăng dần 2s -> 4s -> 8s
        retryExceptions:             # ★ CHỈ retry lỗi TẠM THỜI
          - java.util.concurrent.TimeoutException
          - java.net.SocketTimeoutException
          - java.net.ConnectException
        ignoreExceptions:            # lỗi nghiệp vụ -> KHÔNG retry
          - com.storex.order.exception.WaybillRejectedException
```

> **Về `maxAttempts`:** Resilience4j đếm **cả lần gọi đầu tiên** ⇒ `maxAttempts: 3` = 1 lần gọi + 2 lần thử lại. Nếu muốn đúng **3 lần thử lại** thì đặt `maxAttempts: 4`.
> **Về `waitDuration` cố định vs exponential:** yêu cầu là "mỗi lần cách nhau 2 giây" ⇒ dùng `waitDuration: 2s` và tắt backoff tăng dần. Khi bật `enableExponentialBackoff: true` với multiplier 2, thời gian chờ sẽ là 2s → 4s → 8s (giảm tải cho service đích nếu lỗi kéo dài).

---

## PHẦN 4 — KẾT QUẢ KIỂM CHỨNG (chạy thật)

Test `GhtkRetryTest` — xem `Ex05_TestEvidence.txt`:

| Kịch bản | Kết quả |
|---|---|
| 2 lần timeout đầu, lần 3 thành công | `WAYBILL-ORD-100`, **3 lần gọi**, **4049ms** (đúng 2 lần chờ × 2s) ✅ |
| Lỗi nghiệp vụ (mã đơn không hợp lệ) | **1 lần gọi** (không retry vô ích) ✅ |
| Timeout liên tục | 3 lần gọi rồi trả **fallback** `GHTP-FAILED` — không ném exception ra ngoài ✅ |

---

## PHẦN 5 — BẪY DỮ LIỆU: IDEMPOTENCY

> **Tình huống:** nếu GHTK **trừ tiền tài khoản mỗi lần gọi API**, việc Retry có rủi ro gì?

### Rủi ro

Giả sử request tạo vận đơn **đã tới GHTK và trừ tiền thành công**, nhưng **response bị mất/timeout trên đường về**. Order-Service tưởng lỗi → **retry** → GHTK nhận được **request thứ 2** cho cùng đơn hàng → **trừ tiền lần nữa**. Kết quả: khách bị **trừ tiền 2-3 lần** cho cùng một đơn.

> Retry biến một lỗi "chỉ mất response" thành lỗi **trừ tiền trùng**.

### Khái niệm Idempotency

**Idempotency (tính lũy đẳng)** = một thao tác có thể gọi **nhiều lần** nhưng **kết quả cuối cùng vẫn như gọi 1 lần**. Nếu API có tính lũy đẳng thì retry là an toàn.

### Cách xử lý cho bối cảnh này

1. **Gửi kèm Idempotency Key** — mỗi yêu cầu tạo vận đơn mang một `Idempotency-Key` (thường là `orderId`). GHTK lưu key này; nếu nhận được key đã xử lý thì **trả lại kết quả cũ** thay vì tạo mới/trừ tiền lần nữa.
2. **Chỉ retry khi lỗi xảy ra TRƯỚC khi hệ thống đích xử lý** — ví dụ `ConnectException` (chưa kết nối được) thì retry an toàn; `TimeoutException` (đã gửi đi, chưa nhận response) thì **phải cẩn thận** vì có thể đã xử lý rồi.
3. **Đối chiếu trước khi tạo lại** — trước khi retry, gọi API tra cứu "vận đơn theo orderId đã tồn tại chưa"; nếu có thì dùng lại.
4. **Chỉ retry các thao tác read-only**; với thao tác ghi/mất tiền, ưu tiên **Idempotency Key + tra cứu trạng thái** thay vì retry mù.

> Tóm lại: **Retry chỉ an toàn khi thao tác có tính Idempotency.** Với API trừ tiền, bắt buộc phải có Idempotency Key (hoặc cơ chế tra cứu trạng thái) trước khi bật retry.
