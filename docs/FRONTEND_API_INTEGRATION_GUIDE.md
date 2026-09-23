# HƯỚNG DẪN TÍCH HỢP FRONTEND API (LMCACHE + VLLM ENGINE)

Tài liệu này hướng dẫn chi tiết cho đội ngũ **Frontend (React, Vue, Next.js, TypeScript)** cách tích hợp hệ thống API Backend tối ưu hóa bởi **LMCache + vLLM Engine**, hiển thị các chỉ số Prefix Caching, KV Cache GPU, truy vấn lịch sử từ PostgreSQL Database và xử lý Server-Sent Events (SSE) Streaming theo thời gian thực.

---

## 1. Kiến Trúc Hệ Thống (LMCache + vLLM & PostgreSQL)

- **LMCache & vLLM Engine**: Tăng tốc độ phản hồi của Large Language Model (LLM) bằng kỹ thuật **Prefix Caching** và tái sử dụng **KV Cache**. Giúp giảm thời gian tính toán prompt (Time To First Token - TTFT) và tiết kiệm tài nguyên GPU.
- **Spring Boot Backend**: Xử lý điều hướng API, tính toán chỉ số cache, đồng thời **tự động lưu tất cả câu hỏi và phản hồi vào PostgreSQL Database** (`chat_history`).
- **Base URL API**: `http://localhost:8080/api/v1` (Hoặc cấu hình theo biến môi trường `VITE_API_BASE_URL` / `NEXT_PUBLIC_API_URL`).

---

## 2. Cấu Trúc Phản Hồi Chuẩn (Standard Response Format)

Tất cả các API REST trong hệ thống đều trả về giao thức `ResponseObject<T>` chuẩn:

```json
{
  "status": "SUCCESS",   // hoặc "ERROR"
  "message": "Thông báo từ server",
  "data": { ... },       // Dữ liệu chính (ChatResponse, CacheResponseDTO, SystemStatusDTO, v.v.)
  "timestamp": "2026-09-23T22:30:00"
}
```

### Mã Trạng Thái HTTP (HTTP Status Codes):
- `200 OK`: Thành công, kiểm tra `status === "SUCCESS"`.
- `400 Bad Request`: Đầu vào không hợp lệ (Ví dụ: `prompt` bị trống).
- `500 Internal Server Error`: Lỗi hệ thống backend hoặc vLLM / LMCache engine.

---

## 3. Danh Sách 4 Nhóm API Endpoints Chính

---

### 3.1. API Chat & Lịch Sử (`/api/v1/chat`)

#### A. Chat Đồng Bộ (Full Chat Response + Auto DB Save)
- **Endpoint:** `POST /api/v1/chat`
- **Content-Type:** `application/json`
- **Request Body:**
  ```json
  {
    "prompt": "Giải thích kỹ thuật Prefix Caching trong LMCache và vLLM?",
    "model": "Qwen/Qwen2.5-1.5B-Instruct"
  }
  ```
- **Response Body (`ResponseObject<ChatResponse>`):**
  ```json
  {
    "status": "SUCCESS",
    "message": "Response generated successfully",
    "data": {
      "response": "Prefix Caching là kỹ thuật lưu lại trạng thái KV Cache của các đoạn prompt trùng lặp...",
      "model": "Qwen/Qwen2.5-1.5B-Instruct",
      "executionTimeMs": 320,
      "usage": {
        "promptTokens": 45,
        "completionTokens": 120,
        "totalTokens": 165,
        "cachedTokens": 35,
        "cacheHitRatioPercentage": 77.78
      },
      "vllmMetrics": {
        "prefixCacheHitRatio": 82.5,
        "kvCacheUsagePerc": 45.2,
        "e2eLatencyAvgSeconds": 0.32
      }
    },
    "timestamp": "2026-09-23T22:30:00"
  }
  ```

#### B. Chat Real-time Streaming (SSE Stream + Auto DB Save khi kết thúc)
- **Endpoint:** `POST /api/v1/chat/stream`
- **Headers:** 
  - `Content-Type: application/json`
  - `Accept: text/event-stream`
- **Request Body:**
  ```json
  {
    "prompt": "Viết đoạn văn ngắn về lợi ích của LMCache",
    "model": "Qwen/Qwen2.5-1.5B-Instruct"
  }
  ```
