import axios from 'axios'
import { getActorUserId } from './actor'

/**
 * 统一 API 客户端。dev 由 vite 代理 /api → http://localhost:8081（契约 §3）。
 * 请求自动带 X-Actor-User-Id（评审权限 / 我的问题记录依赖它）。
 */
const http = axios.create({
  baseURL: '/api',
  timeout: 30000,
})

http.interceptors.request.use((config) => {
  if (!config.headers.has('X-Actor-User-Id')) {
    config.headers.set('X-Actor-User-Id', String(getActorUserId()))
  }
  return config
})

export default http
