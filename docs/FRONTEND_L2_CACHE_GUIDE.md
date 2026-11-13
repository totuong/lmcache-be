# HƯỚNG DẪN TÍCH HỢP FRONTEND - QUẢN LÝ CẤU HÌNH L2 CACHE (REDIS)

Tài liệu này hướng dẫn chi tiết cho lập trình viên **Frontend (React, Next.js, TypeScript)** cách tích hợp giao diện cấu hình và quản trị **L2 Remote Cache (Redis Backend)** cho hệ thống **LMCache + vLLM**.

---

## 1. Tổng Quan API Endpoints

| Method | Endpoint | Mục Đích |
| :--- | :--- | :--- |
| `GET` | `/api/v1/cache/l2` | Lấy cấu hình L2 hiện tại (Trạng thái, host, port, remote URL, status) |
| `POST` | `/api/v1/cache/l2/test` | Kiểm tra kết nối Redis (PING thử, đo độ trễ `latencyMs`) trước khi lưu |
| `POST` | `/api/v1/cache/l2` | Lưu thông tin, tự động ghi đè file `lmcache_vllm.yaml` và reload engine |
| `DELETE` | `/api/v1/cache/l2` | Tắt L2 Cache (chuyển về L1 CPU Cache nội bộ) |
| `GET` | `/api/v1/cache/l2/yaml` | Xem trước nội dung raw file `lmcache_vllm.yaml` |

---

## 2. Khai Báo TypeScript Types (`types/l2Cache.ts`)

Tạo file `src/types/l2Cache.ts`:

```typescript
// Cấu trúc Response chuẩn của Backend
export interface ApiResponse<T> {
  status: 'SUCCESS' | 'ERROR';
  message: string;
  data: T;
  timestamp: string;
}

// DTO Cấu hình L2 Cache
export interface L2CacheConfigDTO {
  id?: number;
  enabled: boolean;
  storageType?: string;          // Mặc định: "REDIS"
  host: string;                  // Ví dụ: "localhost" hoặc IP Redis
  port: number;                  // Mặc định: 6379
  password?: string;             // Password người dùng nhập mới
  hasPassword?: boolean;         // true nếu DB đã có lưu password
  maskedPassword?: string;       // "******"
  chunkSize?: number;            // Tokens/chunk (Mặc định: 256)
  maxLocalCpuSizeGb?: number;    // Dung lượng L1 CPU (Mặc định: 5.0 GB)
  remoteSerde?: 'naive' | 'cachegen'; // Kiểu tuần tự hóa
  status?: 'ACTIVE' | 'DISABLED' | 'ERROR';
  lastErrorMessage?: string;
  remoteUrl?: string;            // Ví dụ: "redis://localhost:6379"
  yamlContent?: string;          // Nội dung file lmcache_vllm.yaml sinh ra
  updatedAt?: string;
}

// Request & Response kiểm tra kết nối Redis
export interface RedisConnectionTestRequest {
  host: string;
  port: number;
  password?: string;
  timeoutMs?: number;
}

export interface RedisConnectionTestResponse {
  connected: boolean;
  message: string;
  latencyMs: number;
  endpoint: string;
}
```

---

## 3. Service Gọi API (`services/l2CacheService.ts`)

Tạo file `src/services/l2CacheService.ts`:

