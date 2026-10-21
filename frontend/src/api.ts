let csrf = ''
export async function refreshCsrf() {
  const r = await fetch('/api/auth/csrf')
  const d = await r.json()
  csrf = d.token
}
export async function api<T>(path: string, options: RequestInit = {}): Promise<T> {
  if (options.method && !['GET', 'HEAD'].includes(options.method) && !csrf) await refreshCsrf()
  const headers = new Headers(options.headers)
  if (options.body && !(options.body instanceof FormData))
    headers.set('Content-Type', 'application/json')
  if (csrf) headers.set('X-CSRF-TOKEN', csrf)
  const r = await fetch('/api' + path, { ...options, headers })
  if (!r.ok) {
    let message =
      r.status === 401
        ? '请先登录后继续'
        : r.status === 403
          ? '请求未通过安全校验，请刷新后重试'
          : '请求未完成，请稍后重试'
    try {
      const d = await r.json()
      message = d.message || message
    } catch {}
    throw new Error(message)
  }
  if (r.status === 204) return undefined as T
  return r.json()
}
