export interface BulkLocationRow {
  id: number
  location: string
  timeZone: string
}

export type FormatLocations = Record<string, number[]>

export function initializeFormatLocations(formatIds: string[], rowIds: number[]): FormatLocations {
  return Object.fromEntries(formatIds.map((formatId) => [formatId, [...rowIds]]))
}

export function addLocationToFormats(current: FormatLocations, rowId: number): FormatLocations {
  return Object.fromEntries(Object.entries(current).map(([formatId, rowIds]) => [
    formatId,
    rowIds.includes(rowId) ? rowIds : [...rowIds, rowId],
  ]))
}

export function removeLocationFromFormats(current: FormatLocations, rowId: number): FormatLocations {
  return Object.fromEntries(Object.entries(current).map(([formatId, rowIds]) => [
    formatId,
    rowIds.filter((currentRowId) => currentRowId !== rowId),
  ]))
}

export function reconcileFormatLocations(
  current: FormatLocations,
  formatIds: string[],
  rowIds: number[],
): FormatLocations {
  const availableRows = new Set(rowIds)
  return Object.fromEntries(formatIds.map((formatId) => [
    formatId,
    current[formatId]?.filter((rowId) => availableRows.has(rowId)) ?? [...rowIds],
  ]))
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
