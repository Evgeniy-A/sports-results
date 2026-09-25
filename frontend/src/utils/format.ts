export function formatDuration(milliseconds: number | null): string {
  if (milliseconds === null) return '—'

  const normalizedMilliseconds = Math.trunc(milliseconds)
  const totalSeconds = Math.floor(normalizedMilliseconds / 1000)
  const hours = Math.floor(totalSeconds / 3600)
  const minutes = Math.floor((totalSeconds % 3600) / 60)
  const seconds = totalSeconds % 60
  const millis = normalizedMilliseconds % 1000

  return `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}.${String(millis).padStart(3, '0')}`
}

export function formatGender(gender: string | null): string {
  if (gender?.toLowerCase() === 'female') return 'Ж'
  if (gender?.toLowerCase() === 'male') return 'М'
  return '—'
}

export function formatDate(value: string | null, timeZone = 'UTC'): string {
  if (!value) return 'Дата уточняется'
  return new Intl.DateTimeFormat('ru-RU', {
    day: '2-digit', month: '2-digit', year: 'numeric', timeZone,
  }).format(new Date(value))
}

export function formatDateRange(start: string | null, end: string | null, timeZone = 'UTC'): string {
  if (!start) return 'Дата уточняется'
  const first = formatDate(start, timeZone)
  if (!end || formatDate(end, timeZone) === first) return first
  return `${first} — ${formatDate(end, timeZone)}`
}

export function formatLocalScheduleTime(value: string): string {
  const [date, time = ''] = value.split('T')
  const [year, month, day] = date.split('-')
  return `${day}.${month}.${year} · ${time.slice(0, 5)}`
}

export function statusLabel(status: string): string {
  const labels: Record<string, string> = {
    finished: 'Финишировал', notstarted: 'Не стартовал', disqualified: 'Дисквалификация',
    quarantine: 'Карантин', running: 'На дистанции',
  }
  return labels[status.toLowerCase()] ?? 'Статус недоступен'
}