- **Phản hồi SSE (Streaming Chunk):**
  ```text
  data:{"status":"SUCCESS","message":null,"data":{"response":"LMCache ","model":null,"executionTimeMs":null,"usage":null,"vllmMetrics":null},"timestamp":"..."}

  data:{"status":"SUCCESS","message":null,"data":{"response":"giúp giảm ","model":null,"executionTimeMs":null,"usage":null,"vllmMetrics":null},"timestamp":"..."}

  data:{"status":"SUCCESS","message":null,"data":{"response":"độ trễ...","model":null,"executionTimeMs":null,"usage":null,"vllmMetrics":null},"timestamp":"..."}
  ```
  *(Sau khi stream xong toàn bộ câu trả lời, Backend sẽ tự động lưu lại toàn bộ cặp câu hỏi - câu trả lời vào PostgreSQL database).*

#### C. Truy Vấn Lịch Sử Chat Tóm Tắt Trong PostgreSQL
- **Endpoint:** `GET /api/v1/chat/history?limit=50`
- **Response Body (`ResponseObject<List<ChatHistory>>`):**
  ```json
  {
    "status": "SUCCESS",
    "message": "Chat history fetched successfully",
    "data": [
      {
        "id": 102,
        "prompt": "Giải thích kỹ thuật Prefix Caching trong LMCache và vLLM?",
        "response": "Prefix Caching là kỹ thuật lưu lại trạng thái KV Cache...",
        "model": "Qwen/Qwen2.5-1.5B-Instruct",
        "executionTimeMs": 320,
        "promptTokens": 45,
        "completionTokens": 120,
        "totalTokens": 165,
        "cachedTokens": 35,
        "cacheHitRatioPercentage": 77.78,
        "createdAt": "2026-09-23T22:30:00"
      }
    ]
  }
  ```

---

### 3.2. API vLLM Engine Metrics (`/api/v1/metrics`)

Dùng để theo dõi chỉ số sức khỏe của vLLM Prometheus metrics theo thời gian thực.

- **Endpoint:** `GET /api/v1/metrics`
- **Response Body (`ResponseObject<VllmMetricsDTO>`):**
  ```json
  {
    "status": "SUCCESS",
    "message": "Metrics fetched successfully",
    "data": {
      "prefixCacheQueriesTotal": 1450.0,
      "prefixCacheHitsTotal": 1180.0,
      "prefixCacheHitRatio": 81.38,
      "promptTokensLocalCompute": 24000.0,
      "promptTokensLocalCacheHit": 105000.0,
      "kvCacheUsagePerc": 58.4,
      "numRequestsRunning": 1.0,
      "numRequestsWaiting": 0.0,
      "promptTokensTotal": 129000.0,
      "generationTokensTotal": 45000.0,
      "e2eLatencyAvgSeconds": 0.28,
      "timeToFirstTokenAvgSeconds": 0.06
    }
  }
  ```

---

### 3.3. API Thống Kê LMCache & Prefix Cache (`/api/v1/cache`)

Hiển thị chỉ số chuyên sâu về **LMCache**, tỉ lệ trúng cache (Cache Hit Ratio) và tổng số Token tiết kiệm được.

- **Endpoint:** `GET /api/v1/cache`
- **Response Body (`ResponseObject<CacheResponseDTO>`):**
  ```json
  {
    "status": "SUCCESS",
    "message": "Cache statistics fetched successfully",
    "data": {
      "prefixCacheQueriesTotal": 1450.0,
      "prefixCacheHitsTotal": 1180.0,
      "prefixCacheHitRatio": 81.38,
      "promptTokensLocalCompute": 24000.0,
      "promptTokensLocalCacheHit": 105000.0,
      "totalCachedTokensSaved": 105000,
      "kvCacheUsagePerc": 58.4,
      "cacheStatus": "ACTIVE"
    }
  }
  ```

- **API Reset Cache Status (Xóa/Làm mới trạng thái cache view):**
  - **Endpoint:** `DELETE /api/v1/cache`

---

### 3.4. API Trạng Thái Hệ Thống & Connection (`/api/v1/system`)

Kiểm tra trạng thái Backend, kết nối Database PostgreSQL, thông tin Model vLLM/LMCache và RAM/Uptime.

- **Endpoint:** `GET /api/v1/system`
- **Response Body (`ResponseObject<SystemStatusDTO>`):**
  ```json
  {
    "status": "SUCCESS",
    "message": "System status retrieved successfully",
    "data": {
      "applicationName": "vLLM Cache Backend",
      "version": "1.0.0",
      "status": "UP",
      "llmModelName": "Qwen/Qwen2.5-1.5B-Instruct",
      "llmModelUrl": "http://localhost:8000",
      "databaseStatus": "CONNECTED",
      "totalChatsProcessed": 102,
      "uptimeSeconds": 1420,
      "freeMemoryMb": 450,
      "totalMemoryMb": 2048
    }
  }
  ```

