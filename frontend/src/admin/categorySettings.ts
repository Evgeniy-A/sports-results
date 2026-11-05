export function suggestedCategoryName(minAge: string | number | null, maxAge: string | number | null): string {
  const minimum = minAge === '' || minAge == null ? null : Number(minAge)
  const maximum = maxAge === '' || maxAge == null ? null : Number(maxAge)
  if (minimum == null && maximum == null) return 'Возрастная категория'
  if (minimum == null) return `До ${maximum}`
  if (maximum == null) return `${minimum}+`
  return `${minimum}–${maximum}`
}
