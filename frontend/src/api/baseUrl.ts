export function resolveApiRoot(apiBaseUrl: string | undefined): string {
  const normalizedBaseUrl = apiBaseUrl?.trim().replace(/\/+$/, '')
  if (!normalizedBaseUrl) return '/api'
  return normalizedBaseUrl.endsWith('/api') ? normalizedBaseUrl : `${normalizedBaseUrl}/api`
}

export function apiUrl(
  path: string,
  apiBaseUrl = import.meta.env?.VITE_API_BASE_URL,
): string {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`
  return `${resolveApiRoot(apiBaseUrl)}${normalizedPath}`
}
