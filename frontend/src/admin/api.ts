import { ApiError, readJsonBody } from '../api/client'
import { apiUrl } from '../api/baseUrl.ts'
import type {
  AdminCategory,
  AdminCredentials,
  AdminResultDetails,
  AwardPolicyUpdate,
  AwardPolicy,
  EventIssueDetail,
  EventIssueItem,
  EventDocument,
  EventInfoBlock,
  EventScheduleItem,
  EventSeries,
  EventSummary,
  ImportApplyResult,
  ImportMode,
  ImportPreview,
  IssueStatus,
  JournalDetail,
  JournalFilters,
  JournalItem,
  PageResponse,
  ParticipantInfo,
  Race,
  RecalculationPreview,
  ResultInquirySettings,
  ResultListItem,
  ShareBatch,
  SportFormat,
  StartCluster,
} from './types'

function basicAuthorization(credentials: AdminCredentials): string {
  return `Basic ${btoa(`${credentials.username}:${credentials.password}`)}`
}

function appendQuery(params: URLSearchParams, key: string, value: unknown): void {
  if (value === undefined || value === null || value === '') return
  if (Array.isArray(value)) {
    value.forEach((item) => params.append(key, String(item)))
    return
  }
  params.set(key, String(value))
}

function queryString(query: object): string {
  const params = new URLSearchParams()
  Object.entries(query).forEach(([key, value]) => appendQuery(params, key, value))
  const value = params.toString()
  return value ? `?${value}` : ''
}

async function adminRequest<T>(
  credentials: AdminCredentials,
  path: string,
  init: RequestInit = {},
  allowEmpty = false,
): Promise<T> {
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')
  headers.set('Authorization', basicAuthorization(credentials))
  const response = await fetch(apiUrl(path), { ...init, headers })
  if (!response.ok) {
    const payload = await response.json().catch(() => null) as {
      code?: unknown
      message?: unknown
      violations?: unknown
    } | null
    const code = typeof payload?.code === 'string' ? payload.code : null
    const message = typeof payload?.message === 'string' ? payload.message : `HTTP ${response.status}`
    const violations = Array.isArray(payload?.violations)
      ? payload.violations.filter((item): item is { field: string; message: string } => {
        if (!item || typeof item !== 'object') return false
        const candidate = item as { field?: unknown; message?: unknown }
        return typeof candidate.field === 'string' && typeof candidate.message === 'string'
      })
      : []
    throw new ApiError(response.status, message, code, violations)
  }
  return await readJsonBody<T>(response, allowEmpty) as T
}

async function adminBlob(credentials: AdminCredentials, path: string): Promise<Blob> {
  const response = await fetch(apiUrl(path), {
    headers: { Authorization: basicAuthorization(credentials) },
  })
  if (!response.ok) {
    const payload = await response.json().catch(() => null) as { code?: string; message?: string } | null
    throw new ApiError(response.status, payload?.message ?? `HTTP ${response.status}`, payload?.code ?? null)
  }
  return response.blob()
}

function json(body: unknown): RequestInit {
  return {
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  }
}

export interface AdminExportArtifact {
  blob: Blob
  filename: string
  shareBatchId: string | null
  shareExpiresAt: string | null
}

