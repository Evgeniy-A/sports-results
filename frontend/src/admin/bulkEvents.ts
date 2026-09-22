export interface BulkLocationRow {
  id: number
  location: string
  timeZone: string
}

export function normalizedLocation(value: string): string {
  return value.trim().replace(/\s+/g, ' ').toLocaleLowerCase('ru-RU').replaceAll('ё', 'е')
}

export function duplicateLocationIds(rows: BulkLocationRow[]): Set<number> {
  const byLocation = new Map<string, number[]>()
  rows.forEach((row) => {
    const location = normalizedLocation(row.location)
    if (!location) return
    byLocation.set(location, [...(byLocation.get(location) ?? []), row.id])
  })
  return new Set([...byLocation.values()].filter((ids) => ids.length > 1).flat())
}
