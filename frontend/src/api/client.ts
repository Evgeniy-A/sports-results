import type {
  ApiFieldViolation,
  Category,
  CreateMissingResultIssueRequest,
  CreateResultCorrectionIssueRequest,
  EventFilterOptions,
  EventQuery,
  EventSummary,
  EventDetails,
  PageResponse,
  Race,
  ResultDetails,
  ResultIssueCreated,
  ResultIssueAttachmentCapabilities,
  ResultIssueAttachmentStatus,
  ResultIssueAttachmentUpload,
  ResultInquiryLookup,
  ResultListItem,
  ResultQuery,
  VerifyResultInquiryRequest,
} from './types'

const API_ROOT = import.meta.env?.VITE_API_ROOT ?? '/api'

export class ApiError extends Error {
  readonly status: number
  readonly code: string | null
  readonly violations: ApiFieldViolation[]

  constructor(
    status: number,
    message: string,
    code: string | null = null,
    violations: ApiFieldViolation[] = [],
  ) {
    super(message)
    this.status = status
    this.code = code
    this.violations = violations
  }
}

interface ApiErrorPayload {
  code?: unknown
  violations?: unknown
}

export async function readJsonBody<T>(response: Response, allowEmpty = false): Promise<T | undefined> {
  const body = await response.text()
  if (!body.trim()) {
    if (allowEmpty || response.status === 204 || response.status === 205) return undefined
    throw new ApiError(
      response.status,
      'Сервер вернул пустой ответ вместо JSON',
      'EMPTY_RESPONSE',
    )
  }
  return JSON.parse(body) as T
}

async function request<T>(path: string, signal?: AbortSignal, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_ROOT}${path}`, {
    ...init,
    headers: { Accept: 'application/json', ...init?.headers },
    signal,
  })

  if (!response.ok) {
    const payload = await response.json().catch(() => null) as ApiErrorPayload | null
    const code = typeof payload?.code === 'string' ? payload.code : null
    const violations = Array.isArray(payload?.violations)
      ? payload.violations.filter((violation): violation is ApiFieldViolation => {
        if (!violation || typeof violation !== 'object') return false
        const candidate = violation as Partial<ApiFieldViolation>
        return typeof candidate.field === 'string' && typeof candidate.message === 'string'
      })
      : []
    throw new ApiError(response.status, `Сервер вернул ошибку ${response.status}`, code, violations)
  }

  return await readJsonBody<T>(response) as T
}

function queryString(query: object): string {
  const params = new URLSearchParams()
  Object.entries(query).forEach(([key, value]) => {
    if (value !== undefined && value !== '') params.set(key, String(value))
  })
  return params.toString()
}

export const api = {
  events: (query: EventQuery, signal?: AbortSignal) =>
    request<PageResponse<EventSummary>>(`/events?${queryString(query)}`, signal),
  eventFilterOptions: (query: EventQuery, signal?: AbortSignal) =>
    request<EventFilterOptions>(`/events/filter-options?${queryString(query)}`, signal),
  eventBySlug: (slug: string, signal?: AbortSignal) =>
    request<EventDetails>(`/events/slug/${encodeURIComponent(slug)}`, signal),
  races: (eventId: number, signal?: AbortSignal) =>
    request<Race[]>(`/events/${eventId}/races`, signal),
  categories: (eventId: number, raceId: number, signal?: AbortSignal) =>
    request<Category[]>(`/events/${eventId}/categories?raceId=${raceId}`, signal),
  results: (eventId: number, query: ResultQuery, signal?: AbortSignal) =>
    request<PageResponse<ResultListItem>>(
      `/events/${eventId}/results?${queryString(query)}`,
      signal,
    ),
  result: (resultId: number, signal?: AbortSignal) =>
    request<ResultDetails>(`/results/${resultId}`, signal),
  resultInquiry: (eventId: number, bib: string, signal?: AbortSignal) =>
    request<ResultInquiryLookup>(
      `/events/${eventId}/result-inquiry?${queryString({ bib })}`,
      signal,
    ),
  verifyResultInquiry: (
    eventId: number,
    verification: VerifyResultInquiryRequest,
    signal?: AbortSignal,
  ) => request<ResultInquiryLookup>(`/events/${eventId}/result-inquiry/verify`, signal, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(verification),
  }),
  createMissingResultIssue: (
    eventId: number,
    issue: CreateMissingResultIssueRequest,
    signal?: AbortSignal,
  ) => request<ResultIssueCreated>(`/events/${eventId}/result-issue-requests/missing`, signal, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(issue),
  }),
  createResultCorrectionIssue: (
    eventId: number,
    resultId: number,
    issue: CreateResultCorrectionIssueRequest,
    signal?: AbortSignal,
  ) => request<ResultIssueCreated>(
    `/events/${eventId}/results/${resultId}/result-issue-requests`,
    signal,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(issue),
    },
  ),
  resultIssueAttachmentCapabilities: (signal?: AbortSignal) =>
    request<ResultIssueAttachmentCapabilities>('/result-issue-attachments/capabilities', signal),
  createResultIssueAttachment: (
    issueId: number,
    token: string,
    attachment: { originalFileName: string; contentType: string | null; sizeBytes: number },
    signal?: AbortSignal,
  ) => request<ResultIssueAttachmentUpload>(`/result-issues/${issueId}/attachments`, signal, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-Result-Issue-Upload-Token': token,
    },
    body: JSON.stringify(attachment),
  }),
  refreshResultIssueAttachmentUpload: (
    issueId: number,
    attachmentId: number,
    token: string,
    signal?: AbortSignal,
  ) => request<ResultIssueAttachmentUpload>(
    `/result-issues/${issueId}/attachments/${attachmentId}/authorize`,
    signal,
    { method: 'POST', headers: { 'X-Result-Issue-Upload-Token': token } },
  ),
  confirmResultIssueAttachment: (
    issueId: number,
    attachmentId: number,
    token: string,
    signal?: AbortSignal,
  ) => request<ResultIssueAttachmentStatus>(
    `/result-issues/${issueId}/attachments/${attachmentId}/confirm`,
    signal,
    { method: 'POST', headers: { 'X-Result-Issue-Upload-Token': token } },
  ),
}
