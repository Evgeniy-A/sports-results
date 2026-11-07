import assert from 'node:assert/strict'
import test from 'node:test'
import { groupJournalRowsByCity } from '../src/admin/journalGrouping.ts'

test('journal city grouping normalizes blank, whitespace and case variants deterministically', () => {
  const blank = { id: 1, location: null }
  const whitespace = { id: 2, location: '   ' }
  const firstMoscow = { id: 3, location: ' Moscow ' }
  const secondMoscow = { id: 4, location: 'moscow' }
  const spacedMoscow = { id: 5, location: 'MOSCOW  CITY' }
  const collapsedMoscow = { id: 6, location: 'moscow city' }

  assert.deepEqual(groupJournalRowsByCity([
    firstMoscow,
    blank,
    secondMoscow,
    whitespace,
    spacedMoscow,
    collapsedMoscow,
  ]), [
    { city: 'Город не указан', rows: [blank, whitespace] },
    { city: 'Moscow', rows: [firstMoscow, secondMoscow] },
    { city: 'MOSCOW CITY', rows: [spacedMoscow, collapsedMoscow] },
  ])
})
