import type {
  ResultInquiryAvailability,
  ResultInquiryLookup,
  ResultQuery,
} from '../api/types'

export type DobFallbackIssueKind = 'RESULT_CORRECTION' | 'MISSING_RESULT'

export interface DobVerificationSupportContext {
  issueKind: DobFallbackIssueKind
  eventName: string
  startLabel: string
  bib: string
  participantName?: string | null
  description: string
}

export interface InquirySupportMessage {
  marker: '[RESULT_QUESTION]' | '[RESULT_MISSING]'
  text: string
}

export const HERO_LEAGUE_SUPPORT_URL = 'https://heroleague.ru/feedback'

export interface InquiryAvailabilityView {
  statusText: string
  explanation: string | null
  deadlineText: string | null
  canContact: boolean
}

export interface PublicResultsPageLike {
  content: readonly unknown[]
}

export interface PublicResultsAndInquiryOutcome<T extends PublicResultsPageLike> {
  publicResults: PromiseSettledResult<T>
  inquiry: PromiseSettledResult<ResultInquiryLookup | null>
}

export type PublicResultsAndInquiryResolution<T extends PublicResultsPageLike> =
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

export async function loadPublicResultsAndInquiry<T extends PublicResultsPageLike>(
  filters: Omit<ResultQuery, 'raceId' | 'page' | 'size' | 'sort' | 'direction'>,
  loadPublicResults: () => Promise<T>,
  loadInquiry: () => Promise<ResultInquiryLookup>,
): Promise<PublicResultsAndInquiryOutcome<T>> {
  const [publicResults] = await Promise.allSettled([invokeRequest(loadPublicResults)])
  const shouldLoadInquiry = publicResults.status === 'fulfilled'
    && publicResults.value.content.length === 0
    && shouldRequestResultInquiry(filters)
  const [inquiry] = await Promise.allSettled([
    shouldLoadInquiry ? invokeRequest(loadInquiry) : Promise.resolve(null),
  ])
  return { publicResults, inquiry }
}

export function resolvePublicResultsAndInquiry<T extends PublicResultsPageLike>(
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
    day: '2-digit', month: '2-digit', year: 'numeric',
    timeZone,
  }).format(parsed) + ' включительно'
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
      explanation: 'Приём обращений ещё не открыт.',
      deadlineText: null,
      canContact: false,
    }
  }
  if (availability === 'CLOSED') {
    const formattedDeadline = formatInquiryDeadline(deadline, timeZone)
    return {
      statusText: 'Результат не установлен',
      explanation: formattedDeadline
        ? `Срок подачи обращений завершён. Обращения принимались до ${formattedDeadline}.`
        : 'Срок подачи обращений завершён.',
      deadlineText: null,
      canContact: false,
    }
  }
  return {
    statusText: 'Результат не установлен',
    explanation: 'Приём обращений по результатам закрыт организатором.',
    deadlineText: null,
    canContact: false,
  }
}

export function inquiryRaceLabel(inquiry: ResultInquiryLookup): string {
  return inquiry.startDisplayName ?? inquiry.raceDisplayName ?? ''
}

export function hasMeaningfulSupportDescription(description: string): boolean {
  return description.trim().length > 0
}

export function createDobVerificationSupportMessage(
  context: DobVerificationSupportContext,
): InquirySupportMessage {
  const marker = context.issueKind === 'MISSING_RESULT'
    ? '[RESULT_MISSING]'
    : '[RESULT_QUESTION]'
  const participantLine = context.participantName?.trim()
    ? `\nУчастник: ${context.participantName.trim()}`
    : ''
  const situation = context.issueKind === 'MISSING_RESULT'
    ? 'Не удалось найти мой результат в публичном протоколе.'
    : 'Мне нужно уточнить данные видимого результата.'
  const request = context.issueKind === 'MISSING_RESULT'
    ? 'Прошу проверить регистрационные данные и восстановить или уточнить результат.'
    : 'Прошу проверить регистрационные данные и помочь уточнить результат.'
  return {
    marker,
    text: `${marker} ${context.eventName} | ${context.startLabel || 'Старт не указан'} | №${context.bib}

Здравствуйте!

${situation}
Не удалось подтвердить дату рождения через форму обращения.

Мероприятие: ${context.eventName}
Старт: ${context.startLabel || 'Не указан'}
Стартовый номер: ${context.bib}${participantLine}

Описание ситуации:
${context.description.trim()}

${request}`,
  }
}

export async function copyInquiryText(
  text: string,
  clipboard: Pick<Clipboard, 'writeText'> | undefined = typeof navigator === 'undefined'
    ? undefined
    : navigator.clipboard,
): Promise<void> {
  if (!clipboard) throw new Error('Clipboard API is unavailable')
  await clipboard.writeText(text)
}
