import assert from 'node:assert/strict'
import test from 'node:test'
import {
  defaultPublicResultSort,
  defaultPublicEventSection,
  initialProtocolSelection,
  publicCategoryName,
  publicResultFormats,
  selectionForSportFormat,
} from '../src/utils/publicEventView.ts'

const race = (id, name) => ({ id, name })
const format = (id, displayName, races) => ({ id, displayName, races })

test('published results open results-first and draft opens event-info-first', () => {
  assert.equal(defaultPublicEventSection('PUBLISHED'), 'results')
  assert.equal(defaultPublicEventSection('DRAFT'), 'info')
})

test('default result ordering follows the configured sport time without inventing NONE standing', () => {
  assert.equal(defaultPublicResultSort('GUN_TIME'), 'gunTime')
  assert.equal(defaultPublicResultSort('CHIP_TIME'), 'chipTime')
  assert.equal(defaultPublicResultSort('NONE'), 'gunTime')
})

test('one format is selected without an extra choice and its first race opens immediately', () => {
  assert.deepEqual(
    initialProtocolSelection([format(7, 'Основной формат', [race(11, '10 км'), race(12, '42.2 км')])]),
    { sportFormatId: '7', raceId: '11' },
  )
})

test('one backend-visible race opens directly after hidden races are omitted', () => {
  const formatsAfterVisibility = [format(7, 'Индивидуальный', [race(11, '10 км')])]

  assert.deepEqual(initialProtocolSelection(formatsAfterVisibility), { sportFormatId: '7', raceId: '11' })
  assert.equal(formatsAfterVisibility[0].races.length, 1)
})

test('multiple formats require a format choice and selecting one stays inside its races', () => {
  const formats = [
    format(7, 'Индивидуальный', [race(11, '10 км'), race(12, '20 км')]),
    format(8, 'Командный', [race(13, 'Командная гонка')]),
  ]

  assert.deepEqual(initialProtocolSelection(formats), { sportFormatId: '', raceId: '' })
  assert.deepEqual(selectionForSportFormat(formats, '8'), { sportFormatId: '8', raceId: '13' })
  assert.deepEqual(selectionForSportFormat(formats, '7'), { sportFormatId: '7', raceId: '11' })
})

test('draft races are excluded from result selectors without hiding published event-program races', () => {
  const sourceFormats = [
    format(7, 'Индивидуальный', [
      { ...race(11, '10 км'), resultsPublished: true },
      { ...race(12, '42.2 км'), resultsPublished: false },
    ]),
    format(8, 'Командный', [
      { ...race(13, 'Эстафета'), resultsPublished: false },
    ]),
  ]

  const resultFormats = publicResultFormats(sourceFormats)
  assert.equal(sourceFormats[0].races.length, 2)
  assert.deepEqual(resultFormats, [
    format(7, 'Индивидуальный', [{ ...race(11, '10 км'), resultsPublished: true }]),
  ])
  assert.deepEqual(initialProtocolSelection(resultFormats), { sportFormatId: '7', raceId: '11' })
})

test('all-draft race set produces no public result selection', () => {
  const resultFormats = publicResultFormats([
    format(7, 'Индивидуальный', [{ ...race(11, '10 км'), resultsPublished: false }]),
  ])

  assert.deepEqual(resultFormats, [])
  assert.deepEqual(initialProtocolSelection(resultFormats), { sportFormatId: '', raceId: '' })
})

test('public category requires both race policy and participant category data', () => {
  assert.equal(publicCategoryName(false, { name: '18+ М' }), null)
  assert.equal(publicCategoryName(true, { name: 'М 30–34' }), 'М 30–34')
  assert.equal(publicCategoryName(true, null), null)
})

test('category visibility remains race-scoped inside one event', () => {
  const races = [
    { id: 11, categoryEnabled: false, category: { name: '18+ М' } },
    { id: 12, categoryEnabled: true, category: { name: 'Ж 35–39' } },
  ]

  assert.deepEqual(
    races.map((candidate) => publicCategoryName(candidate.categoryEnabled, candidate.category)),
    [null, 'Ж 35–39'],
  )
})