---

## 4. Khai Báo TypeScript Interfaces (`types/api.ts`)

Tạo file `src/types/api.ts` trong dự án Frontend:

```typescript
// Cấu trúc Response chuẩn
export interface ResponseObject<T> {
  status: 'SUCCESS' | 'ERROR';
  message: string;
  data: T;
  timestamp: string;
}

// Thông tin Token & LMCache Hit Ratio của mỗi lượt Chat
export interface UsageInfo {
  promptTokens: number;
  completionTokens: number;
  totalTokens: number;
  cachedTokens: number;
  cacheHitRatioPercentage: number;
}

// Chỉ số Prometheus từ vLLM Engine
export interface VllmMetricsDTO {
  prefixCacheQueriesTotal: number;
  prefixCacheHitsTotal: number;
  prefixCacheHitRatio: number;
  promptTokensLocalCompute: number;
  promptTokensLocalCacheHit: number;
  kvCacheUsagePerc: number;
  numRequestsRunning: number;
  numRequestsWaiting: number;
  promptTokensTotal: number;
  generationTokensTotal: number;
  e2eLatencyAvgSeconds: number;
  timeToFirstTokenAvgSeconds: number;
}

// Chi tiết lượt Chat trả về
export interface ChatResponse {
  response: string;
  model?: string;
  executionTimeMs?: number;
  usage?: UsageInfo;
  vllmMetrics?: VllmMetricsDTO;
}

// Body Request gửi Chat
export interface ChatRequest {
  prompt: string;
  model?: string;
}

// Entity Lịch sử lưu trong PostgreSQL
export interface ChatHistory {
  id: number;
  prompt: string;
  response: string;
  model: string;
  executionTimeMs: number;
  promptTokens: number;
  completionTokens: number;
  totalTokens: number;
  cachedTokens: number;
  cacheHitRatioPercentage: number;
  createdAt: string;
}

// DTO Thống Kê LMCache
export interface CacheResponseDTO {
  prefixCacheQueriesTotal: number;
  prefixCacheHitsTotal: number;
  prefixCacheHitRatio: number;
  promptTokensLocalCompute: number;
  promptTokensLocalCacheHit: number;
  totalCachedTokensSaved: number;
  kvCacheUsagePerc: number;
  cacheStatus: string;
}

// DTO Trạng Thái Hệ Thống
export interface SystemStatusDTO {
  applicationName: string;
  version: string;
  status: string;
  llmModelName: string;
  llmModelUrl: string;
  databaseStatus: string;
  totalChatsProcessed: number;
  uptimeSeconds: number;
  freeMemoryMb: number;
  totalMemoryMb: number;
}
```

---

## 5. Service Gọi API (`services/apiService.ts`)

Tạo file `src/services/apiService.ts`:

