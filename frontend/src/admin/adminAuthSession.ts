import type { AdminCredentials } from './types'

export const ADMIN_AUTH_SESSION_KEY = 'sports-results.admin.credentials'

export function loadAdminCredentials(storage: Storage = window.sessionStorage): AdminCredentials | null {
  try {
    const value = JSON.parse(storage.getItem(ADMIN_AUTH_SESSION_KEY) ?? 'null') as unknown
    if (!value || typeof value !== 'object') return null
    const candidate = value as { username?: unknown; password?: unknown }
    return typeof candidate.username === 'string' && candidate.username.length > 0
      && typeof candidate.password === 'string' && candidate.password.length > 0
      ? { username: candidate.username, password: candidate.password }
      : null
  } catch {
    return null
  }
}

export function saveAdminCredentials(credentials: AdminCredentials, storage: Storage = window.sessionStorage): void {
  storage.setItem(ADMIN_AUTH_SESSION_KEY, JSON.stringify(credentials))
}

export function clearAdminCredentials(storage: Storage = window.sessionStorage): void {
  storage.removeItem(ADMIN_AUTH_SESSION_KEY)
}

export function notifyUnauthorized(status: number, onUnauthorized?: () => void): void {
  if (status === 401) onUnauthorized?.()
}
