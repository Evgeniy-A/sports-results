import type { ResultCorrectionReason, ResultInquiryAvailability } from '../api/types'
import { formatInquiryDeadline } from './resultInquiry.ts'

export type ResultIssueVerificationDecision =
  | { state: 'VERIFICATION_FAILED' }
  | { state: 'FORM' }
  | { state: 'DUPLICATE'; issueId: number }

export interface CorrectionReasonOption {
  value: Exclude<ResultCorrectionReason, 'PARTICIPANT_DATA'>
  label: string
}

export const PUBLIC_CORRECTION_REASONS: readonly CorrectionReasonOption[] = [
  { value: 'OFFICIAL_TIME', label: 'Официальное время' },
  { value: 'CHIP_TIME', label: 'Чистое время' },
  { value: 'RESULT_STATUS', label: 'Статус результата' },
  { value: 'RACE_OR_FORMAT', label: 'Старт / дистанция' },
  { value: 'OTHER', label: 'Другое' },
]

export function decideResultIssueVerification(
  verifiedForFlow: boolean,
  activeIssue: { issueId: number } | null | undefined,
): ResultIssueVerificationDecision {
  if (!verifiedForFlow) return { state: 'VERIFICATION_FAILED' }
  if (activeIssue) return { state: 'DUPLICATE', issueId: activeIssue.issueId }
  return { state: 'FORM' }
}

export function parseClaimedTime(value: string): number | null {
  const match = value.trim().match(/^(\d{1,3}):([0-5]\d):([0-5]\d)(?:[.,](\d{1,3}))?$/)
  if (!match) return null
  const [, hours, minutes, seconds, fraction = ''] = match
  const milliseconds = fraction ? Number(fraction.padEnd(3, '0')) : 0
  return (((Number(hours) * 60 + Number(minutes)) * 60) + Number(seconds)) * 1000 + milliseconds
}

export function localDateTimeToInstant(value: string): string | null {
  if (!value) return null
  const parsed = new Date(value)
  return Number.isNaN(parsed.getTime()) ? null : parsed.toISOString()
}

export function resultIssueAvailabilityMessage(
  availability: ResultInquiryAvailability,
  deadline?: string,
  timeZone = 'UTC',
): string | null {
  if (availability === 'CLOSED') {
    const formattedDeadline = formatInquiryDeadline(deadline, timeZone)
    return formattedDeadline
      ? `Срок подачи обращений завершён. Обращения принимались до ${formattedDeadline}.`
      : 'Срок подачи обращений завершён.'
  }
  if (availability === 'NOT_OPEN_YET') {
    return 'Приём обращений ещё не открыт.'
  }
  if (availability === 'DISABLED') {
    return 'Приём обращений по результатам закрыт организатором.'
  }
  return null
}
