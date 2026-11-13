# LUỒNG TỰ ĐỘNG CẬP NHẬT CẤU HÌNH L2 CACHE VÀO LMCACHE ENGINE

Tài liệu này mô tả chi tiết kiến trúc, biểu đồ luồng dữ liệu (Data Flow), trình tự thực thi (Sequence Workflow) và cơ chế nội tại khi cập nhật cấu hình **L2 Remote Cache (Redis)** vào hệ thống **LMCache + vLLM**.

---

## 1. Kiến Trúc Phân Tầng Caching (Tiered Caching Architecture)

LMCache hoạt động theo cơ chế bộ nhớ phân cấp (Hierarchical / Tiered Memory):

```mermaid
flowchart TD
    subgraph Client ["Client Layer"]
        UI["Web UI / Frontend (llm-cache-ui)"]
    end

    subgraph Backend ["Spring Boot API Gateway (:8081)"]
        L2Controller["L2CacheConfigController"]
        L2Service["L2CacheConfigService"]
        DB[("PostgreSQL: l2_cache_config")]
        ConfigFile[("lmcache_vllm.yaml")]
    end

    subgraph Engine ["vLLM & LMCache Engine (:8000)"]
        vLLM["vLLM Model Worker Process"]
        L1Cache["Tier 1: Local Host CPU RAM\n(local_cpu: true, 5GB)"]
        GPU["GPU PagedAttention Memory"]
    end

    subgraph Storage ["Tier 2 Remote Storage"]
        Redis[("Redis Server L2\n(remote_url: redis://host:6379)")]
    end

    UI -->|"1. Gửi cấu hình L2 (Host, Port, Pass)"| L2Controller
    L2Controller --> L2Service
    L2Service -->|"2. Validate PING"| Redis
    L2Service -->|"3. Lưu cấu hình"| DB
    L2Service -->|"4. Ghi đè YAML"| ConfigFile
    L2Service -.->|"5. Trigger Reload Container"| vLLM

    ConfigFile -.->|"Nạp cấu hình khởi động"| vLLM
    vLLM <--> GPU
    vLLM <--> L1Cache
    L1Cache <-->|"Offload & Prefetch KV Chunks"| Redis
```

---

## 2. Biểu Đồ Trình Tự Luồng Cập Nhật (Update Sequence Workflow)

Khi người dùng thao tác trên giao diện người dùng, toàn bộ chu trình xử lý diễn ra qua 6 giai đoạn nghiêm ngặt:

```mermaid
sequenceDiagram
    autonumber
    actor User as Quản trị viên (Frontend UI)
    participant API as Spring Boot (L2CacheConfigController)
    participant Svc as L2CacheConfigServiceImpl
    participant Redis as Redis Server (Target L2)
    participant DB as PostgreSQL (l2_cache_config)
    participant FS as File System (lmcache_vllm.yaml)
    participant Docker as vLLM Engine (Docker/Process)

    %% Giai đoạn 1: Test connection
    Note over User, Redis: Giai đoạn 1: Kiểm tra kết nối trước khi lưu (Pre-validation)
    User->>API: POST /api/v1/cache/l2/test {host, port, password}
    API->>Svc: testConnection(request)
    Svc->>Redis: Mở TCP Socket (timeout 3000ms)
    alt Có mật khẩu
        Svc->>Redis: AUTH <password>
        Redis-->>Svc: +OK
    end
    Svc->>Redis: PING
    Redis-->>Svc: +PONG (Đo Latency ms)
    Svc-->>API: RedisConnectionTestResponse (connected=true, latencyMs=3)
    API-->>User: 200 OK: "Kết nối Redis thành công!"

    %% Giai đoạn 2: Lưu và áp dụng
    Note over User, Docker: Giai đoạn 2: Ghi cấu hình & Đồng bộ vào LMCache Engine
    User->>API: POST /api/v1/cache/l2 {enabled: true, host, port, password, chunkSize, ...}
    API->>Svc: saveAndApplyConfig(request)
    
    Svc->>Redis: PING kiểm tra lại lần cuối
    Redis-->>Svc: +PONG

    Svc->>DB: UPDATE / INSERT vào bảng `l2_cache_config` (status: ACTIVE)
    DB-->>Svc: Saved Entity

    Svc->>FS: Render & Ghi đè file `lmcache_vllm.yaml` (UTF-8, Atomic Write)
    FS-->>Svc: File updated

    opt Bật cờ auto-restart (lmcache.auto-restart-vllm=true)
        Svc->>Docker: docker restart <vllm-container-name>
        Docker-->>Svc: Container restarted
    end

    Svc-->>API: Trả về L2CacheConfigDTO (status=ACTIVE, maskedPassword=******)
    API-->>User: 200 OK: "Cấu hình L2 đã được lưu và áp dụng thành công!"

    %% Giai đoạn 3: Runtime KV Cache
    Note over Docker, Redis: Giai đoạn 3: vLLM & LMCache bắt đầu sử dụng L2 Redis
    Docker->>FS: Đọc biến LMCACHE_CONFIG_FILE -> lmcache_vllm.yaml
    Docker->>Redis: Kết nối remote_url và khởi tạo KV Cache Serialization Buffer
```

---

## 3. Chi Tiết Các Bước Kỹ Thuật Trong Luồng

