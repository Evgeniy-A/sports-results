export function toDateTimeLocal(value: string | null, timeZone = 'UTC'): string {
  if (!value) return ''
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone,
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', hourCycle: 'h23',
  }).formatToParts(new Date(value))
  const part = (type: Intl.DateTimeFormatPartTypes) => parts.find((item) => item.type === type)?.value ?? ''
  return `${part('year')}-${part('month')}-${part('day')}T${part('hour')}:${part('minute')}`
}

export function localDateTimeToIso(value: string, timeZone = 'UTC'): string | null {
  if (!value) return null
  const match = value.match(/^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})$/)
  if (!match) return null
  const [, year, month, day, hour, minute] = match
  const targetUtc = Date.UTC(Number(year), Number(month) - 1, Number(day), Number(hour), Number(minute))
  const offsetParts = new Intl.DateTimeFormat('en-CA', {
    timeZone,
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', hourCycle: 'h23',
  }).formatToParts(new Date(targetUtc))
  const read = (type: Intl.DateTimeFormatPartTypes) => Number(offsetParts.find((item) => item.type === type)?.value)
  const representedUtc = Date.UTC(read('year'), read('month') - 1, read('day'), read('hour'), read('minute'))
  return new Date(targetUtc - (representedUtc - targetUtc)).toISOString()
}
