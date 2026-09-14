export interface TimeZoneOption {
  value: string
  city: string
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
  return TIME_ZONE_OPTIONS.find((option) => option.aliases.some((alias) => normalized.includes(normalize(alias))))?.value ?? null
}

function normalize(value: string): string {
  return value.toLocaleLowerCase('ru-RU').replaceAll('ё', 'е').replace(/[^a-zа-я0-9]+/giu, ' ').trim()
}
