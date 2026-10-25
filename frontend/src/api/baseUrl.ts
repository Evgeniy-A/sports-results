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

export function apiResourceUrl(
  resourceUrl: string,
  apiBaseUrl = import.meta.env?.VITE_API_BASE_URL,
): string {
  const normalizedResourceUrl = resourceUrl.trim()
  if (/^https?:\/\//i.test(normalizedResourceUrl)) return normalizedResourceUrl
  return apiUrl(normalizedResourceUrl.replace(/^\/api(?=\/)/, ''), apiBaseUrl)
}
