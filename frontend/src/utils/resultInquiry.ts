import type {
  ResultInquiryAvailability,
  ResultInquiryLookup,
  ResultQuery,
} from '../api/types'

export interface InquiryEmailTemplate {
  id: 'missing' | 'question'
  label: string
  subject: string
  body: string
}

export interface InquiryAvailabilityView {
  statusText: string
  explanation: string | null
  deadlineText: string | null
  canContact: boolean
}

export interface PublicResultsAndInquiryOutcome<T> {
  publicResults: PromiseSettledResult<T>
  inquiry: PromiseSettledResult<ResultInquiryLookup | null>
}

export type PublicResultsAndInquiryResolution<T> =
  | { state: 'CANCELLED' }
  | { state: 'PUBLIC_RESULTS_FAILED'; error: unknown }
  | {
    state: 'PUBLIC_RESULTS_LOADED'
    publicResults: T
    inquiry: ResultInquiryLookup | null
    inquiryError: unknown | null
  }

export class LatestRequestGate {
  private sequence = 0

  begin(): number {
    this.sequence += 1
    return this.sequence
  }

  invalidate(): void {
    this.sequence += 1
  }

  isCurrent(requestId: number): boolean {
    return requestId === this.sequence
  }
}

export function shouldRequestResultInquiry(
  filters: Omit<ResultQuery, 'raceId' | 'page' | 'size' | 'sort' | 'direction'>,
): boolean {
  return Boolean(filters.bib?.trim())
}

export async function loadPublicResultsAndInquiry<T>(
  filters: Omit<ResultQuery, 'raceId' | 'page' | 'size' | 'sort' | 'direction'>,
  loadPublicResults: () => Promise<T>,
  loadInquiry: () => Promise<ResultInquiryLookup>,
): Promise<PublicResultsAndInquiryOutcome<T>> {
  const publicResultsRequest = invokeRequest(loadPublicResults)
  const inquiryRequest = shouldRequestResultInquiry(filters)
    ? invokeRequest(loadInquiry)
    : Promise.resolve(null)
  const [publicResults, inquiry] = await Promise.allSettled([
    publicResultsRequest,
    inquiryRequest,
  ])
  return { publicResults, inquiry }
}

export function resolvePublicResultsAndInquiry<T>(
  outcome: PublicResultsAndInquiryOutcome<T>,
): PublicResultsAndInquiryResolution<T> {
  if (outcome.publicResults.status === 'rejected') {
    return isRequestCancellation(outcome.publicResults.reason)
      ? { state: 'CANCELLED' }
      : { state: 'PUBLIC_RESULTS_FAILED', error: outcome.publicResults.reason }
  }
  if (outcome.inquiry.status === 'rejected') {
    return {
      state: 'PUBLIC_RESULTS_LOADED',
      publicResults: outcome.publicResults.value,
      inquiry: null,
      inquiryError: isRequestCancellation(outcome.inquiry.reason) ? null : outcome.inquiry.reason,
    }
  }
  return {
    state: 'PUBLIC_RESULTS_LOADED',
    publicResults: outcome.publicResults.value,
    inquiry: outcome.inquiry.value,
    inquiryError: null,
  }
}

export function isRequestCancellation(reason: unknown): boolean {
  return typeof reason === 'object' && reason !== null && 'name' in reason
    && reason.name === 'AbortError'
}

function invokeRequest<T>(request: () => Promise<T>): Promise<T> {
  try {
    return Promise.resolve(request())
  } catch (reason) {
    return Promise.reject(reason)
  }
}

export function parseRussianBirthDate(value: string): string | null {
  const match = value.trim().match(/^(\d{2})\.(\d{2})\.(\d{4})$/)
  if (!match) return null
  const [, day, month, year] = match
  const isoDate = `${year}-${month}-${day}`
  const parsed = new Date(`${isoDate}T00:00:00Z`)
  if (Number.isNaN(parsed.getTime())) return null
  return parsed.getUTCFullYear() === Number(year)
    && parsed.getUTCMonth() + 1 === Number(month)
    && parsed.getUTCDate() === Number(day)
    ? isoDate
    : null
}

export function formatInquiryDeadline(deadline: string | undefined, timeZone: string): string | null {
  if (!deadline) return null
  const parsed = new Date(deadline)
  if (Number.isNaN(parsed.getTime())) return null
  return new Intl.DateTimeFormat('ru-RU', {
    day: '2-digit', month: '2-digit', year: 'numeric', timeZone,
  }).format(parsed)
}

export function availabilityView(
  availability: ResultInquiryAvailability,
  deadline: string | undefined,
  timeZone: string,
): InquiryAvailabilityView {
  if (availability === 'OPEN') {
    const formattedDeadline = formatInquiryDeadline(deadline, timeZone)
    return {
      statusText: 'Результат не установлен',
      explanation: null,
      deadlineText: formattedDeadline ? `Обратиться можно до ${formattedDeadline}` : null,
      canContact: true,
    }
  }
  if (availability === 'NOT_OPEN_YET') {
    return {
      statusText: 'Результат пока не установлен',
      explanation: 'Обращения по результатам будут доступны после завершения мероприятия.',
      deadlineText: null,
      canContact: false,
    }
  }
  if (availability === 'CLOSED') {
    return {
      statusText: 'Результат не установлен',
      explanation: 'Срок подачи обращений по результатам завершён.',
      deadlineText: null,
      canContact: false,
    }
  }
  return {
    statusText: 'Результат не установлен',
    explanation: null,
    deadlineText: null,
    canContact: false,
  }
}

export function inquiryRaceLabel(inquiry: ResultInquiryLookup): string {
  return [inquiry.sportFormatDisplayName, inquiry.raceDisplayName].filter(Boolean).join(' · ')
}

export function createInquiryEmailTemplates(
  eventName: string,
  raceLabel: string,
  bib: string,
): InquiryEmailTemplate[] {
  return [
    {
      id: 'missing',
      label: 'Финишировал, но результата нет',
      subject: `[RESULT_MISSING] ${eventName} | ${raceLabel} | №${bib}`,
      body: `Здравствуйте!

Я принимал участие в мероприятии, но мой результат не отображается на сайте.

Мероприятие: ${eventName}
Формат / старт / дистанция: ${raceLabel}
Стартовый номер: ${bib}

ФИО:
Дата рождения:
Примерное время старта:
Примерное время финиша:

Комментарий:

При наличии приложу скриншот с часов, трекера или другого устройства.`,
    },
    {
      id: 'question',
      label: 'Другой вопрос по отсутствующему результату',
      subject: `[RESULT_QUESTION] ${eventName} | ${raceLabel} | №${bib}`,
      body: `Здравствуйте!

Прошу уточнить информацию по моему результату.

Мероприятие: ${eventName}
Формат / старт / дистанция: ${raceLabel}
Стартовый номер: ${bib}

ФИО:
Дата рождения:

Описание вопроса:

При наличии приложу подтверждающие материалы.`,
    },
  ]
}

export function buildInquiryMailto(email: string, template: InquiryEmailTemplate): string {
  return `mailto:${email.trim()}?subject=${encodeURIComponent(template.subject)}&body=${encodeURIComponent(template.body)}`
}