```typescript
import {
  ApiResponse,
  L2CacheConfigDTO,
  RedisConnectionTestRequest,
  RedisConnectionTestResponse,
} from '../types/l2Cache';

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8081/api/v1';

export const l2CacheService = {
  // 1. Lấy cấu hình L2 hiện tại
  async getConfig(): Promise<L2CacheConfigDTO> {
    const res = await fetch(`${API_BASE_URL}/cache/l2`);
    const json: ApiResponse<L2CacheConfigDTO> = await res.json();
    if (json.status !== 'SUCCESS') throw new Error(json.message);
    return json.data;
  },

  // 2. Kiểm tra kết nối Redis (Test Connection)
  async testConnection(data: RedisConnectionTestRequest): Promise<RedisConnectionTestResponse> {
    const res = await fetch(`${API_BASE_URL}/cache/l2/test`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data),
    });
    const json: ApiResponse<RedisConnectionTestResponse> = await res.json();
    if (json.status !== 'SUCCESS') {
      throw new Error(json.message || 'Kiểm tra kết nối thất bại');
    }
    return json.data;
  },

  // 3. Lưu và áp dụng cấu hình L2 (Tự động cập nhật YAML & Reload)
  async saveConfig(config: Partial<L2CacheConfigDTO>): Promise<L2CacheConfigDTO> {
    const res = await fetch(`${API_BASE_URL}/cache/l2`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(config),
    });
    const json: ApiResponse<L2CacheConfigDTO> = await res.json();
    if (json.status !== 'SUCCESS') {
      throw new Error(json.message || 'Lỗi khi lưu cấu hình L2');
    }
    return json.data;
  },

  // 4. Tắt L2 Cache (chuyển về L1)
  async disableL2(): Promise<L2CacheConfigDTO> {
    const res = await fetch(`${API_BASE_URL}/cache/l2`, {
      method: 'DELETE',
    });
    const json: ApiResponse<L2CacheConfigDTO> = await res.json();
    if (json.status !== 'SUCCESS') throw new Error(json.message);
    return json.data;
  },

  // 5. Xem trước file YAML cấu hình
  async getYamlPreview(): Promise<string> {
    const res = await fetch(`${API_BASE_URL}/cache/l2/yaml`);
    const json: ApiResponse<string> = await res.json();
    if (json.status !== 'SUCCESS') throw new Error(json.message);
    return json.data;
  },
};
```

---

## 4. Component Giao Diện Mẫu Hoàn Chỉnh (`components/L2CacheConfigModal.tsx`)

Dưới đây là Component React mẫu với giao diện hiện đại, đầy đủ trạng thái Loading, Test kết nối, xem trước YAML và bật/tắt L2 Cache:

