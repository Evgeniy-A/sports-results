import { ApiError, readJsonBody } from './api/client'
import { apiUrl } from './api/baseUrl'
import type {
  JournalDetail,
  JournalShareContext,
  JournalShareExchange,
  JournalSyncSummary,
  JournalWorkspaceRow,
  JournalWorkspaceSheet,
  JournalWorkItemResponse,
  Race,
} from './admin/types'
import type { JournalWorkspaceClient } from './admin/pages/AdminJournalPage'

async function request<T>(path: string, sessionToken: string | null, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')
  if (sessionToken) headers.set('X-Journal-Share-Session', sessionToken)
  const response = await fetch(apiUrl(`/shared/result-issue-workspace${path}`), {
    ...init,
    cache: 'no-store',
    headers,
  })
  if (!response.ok) {
    const payload = await response.json().catch(() => null) as { code?: string; message?: string } | null
    throw new ApiError(response.status, payload?.message ?? `HTTP ${response.status}`, payload?.code ?? null)
  }
  return await readJsonBody<T>(response) as T
}

function json(value: unknown): RequestInit {
  return { headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(value) }
}

export function exchangeJournalShare(token: string): Promise<JournalShareExchange> {
  return request('/exchange', null, { method: 'POST', ...json({ token }) })
}

export function loadJournalShareContext(sessionToken: string): Promise<JournalShareContext> {
  return request('/context', sessionToken)
}

export function createSharedJournalApi(sessionToken: string): JournalWorkspaceClient {
  return {
    journalSheets: () => request<JournalWorkspaceSheet[]>('/sheets', sessionToken),
    journalWorkspaceRows: () => request<JournalWorkspaceRow[]>('/rows', sessionToken),
    races: (eventId) => request<Race[]>(`/events/${eventId}/races`, sessionToken),
    updateJournalWorkItem: (issueId, body) => request<JournalWorkItemResponse>(
      `/issues/${issueId}`, sessionToken, { method: 'PUT', ...json(body) },
    ),
    resetJournalWorkItem: (issueId, expectedVersion) => request<JournalWorkItemResponse>(
      `/issues/${issueId}/reset`, sessionToken, { method: 'POST', ...json({ expectedVersion }) },
    ),
    syncJournalSheet: () => request<JournalSyncSummary>('/sync', sessionToken, { method: 'POST' }),
    updateIssueStatus: (_eventId, issueId, expectedStatus, status, comment = '') => request(
      `/issues/${issueId}/status`, sessionToken,
      { method: 'PUT', ...json({ expectedStatus, status, comment: comment.trim() || null }) },
    ),
    journalDetail: (issueId) => request<JournalDetail>(`/issues/${issueId}`, sessionToken),
    authorizeAttachment: (_eventId, issueId, attachmentId) => request(
      `/issues/${issueId}/attachments/${attachmentId}/download-authorization`,
      sessionToken,
      { method: 'POST' },
    ),
  }
}
