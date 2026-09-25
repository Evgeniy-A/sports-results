import type { EventSummary, ResultInquiryAvailability } from '../api/types'
import type { ResultInquiryDeadlineMode, ResultInquirySettings } from './types'
import { localDateTimeToIso, toDateTimeLocal } from './time.ts'

export const MAX_RESULT_INQUIRY_WINDOW_DAYS = 3650

export interface ResultInquiryFieldVisibility {
  windowDays: boolean
  fixedDate: boolean
}

export interface ResultInquirySummary {
  status: string
  term: string
  deadline: string
}

export function resultInquiryFieldVisibility(
  deadlineMode: ResultInquiryDeadlineMode,
): ResultInquiryFieldVisibility {
  return {
    windowDays: deadlineMode === 'AFTER_EVENT_DAYS',
    fixedDate: deadlineMode === 'FIXED_DATE',
  }
}

export function calculateResultInquiryDeadline(
  event: Pick<EventSummary, 'startsAt' | 'endsAt' | 'timeZone'>,
  deadlineMode: ResultInquiryDeadlineMode,
  windowDays: number | null,
  fixedDate: string | null,
): string | null {
  const base = event.endsAt ?? event.startsAt
  const localBase = base ? toDateTimeLocal(base, event.timeZone) : ''
  return calculateDraftResultInquiryDeadline(
    localBase, '', deadlineMode, windowDays, fixedDate, event.timeZone,
  )
}

export function calculateDraftResultInquiryDeadline(
  startsAt: string,
  endsAt: string,
  deadlineMode: ResultInquiryDeadlineMode,
  windowDays: number | null,
  fixedDate: string | null,
  timeZone: string,
): string | null {
  if (deadlineMode === 'FIXED_DATE') return fixedDate ? endOfLocalDate(fixedDate, timeZone) : null
  const base = endsAt || startsAt
  if (!base || !windowDays || windowDays < 1 || windowDays > MAX_RESULT_INQUIRY_WINDOW_DAYS) return null
  const match = base.match(/^(\d{4})-(\d{2})-(\d{2})T/)
  if (!match) return null
  const [, year, month, day] = match
  const shifted = new Date(Date.UTC(
    Number(year), Number(month) - 1, Number(day) + windowDays,
  ))
  const finalDate = [
    shifted.getUTCFullYear().toString().padStart(4, '0'),
    (shifted.getUTCMonth() + 1).toString().padStart(2, '0'),
    shifted.getUTCDate().toString().padStart(2, '0'),
  ].join('-')
  return endOfLocalDate(finalDate, timeZone)
}

export function formatResultInquiryDeadline(deadline: string | null, timeZone: string): string {
  if (!deadline) return 'не рассчитан'
  const parsed = new Date(deadline)
  if (Number.isNaN(parsed.getTime())) return 'не рассчитан'
  return new Intl.DateTimeFormat('ru-RU', {
    day: 'numeric', month: 'long', year: 'numeric', timeZone,
  }).format(parsed) + ' включительно'
}

export function resultInquirySummary(
  settings: ResultInquirySettings,
  timeZone: string,
): ResultInquirySummary {
  const modeConfigured = settings.deadlineMode === 'FIXED_DATE'
    ? Boolean(settings.fixedDate)
    : Boolean(settings.windowDays && settings.windowDays > 0)
  const term = settings.deadlineMode === 'FIXED_DATE'
    ? settings.fixedDate ? 'До выбранной даты' : 'Дата не задана'
    : settings.windowDays
      ? `${settings.windowDays} ${daysLabel(settings.windowDays)} после окончания мероприятия`
      : 'Количество дней не задано'
  return {
    status: modeConfigured
      ? resultInquiryAvailabilitySummaryLabel(settings.availability)
      : 'Требуется настройка',
    term,
    deadline: modeConfigured && settings.deadline
      ? formatResultInquiryDeadline(settings.deadline, timeZone)
      : '—',
  }
}

function endOfLocalDate(date: string, timeZone: string): string | null {
  const match = date.match(/^(\d{4})-(\d{2})-(\d{2})$/)
  if (!match) return null
  const next = new Date(Date.UTC(Number(match[1]), Number(match[2]) - 1, Number(match[3]) + 1))
  const nextDate = [
    next.getUTCFullYear().toString().padStart(4, '0'),
    (next.getUTCMonth() + 1).toString().padStart(2, '0'),
    next.getUTCDate().toString().padStart(2, '0'),
  ].join('-')
  const nextMidnight = localDateTimeToIso(`${nextDate}T00:00`, timeZone)
  return nextMidnight ? new Date(new Date(nextMidnight).getTime() - 1).toISOString() : null
}

export function resultInquiryAvailabilityLabel(availability: ResultInquiryAvailability): string {
  if (availability === 'OPEN') return 'Приём обращений открыт.'
  if (availability === 'NOT_OPEN_YET') return 'Приём обращений ещё не открыт.'
  if (availability === 'CLOSED') return 'Срок подачи обращений завершён.'
  return 'Приём обращений закрыт организатором.'
}

function resultInquiryAvailabilitySummaryLabel(availability: ResultInquiryAvailability): string {
  if (availability === 'OPEN') return 'Приём открыт'
  if (availability === 'NOT_OPEN_YET') return 'Приём ещё не открыт'
  if (availability === 'CLOSED') return 'Срок завершён'
  return 'Закрыт организатором'
}

function daysLabel(value: number): string {
  const tens = value % 100
  const units = value % 10
  if (tens >= 11 && tens <= 14) return 'дней'
  if (units === 1) return 'день'
  if (units >= 2 && units <= 4) return 'дня'
  return 'дней'
}
