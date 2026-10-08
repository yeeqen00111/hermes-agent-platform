/**
 * 当前操作人（X-Actor-User-Id）。
 * 评审权限（发起/闭环）与「我的问题记录」都依赖它；开发期用本地存储占位，接入 SSO 后替换。
 */
const KEY = 'hermes.actorUserId'

export function getActorUserId(): number {
  const raw = localStorage.getItem(KEY)
  const n = raw ? Number(raw) : NaN
  return Number.isFinite(n) && n > 0 ? n : 1
}

export function setActorUserId(id: number): void {
  if (Number.isFinite(id) && id > 0) {
    localStorage.setItem(KEY, String(id))
  }
}