### Bước 1: Tiền Kiểm Tra Kết Nối (Socket Pre-Validation)
- Trước khi lưu vào hệ thống, [`L2CacheConfigServiceImpl`](file:///d:/Hangout/Master/Final/llm-cache/vLLM/src/main/java/main/vllm/service/impl/L2CacheConfigServiceImpl.java) mở một socket TCP trực tiếp tới địa chỉ `host:port` với timeout 3 giây.
- Sử dụng giao thức chuẩn **RESP (Redis Serialization Protocol)**:
  1. Gửi lệnh `AUTH <password>\r\n` (nếu có mật khẩu cấu hình).
  2. Gửi lệnh `PING\r\n`.
  3. Đọc phản hồi `+PONG\r\n` và đo đạc thời gian khứ hồi (Round-Trip Time / Latency ms).
- **Lợi ích:** Tránh tình trạng người dùng nhập sai IP/Port dẫn đến việc vLLM khởi động lại bị crash do không thể kết nối tới L2 remote storage backend.

---

### Bước 2: Lưu Cấu Hình Vào Cơ Sở Dữ Liệu PostgreSQL
- Dữ liệu được lưu trữ lâu dài (persistent) trong bảng `l2_cache_config`:
  - `enabled`: Bật/Tắt L2.
  - `host`, `port`: Địa chỉ Redis server.
  - `password`: Mật khẩu kết nối.
  - `chunk_size`: Kích thước chia nhỏ KV tokens (mặc định 256).
  - `max_local_cpu_size_gb`: Dung lượng RAM dành cho L1 cache (mặc định 5.0 GB).
  - `remote_serde`: Định dạng mã hóa (`naive` hoặc `cachegen`).
  - `status`: Trạng thái vận hành (`ACTIVE`, `DISABLED`, `ERROR`).
  - `updated_at`: Thời điểm cập nhật cuối cùng.

---

### Bước 3: Tự Động Sinh & Ghi Đè File `lmcache_vllm.yaml`
- Hệ thống tự động biên dịch thực thể cấu hình thành file cấu hình YAML chuẩn của LMCache:

```yaml
# ================================================================
# LMCache Configuration File (Auto-generated by Spring Boot)
# ================================================================

# Chunk size (number of tokens per KV chunk)
chunk_size: 256

# Tier 1: Local CPU Cache
local_cpu: true
max_local_cpu_size: 5.0

# Tier 2: Remote Distributed Storage (Redis)
remote_url: "redis://:mypassword@192.168.1.50:6379"
remote_serde: "naive"
```

- Khi người dùng **Tắt L2 Cache** (qua `DELETE /api/v1/cache/l2` hoặc toggle tắt trên UI):
  File YAML sẽ tự động chú thích lại (comment out) dòng `remote_url`:
  ```yaml
  # Tier 2: Remote Distributed Storage (Disabled)
  # remote_url: "redis://localhost:6379"
  # remote_serde: "naive"
  ```
  LMCache lập tức trở về chế độ **L1 CPU Cache độc lập**, không còn phụ thuộc Redis.

---

### Bước 4: Đồng Bộ Khởi Động Lại vLLM Engine (Lifecycle Synchronization)
- Vì LMCache khởi tạo socket kết nối lưu trữ lúc khởi động vLLM worker process:
  - Nếu `lmcache.auto-restart-vllm=true`: Backend gọi lệnh `docker restart <vllmContainerName>` để container tự đọc lại file `lmcache_vllm.yaml` mới.
  - Nếu vLLM chạy dưới dạng tiến trình độc lập (Systemd service hoặc Python command), người dùng có thể kích hoạt reload service thủ công.

---

## 4. Luồng Truy Vấn & Lưu KV Cache Tại Thời Điểm Chạy (Runtime KV Flow)

Khi cấu hình L2 Redis đã hoạt động, mỗi khi người dùng gửi một lượt chat mới (`POST /api/v1/chat`):

```mermaid
flowchart TD
    Prompt[User gửi Prompt] --> Tokenize[vLLM Tokenize Prompt]
    Tokenize --> CheckGPU{KV Cache có sẵn trên GPU?}
    
    CheckGPU -- Hit --> FastInfer[⚡ Suy luận ngay lập tức\nTTFT cực thấp]
    
    CheckGPU -- Miss --> CheckL1{Kiểm tra L1 Host CPU Cache}
    CheckL1 -- Hit --> LoadToGPU[Tải KV Cache từ CPU RAM lên GPU] --> FastInfer
    
    CheckL1 -- Miss --> CheckL2{Kiểm tra L2 Redis Remote Cache}
    CheckL2 -- Hit --> LoadL2ToL1[Tải KV Chunks từ Redis về CPU RAM & GPU] --> FastInfer
    
    CheckL2 -- Miss --> FullCompute[Tính toán Prompt Full Prefill trên GPU]
    FullCompute --> SaveL1[Lưu KV Cache mới vào L1 Host CPU RAM]
    SaveL1 --> SaveL2[Bất đồng bộ offload KV Chunks xuống L2 Redis]
    SaveL2 --> ReturnResponse[Trả về kết quả câu trả lời]
```

---

## 5. Cơ Chế Xử Lý Lỗi & Rollback (Fault Tolerance)

| Tình Huống Lỗi | Cơ Chế Xử Lý Của Hệ Thống |
| :--- | :--- |
| **Sai IP/Port hoặc Redis sập trước khi lưu** | Backend chặn ngay ở bước kiểm tra `testConnection`, trả về lỗi HTTP 400 kèm thông báo chi tiết, **không ghi đè file YAML**, hệ thống vLLM tiếp tục chạy bình thường với cấu hình cũ. |
| **Redis bị ngắt kết nối đột ngột lúc runtime** | LMCache có cơ chế fallback: nếu không thể đọc/ghi L2 Redis, engine tự động chuyển sang tính toán cục bộ (Local Compute) trên GPU/L1 mà không làm gián đoạn câu trả lời chat của người dùng. |
| **Người dùng đổi mật khẩu hoặc tắt L2** | Gọi `DELETE /api/v1/cache/l2`: Hệ thống xóa `remote_url` trong `lmcache_vllm.yaml` và reload engine, chuyển hoàn toàn về Local Tier an toàn. |