```typescript
import {
  ChatRequest,
  ChatResponse,
  ChatHistory,
  CacheResponseDTO,
  SystemStatusDTO,
  VllmMetricsDTO,
  ResponseObject,
} from '../types/api';

const API_BASE_URL = process.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1';

// 1. Call API Chat Đồng Bộ
export async function sendChatRequest(request: ChatRequest): Promise<ChatResponse> {
  const res = await fetch(`${API_BASE_URL}/chat`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  });

  if (!res.ok) {
    const errorData: ResponseObject<null> = await res.json();
    throw new Error(errorData.message || 'Lỗi khi gửi yêu cầu Chat');
  }

  const result: ResponseObject<ChatResponse> = await res.json();
  if (result.status === 'ERROR') throw new Error(result.message);
  return result.data;
}

// 2. Call API Chat Real-time Streaming (SSE Stream)
export async function streamChatRequest(
  request: ChatRequest,
  onChunk: (chunkText: string) => void,
  onComplete?: () => void,
  onError?: (err: Error) => void
) {
  try {
    const response = await fetch(`${API_BASE_URL}/chat/stream`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'text/event-stream',
      },
      body: JSON.stringify(request),
    });

    if (!response.ok || !response.body) {
      throw new Error(`Stream thất bại với HTTP status: ${response.status}`);
    }

    const reader = response.body.getReader();
    const decoder = new TextDecoder('utf-8');
    let buffer = '';

    while (true) {
      const { value, done } = await reader.read();
      if (done) break;

      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split('\n');
      buffer = lines.pop() || '';

      for (const line of lines) {
        const trimmed = line.trim();
        if (trimmed.startsWith('data:')) {
          const jsonStr = trimmed.replace('data:', '').trim();
          if (!jsonStr) continue;

          try {
            const parsed: ResponseObject<ChatResponse> = JSON.parse(jsonStr);
            if (parsed.status === 'SUCCESS' && parsed.data?.response) {
              onChunk(parsed.data.response);
            }
          } catch (e) {
            console.error('Lỗi parse JSON từ SSE chunk:', e);
          }
        }
      }
    }

    if (onComplete) onComplete();
  } catch (error: any) {
    if (onError) onError(error);
  }
}

// 3. Lấy Lịch Sử Chat từ PostgreSQL
export async function fetchChatHistory(limit: number = 50): Promise<ChatHistory[]> {
  const res = await fetch(`${API_BASE_URL}/chat/history?limit=${limit}`);
  if (!res.ok) throw new Error('Không thể tải lịch sử chat');
  const result: ResponseObject<ChatHistory[]> = await res.json();
  return result.data;
}

// 4. Lấy vLLM Prometheus Metrics
export async function fetchVllmMetrics(): Promise<VllmMetricsDTO> {
  const res = await fetch(`${API_BASE_URL}/metrics`);
  if (!res.ok) throw new Error('Không thể tải chỉ số metrics vLLM');
  const result: ResponseObject<VllmMetricsDTO> = await res.json();
  return result.data;
}

// 5. Lấy Thống Kê LMCache Stats
export async function fetchCacheStats(): Promise<CacheResponseDTO> {
  const res = await fetch(`${API_BASE_URL}/cache`);
  if (!res.ok) throw new Error('Không thể tải thống kê LMCache');
  const result: ResponseObject<CacheResponseDTO> = await res.json();
  return result.data;
}

// 6. Lấy Trạng Thái Hệ Thống System Status
export async function fetchSystemStatus(): Promise<SystemStatusDTO> {
  const res = await fetch(`${API_BASE_URL}/system`);
  if (!res.ok) throw new Error('Không thể tải trạng thái hệ thống');
  const result: ResponseObject<SystemStatusDTO> = await res.json();
  return result.data;
}
```

---

## 6. Ví Dụ React Component

### 6.1. Component Dashboard Giám Sát LMCache & vLLM (`components/LmCacheMetricsDashboard.tsx`)

```tsx
import React, { useEffect, useState } from 'react';
import { fetchCacheStats } from '../services/apiService';
import { CacheResponseDTO } from '../types/api';

export const LmCacheMetricsDashboard: React.FC = () => {
  const [cacheStats, setCacheStats] = useState<CacheResponseDTO | null>(null);

  useEffect(() => {
    const loadStats = async () => {
      try {
        const data = await fetchCacheStats();
        setCacheStats(data);
      } catch (err) {
        console.error('Lỗi tải LMCache stats:', err);
      }
    };

    loadStats();
    const interval = setInterval(loadStats, 4000); // Auto refresh mỗi 4 giây
    return () => clearInterval(interval);
  }, []);

  if (!cacheStats) return <div>Đang kết nối LMCache Engine...</div>;

  return (
    <div style={{ padding: '20px', fontFamily: 'Segoe UI, sans-serif' }}>
      <h3>🚀 LMCache & vLLM Performance Dashboard</h3>
      
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: '16px' }}>
        <div style={cardStyle}>
          <h4>⚡ Prefix Cache Hit Ratio</h4>
          <p style={{ ...valueStyle, color: '#28a745' }}>
            {cacheStats.prefixCacheHitRatio?.toFixed(1)}%
          </p>
          <small>Tỉ lệ trúng KV Cache trùng lặp</small>
        </div>

        <div style={cardStyle}>
          <h4>💾 GPU KV Cache Usage</h4>
          <p style={{ ...valueStyle, color: '#007bff' }}>
            {cacheStats.kvCacheUsagePerc?.toFixed(1)}%
          </p>
          <small>Dung lượng vRAM GPU được dùng</small>
        </div>

        <div style={cardStyle}>
          <h4>🔥 Tokens Local Cache Hit</h4>
          <p style={valueStyle}>
            {cacheStats.promptTokensLocalCacheHit?.toLocaleString()}
          </p>
          <small>Token được tải từ LMCache</small>
        </div>

        <div style={cardStyle}>
          <h4>📦 Total Cached Saved</h4>
          <p style={{ ...valueStyle, color: '#6f42c1' }}>
            {cacheStats.totalCachedTokensSaved?.toLocaleString() || 0}
          </p>
          <small>Tổng Token tiết kiệm được</small>
        </div>
      </div>
    </div>
  );
};

const cardStyle: React.CSSProperties = {
  background: '#ffffff',
  border: '1px solid #e9ecef',
  padding: '16px',
  borderRadius: '12px',
  boxShadow: '0 4px 6px rgba(0, 0, 0, 0.05)',
};

const valueStyle: React.CSSProperties = {
  fontSize: '28px',
  fontWeight: 'bold',
  margin: '8px 0',
};
```

