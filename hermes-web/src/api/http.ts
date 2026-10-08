import axios from 'axios'

/**
 * 统一 API 客户端。dev 由 vite 代理 /api → http://localhost:8081（契约 §3）。
 */
const http = axios.create({
  baseURL: '/api',
  timeout: 30000,
})

export default http
