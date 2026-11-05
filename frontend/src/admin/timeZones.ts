export interface TimeZoneOption {
  value: string
  city: string
  aliases: string[]
}

export interface LocationOption {
  name: string
  country: string
  timeZone: string
  aliases: string[]
}

const DEFINITIONS: TimeZoneOption[] = [
  { value: 'Europe/Kaliningrad', city: 'Калининград', aliases: ['калининград'] },
  { value: 'Europe/Moscow', city: 'Москва', aliases: ['москва', 'московская область', 'санкт петербург', 'санкт-петербург'] },
  { value: 'Europe/Samara', city: 'Самара', aliases: ['самара', 'саратов', 'ульяновск', 'ижевск', 'астрахань'] },
  { value: 'Asia/Yekaterinburg', city: 'Екатеринбург', aliases: ['екатеринбург', 'челябинск', 'уфа', 'пермь', 'тюмень', 'курган'] },
  { value: 'Asia/Omsk', city: 'Омск', aliases: ['омск'] },
  { value: 'Asia/Novosibirsk', city: 'Новосибирск', aliases: ['новосибирск', 'барнаул', 'томск'] },
  { value: 'Asia/Krasnoyarsk', city: 'Красноярск', aliases: ['красноярск', 'кемерово', 'новокузнецк'] },
  { value: 'Asia/Irkutsk', city: 'Иркутск', aliases: ['иркутск', 'улан удэ', 'улан-удэ'] },
  { value: 'Asia/Yakutsk', city: 'Якутск', aliases: ['якутск', 'чита', 'благовещенск'] },
  { value: 'Asia/Vladivostok', city: 'Владивосток', aliases: ['владивосток', 'хабаровск', 'южно сахалинск', 'южно-сахалинск'] },
  { value: 'Asia/Magadan', city: 'Магадан', aliases: ['магадан'] },
  { value: 'Asia/Kamchatka', city: 'Камчатка', aliases: ['камчатка', 'петропавловск камчатский', 'петропавловск-камчатский', 'анадырь'] },
  { value: 'Europe/Minsk', city: 'Минск', aliases: ['минск', 'беларусь'] },
  { value: 'Europe/Istanbul', city: 'Стамбул', aliases: ['стамбул', 'турция'] },
  { value: 'Asia/Tbilisi', city: 'Тбилиси', aliases: ['тбилиси', 'грузия'] },
  { value: 'Asia/Yerevan', city: 'Ереван', aliases: ['ереван', 'армения'] },
  { value: 'Asia/Dubai', city: 'Дубай', aliases: ['дубай', 'оаэ'] },
  { value: 'Asia/Almaty', city: 'Алматы', aliases: ['алматы', 'астана', 'казахстан'] },
  { value: 'Asia/Tashkent', city: 'Ташкент', aliases: ['ташкент', 'узбекистан'] },
  { value: 'Asia/Kolkata', city: 'Дели', aliases: ['дели', 'мумбаи', 'индия'] },
  { value: 'Asia/Bangkok', city: 'Бангкок', aliases: ['бангкок', 'таиланд'] },
  { value: 'Asia/Shanghai', city: 'Пекин', aliases: ['пекин', 'шанхай', 'китай'] },
  { value: 'Asia/Tokyo', city: 'Токио', aliases: ['токио', 'япония'] },
  { value: 'Europe/London', city: 'Лондон', aliases: ['лондон', 'великобритания'] },
  { value: 'Europe/Berlin', city: 'Берлин', aliases: ['берлин', 'германия'] },
  { value: 'Europe/Paris', city: 'Париж', aliases: ['париж', 'франция'] },
  { value: 'America/New_York', city: 'Нью-Йорк', aliases: ['нью йорк', 'нью-йорк'] },
  { value: 'America/Chicago', city: 'Чикаго', aliases: ['чикаго'] },
  { value: 'America/Denver', city: 'Денвер', aliases: ['денвер'] },
  { value: 'America/Los_Angeles', city: 'Лос-Анджелес', aliases: ['лос анджелес', 'лос-анджелес'] },
  { value: 'America/Sao_Paulo', city: 'Сан-Паулу', aliases: ['сан паулу', 'сан-паулу', 'бразилия'] },
  { value: 'Africa/Johannesburg', city: 'Йоханнесбург', aliases: ['йоханнесбург', 'юар'] },
  { value: 'Australia/Sydney', city: 'Сидней', aliases: ['сидней', 'австралия'] },
  { value: 'Pacific/Auckland', city: 'Окленд', aliases: ['окленд', 'новая зеландия'] },
  { value: 'UTC', city: 'Всемирное координированное время', aliases: ['utc', 'всемирное время'] },
]

export const TIME_ZONE_OPTIONS = DEFINITIONS.map((option) => ({
  ...option,
  label: `${option.city} — ${utcOffset(option.value)}`,
}))