export function createAdminApi(credentials: AdminCredentials) {
  const request = <T>(path: string, init?: RequestInit, allowEmpty = false) => adminRequest<T>(credentials, path, init, allowEmpty)
  return {
    events: (query: {
      name?: string
      location?: string
      publicationStatus?: string
      resultsPublicationStatus?: string
      page?: number
      size?: number
    }) => request<PageResponse<EventSummary>>(`/admin/events${queryString(query)}`),
    event: (eventId: number) => request<EventSummary>(`/admin/events/${eventId}`),
    createEvent: (body: object) => request<EventSummary>('/admin/events', { method: 'POST', ...json(body) }),
    updateEvent: (eventId: number, body: object) =>
      request<EventSummary>(`/admin/events/${eventId}`, { method: 'PUT', ...json(body) }),
    updateEventPublication: (eventId: number, body: object) =>
      request<EventSummary>(`/admin/events/${eventId}/publication`, { method: 'PUT', ...json(body) }),
    eventSeries: () => request<EventSeries[]>('/admin/event-series'),
    createEventSeries: (body: object) =>
      request<EventSeries>('/admin/event-series', { method: 'POST', ...json(body) }),
    races: (eventId: number) => request<Race[]>(`/admin/events/${eventId}/races`),
    sportFormats: (eventId: number) => request<SportFormat[]>(`/admin/events/${eventId}/sport-formats`),
    createSportFormat: (eventId: number, body: object) =>
      request<SportFormat>(`/admin/events/${eventId}/sport-formats`, { method: 'POST', ...json(body) }),
    updateSportFormat: (eventId: number, formatId: number, body: object) => request<SportFormat>(
      `/admin/events/${eventId}/sport-formats/${formatId}`,
      { method: 'PUT', ...json(body) },
    ),
    deleteSportFormat: (eventId: number, formatId: number) => request<void>(
      `/admin/events/${eventId}/sport-formats/${formatId}`,
      { method: 'DELETE' },
      true,
    ),
    createRace: (eventId: number, body: object) =>
      request<Race>(`/admin/events/${eventId}/races`, { method: 'POST', ...json(body) }),
    updateRace: (eventId: number, raceId: number, body: object) =>
      request<Race>(`/admin/events/${eventId}/races/${raceId}`, { method: 'PUT', ...json(body) }),
    publishRace: (eventId: number, raceId: number) => request(
      `/admin/events/${eventId}/races/${raceId}/results/publish`,
      { method: 'POST' },
    ),
    draftRace: (eventId: number, raceId: number, reason: string) => request(
      `/admin/events/${eventId}/races/${raceId}/results/draft`,
      { method: 'POST', ...json({ reason: reason || null }) },
    ),
    categories: (raceId: number) => request<AdminCategory[]>(`/admin/races/${raceId}/categories`),
    createCategory: (raceId: number, body: object) =>
      request<AdminCategory>(`/admin/races/${raceId}/categories`, { method: 'POST', ...json(body) }),
    updateCategory: (raceId: number, categoryId: number, body: object) => request<AdminCategory>(
      `/admin/races/${raceId}/categories/${categoryId}`,
      { method: 'PUT', ...json(body) },
    ),
    deleteCategory: (raceId: number, categoryId: number) => request<void>(
      `/admin/races/${raceId}/categories/${categoryId}`,
      { method: 'DELETE' },
      true,
    ),
    clusters: (eventId: number, raceId: number) =>
      request<StartCluster[]>(`/admin/events/${eventId}/races/${raceId}/clusters`),
    createCluster: (eventId: number, raceId: number, body: object) => request<StartCluster>(
      `/admin/events/${eventId}/races/${raceId}/clusters`,
      { method: 'POST', ...json(body) },
    ),
    updateCluster: (eventId: number, raceId: number, clusterId: number, body: object) =>
      request<StartCluster>(`/admin/events/${eventId}/races/${raceId}/clusters/${clusterId}`, {
        method: 'PUT',
        ...json(body),
      }),
    deleteCluster: (eventId: number, raceId: number, clusterId: number) => request<void>(
      `/admin/events/${eventId}/races/${raceId}/clusters/${clusterId}`,
      { method: 'DELETE' },
      true,
    ),
    awardPolicy: (raceId: number) => request<AwardPolicy>(`/admin/races/${raceId}/award-policy`),
    updateAwardPolicy: (raceId: number, body: AwardPolicyUpdate) => request<AwardPolicy>(
      `/admin/races/${raceId}/award-policy`,
      { method: 'PUT', ...json(body) },
    ),
    resultInquiry: (eventId: number) =>
      request<ResultInquirySettings>(`/admin/events/${eventId}/result-inquiry`),
    updateResultInquiry: (eventId: number, body: object) => request<ResultInquirySettings>(
      `/admin/events/${eventId}/result-inquiry`,
      { method: 'PUT', ...json(body) },
    ),
    participantInfo: (eventId: number) =>
      request<ParticipantInfo>(`/admin/events/${eventId}/participant-info`),
    updateParticipantInfo: (eventId: number, body: object) => request<ParticipantInfo>(
      `/admin/events/${eventId}/participant-info`,
      { method: 'PUT', ...json(body) },
    ),
    infoBlocks: (eventId: number) => request<EventInfoBlock[]>(`/admin/events/${eventId}/info-blocks`),
    createInfoBlock: (eventId: number, body: object) => request<EventInfoBlock>(
      `/admin/events/${eventId}/info-blocks`, { method: 'POST', ...json(body) },
    ),
    updateInfoBlock: (eventId: number, blockId: number, body: object) => request<EventInfoBlock>(
      `/admin/events/${eventId}/info-blocks/${blockId}`, { method: 'PUT', ...json(body) },
    ),
    deleteInfoBlock: (eventId: number, blockId: number) => request<void>(
      `/admin/events/${eventId}/info-blocks/${blockId}`, { method: 'DELETE' }, true,
    ),
    schedule: (eventId: number) => request<EventScheduleItem[]>(`/admin/events/${eventId}/schedule`),
    createScheduleItem: (eventId: number, body: object) => request<EventScheduleItem>(
      `/admin/events/${eventId}/schedule`, { method: 'POST', ...json(body) },
    ),
    updateScheduleItem: (eventId: number, itemId: number, body: object) => request<EventScheduleItem>(
      `/admin/events/${eventId}/schedule/${itemId}`, { method: 'PUT', ...json(body) },
    ),
    deleteScheduleItem: (eventId: number, itemId: number) => request<void>(
      `/admin/events/${eventId}/schedule/${itemId}`, { method: 'DELETE' }, true,
    ),
    documents: (eventId: number) => request<EventDocument[]>(`/admin/events/${eventId}/documents`),
    uploadDocument: (eventId: number, metadata: { type: string; displayName: string; displayOrder: number; publicDocument: boolean }, file: File) => {
      const form = new FormData()
      form.append('file', file)
      const query = queryString(metadata)
      return request<EventDocument>(`/admin/events/${eventId}/documents${query}`, { method: 'POST', body: form })
    },
    updateDocument: (eventId: number, documentId: number, body: object) => request<EventDocument>(
      `/admin/events/${eventId}/documents/${documentId}`, { method: 'PUT', ...json(body) },
    ),
    deleteDocument: (eventId: number, documentId: number) => request<void>(
      `/admin/events/${eventId}/documents/${documentId}`, { method: 'DELETE' }, true,
    ),
    documentContent: (eventId: number, documentId: number) =>
      adminBlob(credentials, `/admin/events/${eventId}/documents/${documentId}/content`),
    results: (eventId: number, query: object) =>
      request<PageResponse<ResultListItem>>(`/admin/events/${eventId}/results${queryString(query)}`),
    result: (resultId: number) => request<AdminResultDetails>(`/admin/results/${resultId}`),
    updateRegistration: (registrationId: number, body: object) => request<void>(
      `/admin/registrations/${registrationId}`,
      { method: 'PUT', ...json(body) },
      true,
    ),
    updateResult: (resultId: number, body: object) => request<void>(
      `/admin/results/${resultId}`,
      { method: 'PUT', ...json(body) },
      true,
    ),
    importPreview: async (
      eventId: number,
      mode: ImportMode,
      raceIds: number[],
      file: File,
      rowLimit = 200,
    ) => {
      const form = new FormData()
      form.append('file', file)
      const query = queryString({ mode, raceIds, rowLimit })
      return request<ImportPreview>(`/admin/events/${eventId}/imports/preview${query}`, {
        method: 'POST',
        body: form,
      })
    },
    importApply: (eventId: number, operationId: string, file: File) => {
      const form = new FormData()
      form.append('file', file)
      return request<ImportApplyResult>(
        `/admin/events/${eventId}/imports/${encodeURIComponent(operationId)}/apply`,
        { method: 'POST', body: form },
      )
    },
    recalculationPreview: (eventId: number, raceIds: number[]) => request<RecalculationPreview>(
      `/admin/events/${eventId}/result-recalculations/preview`,
      { method: 'POST', ...json({ raceIds, rowLimit: 200 }) },
    ),
    recalculationApply: (eventId: number, operationId: string) => request(
      `/admin/events/${eventId}/result-recalculations/${encodeURIComponent(operationId)}/apply`,
      { method: 'POST' },
    ),
    eventIssues: (eventId: number, query: object) => request<PageResponse<EventIssueItem>>(
      `/admin/events/${eventId}/result-issue-requests${queryString(query)}`,
    ),
    eventIssue: (eventId: number, issueId: number) =>
      request<EventIssueDetail>(`/admin/events/${eventId}/result-issue-requests/${issueId}`),
    updateIssueStatus: (eventId: number, issueId: number, expectedStatus: IssueStatus, status: IssueStatus) =>
      request(`/admin/events/${eventId}/result-issue-requests/${issueId}/status`, {
        method: 'PUT',
        ...json({ expectedStatus, status }),
      }),
    archiveIssue: (eventId: number, issueId: number, reason: 'MANUAL' | 'OTHER') => request(
      `/admin/events/${eventId}/result-issue-requests/${issueId}/archive`,
      { method: 'POST', ...json({ reason }) },
    ),
    authorizeAttachment: (eventId: number, issueId: number, attachmentId: number) =>
      request<{ downloadUrl: string; expiresAt: string }>(
        `/admin/events/${eventId}/result-issue-requests/${issueId}/attachments/${attachmentId}/download-authorization`,
        { method: 'POST' },
      ),
    journal: (filters: JournalFilters) => request<PageResponse<JournalItem>>(
      `/admin/result-issue-requests${queryString({ ...filters, status: filters.status })}`,
    ),
    journalDetail: (issueId: number) => request<JournalDetail>(`/admin/result-issue-requests/${issueId}`),
    exportJournal: async (
      filters: JournalFilters,
      shareLifetimeDays: number | null,
      noExpiry: boolean,
    ): Promise<AdminExportArtifact> => {
      const response = await fetch(apiUrl('/admin/result-issue-requests/export/xlsx'), {
        method: 'POST',
        headers: {
          Accept: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
          Authorization: basicAuthorization(credentials),
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          ...filters,
          statuses: filters.status,
          status: undefined,
          page: undefined,
          size: undefined,
          shareLifetimeDays,
          noExpiry,
        }),
      })
      if (!response.ok) {
        const payload = await response.json().catch(() => null) as { code?: string; message?: string } | null
        throw new ApiError(response.status, payload?.message ?? `HTTP ${response.status}`, payload?.code ?? null)
      }
      const disposition = response.headers.get('Content-Disposition') ?? ''
      const filename = disposition.match(/filename="?([^";]+)"?/i)?.[1] ?? 'result-issues.xlsx'
      return {
        blob: await response.blob(),
        filename,
        shareBatchId: response.headers.get('X-Share-Batch-Id'),
        shareExpiresAt: response.headers.get('X-Share-Expires-At'),
      }
    },
    shareBatch: (batchId: string) =>
      request<ShareBatch>(`/admin/result-issue-share-batches/${encodeURIComponent(batchId)}`),
    revokeShareBatch: (batchId: string) => request<ShareBatch>(
      `/admin/result-issue-share-batches/${encodeURIComponent(batchId)}/revoke`,
      { method: 'POST' },
    ),
  }
}

export type AdminApi = ReturnType<typeof createAdminApi>
