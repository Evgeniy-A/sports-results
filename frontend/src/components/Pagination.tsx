interface PaginationProps {
  page: number
  totalPages: number
  onChange: (page: number) => void
}

export function Pagination({ page, totalPages, onChange }: PaginationProps) {
  if (totalPages <= 1) return null

  return (
    <nav className="pagination" aria-label="Страницы">
      <button type="button" disabled={page === 0} onClick={() => onChange(page - 1)}>← Назад</button>
      <span>Страница <strong>{page + 1}</strong> из {totalPages}</span>
      <button type="button" disabled={page + 1 >= totalPages} onClick={() => onChange(page + 1)}>Вперёд →</button>
    </nav>
  )
}