---

### 6.2. Component Chat Real-Time Streaming (`components/ChatBox.tsx`)

```tsx
import React, { useState, useRef, useEffect } from 'react';
import { streamChatRequest } from '../services/apiService';

interface Message {
  sender: 'user' | 'bot';
  text: string;
}

export const ChatBox: React.FC = () => {
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState('');
  const [isStreaming, setIsStreaming] = useState(false);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const handleSend = async () => {
    if (!input.trim() || isStreaming) return;

    const userPrompt = input;
    setInput('');

    setMessages((prev) => [
      ...prev,
      { sender: 'user', text: userPrompt },
      { sender: 'bot', text: '' },
    ]);
    setIsStreaming(true);

    await streamChatRequest(
      { prompt: userPrompt },
      (chunkText) => {
        setMessages((prev) => {
          const updated = [...prev];
          const lastIndex = updated.length - 1;
          if (lastIndex >= 0 && updated[lastIndex].sender === 'bot') {
            updated[lastIndex] = {
              ...updated[lastIndex],
              text: updated[lastIndex].text + chunkText,
            };
          }
          return updated;
        });
      },
      () => {
        setIsStreaming(false);
      },
      (err) => {
        console.error('Lỗi khi streaming:', err);
        setIsStreaming(false);
      }
    );
  };

  return (
    <div style={{ width: '650px', margin: '0 auto', fontFamily: 'sans-serif' }}>
      <h2>💬 LMCache AI Assistant</h2>
      
      <div style={{ height: '420px', overflowY: 'auto', border: '1px solid #ddd', padding: '16px', borderRadius: '12px', background: '#fafafa' }}>
        {messages.map((msg, idx) => (
          <div key={idx} style={{ textAlign: msg.sender === 'user' ? 'right' : 'left', margin: '10px 0' }}>
            <span style={{
              display: 'inline-block',
              padding: '10px 14px',
              borderRadius: '18px',
              maxWidth: '80%',
              background: msg.sender === 'user' ? '#007bff' : '#ffffff',
              color: msg.sender === 'user' ? '#fff' : '#212529',
              boxShadow: '0 2px 4px rgba(0,0,0,0.05)',
              border: msg.sender === 'bot' ? '1px solid #eee' : 'none'
            }}>
              {msg.text || (isStreaming && idx === messages.length - 1 ? '⚡ Đang suy nghĩ (LMCache hit...)...' : '')}
            </span>
          </div>
        ))}
        <div ref={messagesEndRef} />
      </div>

      <div style={{ display: 'flex', gap: '10px', marginTop: '14px' }}>
        <input
          type="text"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && handleSend()}
          placeholder="Nhập câu hỏi cho LLM..."
          disabled={isStreaming}
          style={{ flex: 1, padding: '12px', borderRadius: '8px', border: '1px solid #ccc' }}
        />
        <button onClick={handleSend} disabled={isStreaming} style={{ padding: '12px 24px', borderRadius: '8px', background: '#007bff', color: '#fff', border: 'none', cursor: 'pointer' }}>
          {isStreaming ? 'Đang tạo...' : 'Gửi'}
        </button>
      </div>
    </div>
  );
};
```

---

## 7. Quy Tắc UI/UX Tối Ưu Trải Nghiệm LMCache
1. **Auto-Scroll Mượt**: Đảm bảo tin nhắn tự cuộn theo dòng dữ liệu stream SSE.
2. **Khóa Input Khi Streaming**: Ngăn chặn gửi nhiều prompt trùng lặp liên tục trước khi stream đóng.
3. **Hiển Thị Tỉ Lệ Hit Cache**: Khi chat xong, có thể hiển thị nhẹ badge tỉ lệ hit cache (`UsageInfo.cacheHitRatioPercentage`) ở cuối câu trả lời để người dùng biết thời gian phản hồi được tăng tốc nhờ **LMCache**.