export const LOCATION_OPTIONS: LocationOption[] = [
  { name: 'Москва', country: 'Россия', timeZone: 'Europe/Moscow', aliases: ['москва'] },
  { name: 'Санкт-Петербург', country: 'Россия', timeZone: 'Europe/Moscow', aliases: ['санкт петербург', 'санкт-петербург', 'спб'] },
  { name: 'Казань', country: 'Россия', timeZone: 'Europe/Moscow', aliases: ['казань'] },
  { name: 'Нижний Новгород', country: 'Россия', timeZone: 'Europe/Moscow', aliases: ['нижний новгород'] },
  { name: 'Самара', country: 'Россия', timeZone: 'Europe/Samara', aliases: ['самара'] },
  { name: 'Екатеринбург', country: 'Россия', timeZone: 'Asia/Yekaterinburg', aliases: ['екатеринбург'] },
  { name: 'Челябинск', country: 'Россия', timeZone: 'Asia/Yekaterinburg', aliases: ['челябинск'] },
  { name: 'Омск', country: 'Россия', timeZone: 'Asia/Omsk', aliases: ['омск'] },
  { name: 'Новосибирск', country: 'Россия', timeZone: 'Asia/Novosibirsk', aliases: ['новосибирск'] },
  { name: 'Красноярск', country: 'Россия', timeZone: 'Asia/Krasnoyarsk', aliases: ['красноярск'] },
  { name: 'Иркутск', country: 'Россия', timeZone: 'Asia/Irkutsk', aliases: ['иркутск'] },
  { name: 'Якутск', country: 'Россия', timeZone: 'Asia/Yakutsk', aliases: ['якутск'] },
  { name: 'Владивосток', country: 'Россия', timeZone: 'Asia/Vladivostok', aliases: ['владивосток'] },
  { name: 'Магадан', country: 'Россия', timeZone: 'Asia/Magadan', aliases: ['магадан'] },
  { name: 'Камчатка', country: 'Россия', timeZone: 'Asia/Kamchatka', aliases: ['камчатка', 'петропавловск камчатский', 'петропавловск-камчатский'] },
  { name: 'Минск', country: 'Беларусь', timeZone: 'Europe/Minsk', aliases: ['минск'] },
  { name: 'Алматы', country: 'Казахстан', timeZone: 'Asia/Almaty', aliases: ['алматы'] },
  { name: 'Сан-Паулу', country: 'Бразилия', timeZone: 'America/Sao_Paulo', aliases: ['сан паулу', 'сан-паулу'] },
  { name: 'Лондон', country: 'Великобритания', timeZone: 'Europe/London', aliases: ['лондон'] },
  { name: 'Берлин', country: 'Германия', timeZone: 'Europe/Berlin', aliases: ['берлин'] },
  { name: 'Токио', country: 'Япония', timeZone: 'Asia/Tokyo', aliases: ['токио'] },
]

export const LOCATION_SUGGESTIONS = LOCATION_OPTIONS.map((option) => option.name)

export function utcOffset(timeZone: string, at = new Date()): string {
  try {
    const part = new Intl.DateTimeFormat('en-US', {
      timeZone,
      timeZoneName: 'shortOffset',
    }).formatToParts(at).find((item) => item.type === 'timeZoneName')?.value ?? 'GMT'
    return part.replace('GMT', 'UTC').replace(/:00$/, '')
  } catch {
    return 'UTC'
  }
}

export function timeZoneLabel(value: string): string {
  if (!value) return 'Выберите часовой пояс'
  const known = TIME_ZONE_OPTIONS.find((option) => option.value === value)
  if (known) return known.label
  const city = value.split('/').at(-1)?.replaceAll('_', ' ') || 'Настроенный часовой пояс'
  return `${city} — ${utcOffset(value)}`
}

export function filterTimeZones(query: string, currentValue?: string) {
  const normalized = normalize(query)
  const options = currentValue && !TIME_ZONE_OPTIONS.some((option) => option.value === currentValue)
    ? [{ value: currentValue, city: timeZoneLabel(currentValue).split(' — ')[0], aliases: [], label: timeZoneLabel(currentValue) }, ...TIME_ZONE_OPTIONS]
    : TIME_ZONE_OPTIONS
  if (!normalized) return options
  return options.filter((option) => normalize(`${option.label} ${option.aliases.join(' ')} ${option.value}`).includes(normalized))
}

export function suggestTimeZone(location: string): string | null {
  const normalized = normalize(location)
  if (!normalized) return null
  const known = filterLocations(location)[0]
  if (known && locationMatchRank(known, normalized) <= 1) return known.timeZone
  return TIME_ZONE_OPTIONS.find((option) => option.aliases.some((alias) => normalized.includes(normalize(alias))))?.value ?? null
}

export function filterLocations(query: string): LocationOption[] {
  const normalized = normalize(query)
  if (!normalized) return LOCATION_OPTIONS
  return LOCATION_OPTIONS
    .map((option, index) => ({ option, index, rank: locationMatchRank(option, normalized) }))
    .filter(({ rank }) => rank < 4)
    .sort((left, right) => left.rank - right.rank || left.index - right.index)
    .map(({ option }) => option)
}

function locationMatchRank(option: LocationOption, query: string): number {
  const name = normalize(option.name)
  const aliases = option.aliases.map(normalize)
  if (name === query) return 0
  if (aliases.includes(query)) return 1
  if (name.startsWith(query) || aliases.some((alias) => alias.startsWith(query))) return 2
  if (name.includes(query) || aliases.some((alias) => alias.includes(query))) return 3
  return 4
}

export function normalizeLocation(value: string): string {
  return normalize(value)
}

function normalize(value: string): string {
  return value.toLocaleLowerCase('ru-RU').replaceAll('ё', 'е').replace(/[^a-zа-я0-9]+/giu, ' ').trim()
}
