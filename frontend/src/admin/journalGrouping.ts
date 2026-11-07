export interface JournalCityRow {
  location: string | null
}

export interface JournalCityGroup<T extends JournalCityRow> {
  city: string
  rows: T[]
}

export function groupJournalRowsByCity<T extends JournalCityRow>(rows: T[]): JournalCityGroup<T>[] {
  const groups = new Map<string, JournalCityGroup<T>>()
  rows.forEach((row) => {
    const city = normalizeCityLabel(row.location)
    const key = city.toLocaleLowerCase('ru-RU')
    const group = groups.get(key)
    if (group) group.rows.push(row)
    else groups.set(key, { city, rows: [row] })
  })
  return [...groups.values()].sort((left, right) => left.city.localeCompare(right.city, 'ru-RU'))
}

function normalizeCityLabel(value: string | null): string {
  return value?.trim().replace(/\s+/g, ' ') || 'Город не указан'
}