```tsx
import React, { useState, useEffect } from 'react';
import { l2CacheService } from '../services/l2CacheService';
import { L2CacheConfigDTO, RedisConnectionTestResponse } from '../types/l2Cache';

interface Props {
  isOpen: boolean;
  onClose: () => void;
  onConfigUpdated?: (config: L2CacheConfigDTO) => void;
}

export const L2CacheConfigModal: React.FC<Props> = ({ isOpen, onClose, onConfigUpdated }) => {
  const [loading, setLoading] = useState<boolean>(false);
  const [testing, setTesting] = useState<boolean>(false);
  const [saving, setSaving] = useState<boolean>(false);
  const [testResult, setTestResult] = useState<RedisConnectionTestResponse | null>(null);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);
  const [showYaml, setShowYaml] = useState<boolean>(false);

  // Form State
  const [enabled, setEnabled] = useState<boolean>(false);
  const [host, setHost] = useState<string>('localhost');
  const [port, setPort] = useState<number>(6379);
  const [password, setPassword] = useState<string>('');
  const [hasPassword, setHasPassword] = useState<boolean>(false);
  const [chunkSize, setChunkSize] = useState<number>(256);
  const [maxCpuSize, setMaxCpuSize] = useState<number>(5.0);
  const [remoteSerde, setRemoteSerde] = useState<'naive' | 'cachegen'>('naive');
  const [yamlContent, setYamlContent] = useState<string>('');
  const [status, setStatus] = useState<string>('DISABLED');

  // Load cấu hình hiện tại khi mở modal
  useEffect(() => {
    if (isOpen) {
      loadCurrentConfig();
    }
  }, [isOpen]);

  const loadCurrentConfig = async () => {
    try {
      setLoading(true);
      setErrorMsg(null);
      const data = await l2CacheService.getConfig();
      setEnabled(data.enabled);
      setHost(data.host || 'localhost');
      setPort(data.port || 6379);
      setHasPassword(!!data.hasPassword);
      setPassword(''); // Mật khẩu được ẩn, chỉ nhập nếu muốn đổi
      setChunkSize(data.chunkSize || 256);
      setMaxCpuSize(data.maxLocalCpuSizeGb || 5.0);
      setRemoteSerde(data.remoteSerde || 'naive');
      setStatus(data.status || 'DISABLED');
      setYamlContent(data.yamlContent || '');
    } catch (err: any) {
      setErrorMsg(err.message || 'Không thể tải cấu hình L2');
    } finally {
      setLoading(false);
    }
  };

  // 1. Kiểm tra kết nối Redis
  const handleTestConnection = async () => {
    try {
      setTesting(true);
      setTestResult(null);
      setErrorMsg(null);
      setSuccessMsg(null);

      const result = await l2CacheService.testConnection({
        host,
        port: Number(port),
        password: password || undefined,
      });

      setTestResult(result);
    } catch (err: any) {
      setTestResult({
        connected: false,
        message: err.message || 'Kết nối thất bại',
        latencyMs: 0,
        endpoint: `${host}:${port}`,
      });
    } finally {
      setTesting(false);
    }
  };

  // 2. Lưu & Áp Dụng Cấu Hình
  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      setSaving(true);
      setErrorMsg(null);
      setSuccessMsg(null);

      const updated = await l2CacheService.saveConfig({
        enabled,
        host,
        port: Number(port),
        password: password || (hasPassword ? '******' : undefined),
        chunkSize: Number(chunkSize),
        maxLocalCpuSizeGb: Number(maxCpuSize),
        remoteSerde,
      });

      setSuccessMsg('Cấu hình L2 Cache đã được áp dụng và đồng bộ vào LMCache!');
      setStatus(updated.status || 'ACTIVE');
      setYamlContent(updated.yamlContent || '');
      if (onConfigUpdated) onConfigUpdated(updated);
    } catch (err: any) {
      setErrorMsg(err.message || 'Lỗi khi lưu cấu hình');
    } finally {
      setSaving(false);
    }
  };

  if (!isOpen) return null;

  return (
    <div className="modal-backdrop">
      <div className="modal-content">
        {/* Header */}
        <div className="modal-header">
          <div>
            <h2>⚙️ Cấu Hình LMCache L2 Remote Cache</h2>
            <p className="subtitle">Lưu trữ KV Cache phân tán trên Redis để chia sẻ cache giữa các lượt chat & instance</p>
          </div>
          <button className="close-btn" onClick={onClose}>✕</button>
        </div>

        {loading ? (
          <div className="loading-state">Đang tải cấu hình hiện tại...</div>
        ) : (
          <form onSubmit={handleSave} className="modal-body">
            {/* Alert Messages */}
            {errorMsg && <div className="alert alert-error">❌ {errorMsg}</div>}
            {successMsg && <div className="alert alert-success">✅ {successMsg}</div>}

            {/* Trạng thái hiện tại */}
            <div className="status-row">
              <span className="label">Trạng thái L2 Cache:</span>
              <span className={`status-badge status-${status.toLowerCase()}`}>
                ● {status}
              </span>
            </div>

            {/* Switch Toggle Enable */}
            <div className="toggle-row">
              <label className="switch">
                <input
                  type="checkbox"
                  checked={enabled}
                  onChange={(e) => setEnabled(e.target.checked)}
                />
                <span className="slider round"></span>
              </label>
              <span className="toggle-label">
                <strong>Kích hoạt L2 Remote Cache (Redis Backend)</strong>
                <small>Khi bật, KV Cache sẽ tự động offload xuống Redis khi L1 RAM đầy</small>
              </span>
            </div>

            {/* Form Fields */}
            <div className={`form-fields ${!enabled ? 'fields-disabled' : ''}`}>
              <div className="form-group-row">
                <div className="form-group flex-2">
                  <label>Redis Host / IP:</label>
                  <input
                    type="text"
                    required={enabled}
                    value={host}
                    onChange={(e) => setHost(e.target.value)}
                    placeholder="localhost hoặc 192.168.1.100"
                  />
                </div>
                <div className="form-group flex-1">
                  <label>Port:</label>
                  <input
                    type="number"
                    required={enabled}
                    value={port}
                    onChange={(e) => setPort(Number(e.target.value))}
                    placeholder="6379"
                  />
                </div>
              </div>

              <div className="form-group">
                <label>
                  Redis Password:
                  {hasPassword && <span className="tag-saved"> (Đã có mật khẩu được lưu)</span>}
                </label>
                <input
                  type="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder={hasPassword ? 'Để trống nếu giữ nguyên mật khẩu cũ' : 'Không có mật khẩu (tùy chọn)'}
                />
              </div>

              <div className="form-group-row">
                <div className="form-group flex-1">
                  <label>Chunk Size (Tokens):</label>
                  <input
                    type="number"
                    value={chunkSize}
                    onChange={(e) => setChunkSize(Number(e.target.value))}
                  />
                  <small>Kích thước block chia nhỏ KV cache (thường là 256)</small>
                </div>
                <div className="form-group flex-1">
                  <label>L1 CPU RAM Size (GB):</label>
                  <input
                    type="number"
                    step="0.5"
                    value={maxCpuSize}
                    onChange={(e) => setMaxCpuSize(Number(e.target.value))}
                  />
                  <small>Dung lượng RAM tối đa cho L1 Cache trước khi đẩy xuống L2</small>
                </div>
              </div>

              <div className="form-group">
                <label>Serialization Format (remote_serde):</label>
                <select
                  value={remoteSerde}
                  onChange={(e) => setRemoteSerde(e.target.value as 'naive' | 'cachegen')}
                >
                  <option value="naive">naive (Mặc định - Tốc độ cao, không nén)</option>
                  <option value="cachegen">cachegen (Nén KV cache - Tiết kiệm băng thông mạng)</option>
                </select>
              </div>

              {/* Test Connection Button & Result */}
              <div className="test-connection-section">
                <button
                  type="button"
                  className="btn-test"
                  disabled={testing || !host}
                  onClick={handleTestConnection}
                >
                  {testing ? '⏳ Đang kiểm tra kết nối...' : '🔍 Kiểm tra kết nối Redis'}
                </button>

                {testResult && (
                  <div className={`test-badge ${testResult.connected ? 'test-pass' : 'test-fail'}`}>
                    {testResult.connected ? (
                      <>
                        <span>✅ Kết nối thành công!</span>
                        <span className="latency">Ping: {testResult.latencyMs}ms</span>
                      </>
                    ) : (
                      <span>❌ {testResult.message}</span>
                    )}
                  </div>
                )}
              </div>
            </div>

            {/* Collapsible YAML Preview */}
            <div className="yaml-preview-section">
              <button
                type="button"
                className="btn-link"
                onClick={() => setShowYaml(!showYaml)}
              >
                {showYaml ? '▼ Ẩn cấu hình lmcache_vllm.yaml' : '▶ Xem trước file lmcache_vllm.yaml'}
              </button>
              {showYaml && (
                <pre className="yaml-box">
                  <code>{yamlContent || 'Chưa có cấu hình YAML'}</code>
                </pre>
              )}
            </div>

            {/* Footer Actions */}
            <div className="modal-footer">
              <button type="button" className="btn-cancel" onClick={onClose}>
                Hủy bỏ
              </button>
              <button
                type="submit"
                className="btn-save"
                disabled={saving}
              >
                {saving ? '⏳ Đang lưu & nạp vào vLLM...' : '💾 Lưu & Kích hoạt cấu hình'}
              </button>
            </div>
          </form>
        )}
      </div>

      <style jsx>{`
        .modal-backdrop {
          position: fixed;
          top: 0; left: 0; right: 0; bottom: 0;
          background: rgba(10, 15, 29, 0.75);
          backdrop-filter: blur(4px);
          display: flex;
          align-items: center;
          justify-content: center;
          z-index: 9999;
        }
        .modal-content {
          background: #111827;
          color: #f3f4f6;
          border: 1px solid #374151;
          border-radius: 12px;
          width: 90%;
          max-width: 650px;
          box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.5);
          overflow: hidden;
        }
        .modal-header {
          display: flex;
          justify-content: space-between;
          align-items: flex-start;
          padding: 20px 24px;
          border-bottom: 1px solid #1f2937;
        }
        .modal-header h2 { margin: 0; font-size: 1.25rem; font-weight: 600; color: #fff; }
        .subtitle { margin: 4px 0 0; font-size: 0.85rem; color: #9ca3af; }
        .close-btn { background: transparent; border: none; font-size: 1.2rem; color: #9ca3af; cursor: pointer; }
        .modal-body { padding: 20px 24px; display: flex; flex-direction: column; gap: 16px; }
        .alert { padding: 10px 14px; border-radius: 6px; font-size: 0.875rem; }
        .alert-error { background: rgba(239, 68, 68, 0.15); border: 1px solid #ef4444; color: #fca5a5; }
        .alert-success { background: rgba(16, 185, 129, 0.15); border: 1px solid #10b981; color: #6ee7b7; }
        .status-row { display: flex; align-items: center; justify-content: space-between; background: #1f2937; padding: 10px 14px; border-radius: 8px; }
        .status-badge { font-weight: 600; font-size: 0.85rem; padding: 4px 10px; border-radius: 12px; }
        .status-active { background: rgba(16, 185, 129, 0.2); color: #10b981; }
        .status-disabled { background: rgba(107, 114, 128, 0.2); color: #9ca3af; }
        .status-error { background: rgba(239, 68, 68, 0.2); color: #ef4444; }
        .toggle-row { display: flex; align-items: center; gap: 14px; padding: 12px; background: #1a2234; border-radius: 8px; border: 1px solid #2d3748; }
        .toggle-label { display: flex; flex-direction: column; font-size: 0.95rem; }
        .toggle-label small { color: #9ca3af; font-size: 0.8rem; }
        .form-fields { display: flex; flex-direction: column; gap: 14px; transition: opacity 0.2s; }
        .fields-disabled { opacity: 0.45; pointer-events: none; }
        .form-group-row { display: flex; gap: 14px; }
        .flex-1 { flex: 1; }
        .flex-2 { flex: 2; }
        .form-group { display: flex; flex-direction: column; gap: 6px; }
        .form-group label { font-size: 0.85rem; font-weight: 500; color: #d1d5db; }
        .form-group input, .form-group select {
          background: #1f2937;
          border: 1px solid #4b5563;
          border-radius: 6px;
          padding: 8px 12px;
          color: #fff;
          font-size: 0.9rem;
        }
        .form-group small { font-size: 0.75rem; color: #9ca3af; }
        .test-connection-section { display: flex; align-items: center; justify-content: space-between; margin-top: 4px; }
        .btn-test {
          background: #374151; color: #fff; border: 1px solid #4b5563; border-radius: 6px;
          padding: 8px 14px; font-size: 0.85rem; cursor: pointer; transition: 0.2s;
        }
        .btn-test:hover:not(:disabled) { background: #4b5563; }
        .test-badge { font-size: 0.85rem; padding: 6px 12px; border-radius: 6px; display: flex; gap: 8px; }
        .test-pass { background: rgba(16, 185, 129, 0.2); color: #10b981; }
        .test-fail { background: rgba(239, 68, 68, 0.2); color: #ef4444; }
        .latency { font-weight: bold; }
        .btn-link { background: none; border: none; color: #60a5fa; cursor: pointer; font-size: 0.85rem; padding: 0; }
        .yaml-box { background: #030712; padding: 12px; border-radius: 6px; font-size: 0.8rem; color: #38bdf8; overflow-x: auto; max-height: 140px; }
        .modal-footer { display: flex; justify-content: flex-end; gap: 12px; padding-top: 10px; border-top: 1px solid #1f2937; }
        .btn-cancel { background: transparent; border: 1px solid #4b5563; color: #d1d5db; padding: 8px 16px; border-radius: 6px; cursor: pointer; }
        .btn-save { background: #2563eb; border: none; color: #fff; padding: 8px 18px; border-radius: 6px; font-weight: 500; cursor: pointer; }
        .btn-save:hover:not(:disabled) { background: #1d4ed8; }

        /* Switch Toggle */
        .switch { position: relative; display: inline-block; width: 44px; height: 24px; flex-shrink: 0; }
        .switch input { opacity: 0; width: 0; height: 0; }
        .slider { position: absolute; cursor: pointer; top: 0; left: 0; right: 0; bottom: 0; background-color: #4b5563; transition: .3s; }
        .slider:before { position: absolute; content: ""; height: 18px; width: 18px; left: 3px; bottom: 3px; background-color: white; transition: .3s; }
        input:checked + .slider { background-color: #2563eb; }
        input:checked + .slider:before { transform: translateX(20px); }
        .slider.round { border-radius: 24px; }
        .slider.round:before { border-radius: 50%; }
      `}</style>
    </div>
  );
};
```
