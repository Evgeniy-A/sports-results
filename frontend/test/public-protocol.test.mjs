import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const page = await readFile(new URL('../src/components/EventResults.tsx', import.meta.url), 'utf8')
const client = await readFile(new URL('../src/api/client.ts', import.meta.url), 'utf8')
const ranking = await readFile(new URL('../src/components/RankingAchievements.tsx', import.meta.url), 'utf8')
const details = await readFile(new URL('../src/components/ResultDetailsDialog.tsx', import.meta.url), 'utf8')
const types = await readFile(new URL('../src/api/types.ts', import.meta.url), 'utf8')

test('public protocol has no gender column or ranking selector', () => {
  assert.doesNotMatch(page, /<th>Пол<\/th>/)
  assert.doesNotMatch(page, /Основа протокола|Gun Time|Chip Time/)
  assert.match(page, /Официальное время/)
  assert.match(page, /Чистое время/)
})

test('public status filter contains only supported statuses', () => {
  assert.match(page, /value="finished"/)
  assert.match(page, /value="disqualified"/)
  assert.doesNotMatch(page, /value="(?:notstarted|running|quarantine|unknown)"/)
})

test('filter reset preserves race selection and sorting while clearing public filters', () => {
  const reset = page.match(/const resetFilters = \(\) => \{([\s\S]*?)\n  \}/)?.[1] ?? ''
  assert.doesNotMatch(reset, /setRaceId|setSportFormatId/)
  assert.match(reset, /setGender\(''\)/)
  assert.match(reset, /setCategoryId\(''\)/)
  assert.match(reset, /setClusterId\(''\)/)
  assert.match(reset, /setStatus\(''\)/)
  assert.match(reset, /setName\(''\)/)
  assert.match(reset, /setBib\(''\)/)
  assert.match(reset, /setPage\(0\)/)
  assert.doesNotMatch(reset, /setSort|setDirection/)
})

test('category controls and cells depend on backend race metadata', () => {
  assert.match(page, /selectedRace\?\.rules\?\.categoryEnabled/)
  assert.match(page, /showCategoryColumn && <th>Категория<\/th>/)
  assert.match(page, /categoryEnabled=\{showCategoryColumn\}/)
})

test('result detail category follows race policy without rendering an empty placeholder', () => {
  assert.match(details, /publicCategoryName\(categoryEnabled, result\.category\)/)
  assert.match(details, /\{categoryName && <div><dt>Категория<\/dt><dd>\{categoryName\}<\/dd><\/div>\}/)
  assert.doesNotMatch(details, /result\.category\?\.name \?\? '—'/)
  assert.match(details, /<dt>Дистанция<\/dt>/)
  assert.match(details, /<dt>Пол<\/dt>/)
  assert.match(details, /<dt>Официальное время/)
  assert.match(details, /<dt>Чистое время/)
  assert.match(details, /Сообщить об ошибке/)
})

test('cluster filter is race-scoped and never changes official achievements', () => {
  assert.match(page, /selectedRace\.clusters\.length >= 2/)
  assert.match(page, /clusterId: clusterId \? Number\(clusterId\) : undefined/)
  assert.match(page, /setClusterId\(''\)/)
  assert.match(page, /не меняется от фильтров или сортировки/)
})

test('public results always send a concrete race and never offer all starts', () => {
  assert.match(page, /raceId: Number\(raceId\)/)
  assert.match(page, /if \(!raceId\) return/)
  assert.doesNotMatch(page, /Все старты/)
})

test('sport format selector is hidden for one format and scopes the race selector', () => {
  assert.match(page, /publicFormats\.length > 1/)
  assert.match(page, /selectedFormat && races\.length > 1/)
  assert.match(page, /useMemo\(\(\) => selectedFormat\?\.races \?\? \[\]/)
  assert.match(page, /selectionForSportFormat\(publicFormats, nextFormatId\)/)
  assert.match(page, /publicResultFormats\(event\.sportFormats\)/)
  assert.match(page, /initialProtocolSelection\(initialFormats\)/)
  assert.match(page, /publicFormats\.length === 0 \? 'Результаты ещё не опубликованы\.'/)
  assert.doesNotMatch(page, /Все старты/)
})

test('one public protocol embeds official achievements without a second award view', () => {
  assert.match(page, /hasOfficialStanding \? 'Официальный зачёт' : 'Место'/)
  assert.match(page, /RankingAchievements achievements=\{result\.rankingAchievements\}/)
  assert.doesNotMatch(page, /ProtocolTab|Наградной зачёт|api\.awards|result\.place/)
  assert.doesNotMatch(client, /\/awards|AwardStanding/)
})

test('achievement renderer supports none, one, or multiple backend achievements', () => {
  assert.match(ranking, /achievements\.length === 0/)
  assert.match(ranking, /achievements\.map/)
  assert.match(ranking, /achievement\.label/)
  assert.match(ranking, /achievement\.prize \? 'ranking-prize' : 'ranking-standing'/)
  assert.doesNotMatch(ranking, /\.filter\(/)
  assert.match(ranking, /ranking-empty.*—/s)
})

test('sorting offers user fields without the technical protocol-order option', () => {
  assert.doesNotMatch(page, /Порядок протокола|<option value="place">/)
  assert.match(page, /<option value="gunTime">Официальное время/)
  assert.match(page, /<option value="chipTime">Чистое время/)
  assert.match(page, /<option value="displayName">ФИО<\/option>/)
  assert.match(page, /<option value="bib">Стартовый номер<\/option>/)
  assert.match(page, /не меняется от фильтров или сортировки/)
})

test('ranking basis drives standing badge and column semantics', () => {
  assert.match(types, /'CHIP_TIME' \| 'GUN_TIME' \| 'NONE'/)
  assert.match(page, /rankingBasis === 'GUN_TIME' \? ' · Зачёт'/)
  assert.match(page, /rankingBasis === 'CHIP_TIME' \? ' · Зачёт'/)
  assert.match(page, /rankingBasis !== 'NONE'/)
  assert.match(page, /result\.displayPosition/)
  assert.match(page, /не является официальным спортивным местом/)
})

test('NONE hides official achievements in list and result details', () => {
  assert.match(page, /hasOfficialStanding \? <RankingAchievements[\s\S]*result\.displayPosition/)
  assert.match(details, /result\.rankingBasis !== 'NONE'/)
  assert.match(details, /result\.rankingBasis === 'GUN_TIME'/)
  assert.match(details, /result\.rankingBasis === 'CHIP_TIME'/)
})
