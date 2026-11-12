import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import {
  activeRankingAchievements,
  genderAchievementClass,
  hasOfficialStanding,
  officialStandingHeading,
  scoringTimeHeading,
} from '../src/utils/rankingPresentation.ts'

const page = await readFile(new URL('../src/components/EventResults.tsx', import.meta.url), 'utf8')
const client = await readFile(new URL('../src/api/client.ts', import.meta.url), 'utf8')
const ranking = await readFile(new URL('../src/components/RankingAchievements.tsx', import.meta.url), 'utf8')
const details = await readFile(new URL('../src/components/ResultDetailsDialog.tsx', import.meta.url), 'utf8')
const types = await readFile(new URL('../src/api/types.ts', import.meta.url), 'utf8')
const css = await readFile(new URL('../src/App.css', import.meta.url), 'utf8')

test('public protocol has no gender column or ranking selector', () => {
  assert.doesNotMatch(page, /<th>Пол<\/th>/)
  assert.doesNotMatch(page, /Основа протокола|Gun Time|Chip Time/)
  assert.match(page, /Официальное время/)
  assert.match(page, /Чистое время/)
})

test('both public time columns render backend leader gaps without client-side ranking math', () => {
  assert.match(types, /gunLeaderGapSeconds: number \| null/)
  assert.match(types, /chipLeaderGapSeconds: number \| null/)
  assert.match(page, /gapSeconds=\{result\.gunLeaderGapSeconds\}/)
  assert.match(page, /gapSeconds=\{result\.chipLeaderGapSeconds\}/)
  assert.match(page, /formatLeaderGap\(gapSeconds\)/)
  assert.doesNotMatch(page, /Math\.min\(|reduce\([^)]*Time/)
  assert.match(css, /\.leader-gap[^}]*color:\s*var\(--muted\)/)
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
  assert.match(page, /selectedRace\.rules\.availableCategories\.map/)
  assert.doesNotMatch(page, /selectedRace\.rules\.categories\.map/)
  assert.match(types, /availableCategories: CategoryRule\[\]/)
  assert.match(types, /categoryRankingBasis: RankingBasis/)
  assert.match(types, /rankingBasis: RankingBasis/)
  assert.match(page, /selectedCategory\?\.rankingBasis/)
  assert.match(page, /defaultPublicResultSort\(nextCategory\?\.rankingBasis/)
  assert.match(page, /showCategoryColumn && <th>Категория<\/th>/)
  assert.match(page, /categoryEnabled=\{showCategoryColumn\}/)
})

test('category filter labels the actual category scoring time without changing backend places', () => {
  assert.match(page, /selectedCategory = categories\.find/)
  assert.match(page, /Зачёт по чистому времени/)
  assert.match(page, /Зачёт по официальному времени/)
  assert.match(page, /inside|внутри выбранной возрастной категории/)
  assert.doesNotMatch(page, /set.*Place|calculate.*Place/)
})

test('result detail category follows race policy without rendering an empty placeholder', () => {
  assert.match(details, /publicCategoryName\(categoryEnabled, result\.category\)/)
  assert.match(details, /\{categoryName && <div><dt>Категория<\/dt><dd>\{categoryName\}<\/dd><\/div>\}/)
  assert.doesNotMatch(details, /result\.category\?\.name \?\? '—'/)
  assert.match(details, /<dt>Старт<\/dt>/)
  assert.match(details, /<dd>\{startName\}<\/dd>/)
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

test('public results use only the flat Race-only contract and one Start selector', () => {
  assert.match(page, /publicResultRaces\(event\.races\)/)
  assert.match(page, /initialRaceSelection\(initialRaces\)/)
  assert.match(page, /races\.length > 1/)
  assert.match(page, /<span>Старт<\/span>/)
  assert.match(page, /className="selection-buttons" role="group" aria-label="Старт"/)
  assert.match(page, /races\.length === 0 \? 'Результаты ещё не опубликованы\.'/)
  assert.doesNotMatch(page, /event\.sportFormats|sportFormatId|format-select|Все старты/)
})

test('one public protocol embeds only the configured official standing without a second award view', () => {
  assert.match(page, /hasOfficialStanding && <th>\{standingHeading\}<\/th>/)
  assert.match(page, /RankingAchievements achievements=\{result\.rankingAchievements\} mode=\{rankingMode\}/)
  assert.doesNotMatch(page, /ProtocolTab|Наградной зачёт|api\.awards|result\.place/)
  assert.doesNotMatch(client, /\/awards|AwardStanding/)
})

test('achievement renderer keeps only the active standing without recalculating places', () => {
  const achievements = [
    { place: 3, type: 'GENDER', code: 'male', name: 'Мужчины', label: '3 место · мужчины', prize: true },
    { place: 1, type: 'CATEGORY', code: '30-39', name: '30–39 · мужчины', label: '1 место · 30–39 · мужчины', prize: true },
  ]
  assert.deepEqual(activeRankingAchievements(achievements, 'PRIMARY'), [achievements[0]])
  assert.deepEqual(activeRankingAchievements(achievements, 'CATEGORY'), [achievements[1]])
  assert.equal(activeRankingAchievements([], 'PRIMARY').length, 0)
  assert.match(ranking, /activeRankingAchievements\(achievements, mode\)/)
  assert.match(ranking, /activeAchievements\.map/)
  assert.match(ranking, /achievement\.label/)
  assert.match(ranking, /achievement\.prize \? 'ranking-prize' : 'ranking-standing'/)
  assert.match(ranking, /ranking-empty.*—/s)
})

test('sex all keeps gender achievements together and distinguishes badge colors', () => {
  const male = { place: 1, type: 'GENDER', code: 'male', name: 'Мужчины', label: '1 место · мужчины', prize: true }
  const female = { place: 1, type: 'GENDER', code: 'female', name: 'Женщины', label: '1 место · женщины', prize: true }
  assert.equal(genderAchievementClass(male), 'ranking-gender-male')
  assert.equal(genderAchievementClass(female), 'ranking-gender-female')
  assert.match(css, /\.ranking-gender-male[^}]*background:\s*#e8ebef/)
  assert.match(css, /\.ranking-gender-female[^}]*background:\s*#e5f0f8/)
  assert.doesNotMatch(page, /setGender\('male'\).*setGender\('female'\)/s)
})

test('sorting offers user fields without the technical protocol-order option', () => {
  assert.doesNotMatch(page, /Порядок протокола|<option value="place">/)
  assert.match(page, /<option value="gunTime">Официальное время/)
  assert.match(page, /<option value="chipTime">Чистое время/)
  assert.match(page, /<option value="displayName">ФИО<\/option>/)
  assert.match(page, /<option value="bib">Стартовый номер<\/option>/)
  assert.match(page, /не меняется от фильтров или сортировки/)
})

test('ranking basis marks scoring time only when the current standing is active', () => {
  assert.match(types, /'CHIP_TIME' \| 'GUN_TIME' \| 'NONE'/)
  assert.equal(scoringTimeHeading('GUN_TIME', 'GUN_TIME'), '⏱ Зачёт (офиц. время)')
  assert.equal(scoringTimeHeading('GUN_TIME', 'CHIP_TIME'), 'Чистое время')
  assert.equal(scoringTimeHeading('CHIP_TIME', 'CHIP_TIME'), '⏱ Зачёт (чистое время)')
  assert.equal(scoringTimeHeading('CHIP_TIME', 'GUN_TIME'), 'Официальное время')
  assert.equal(scoringTimeHeading('GUN_TIME', 'GUN_TIME', false), 'Официальное время')
  assert.equal(scoringTimeHeading('CHIP_TIME', 'CHIP_TIME', false), 'Чистое время')
  assert.match(page, /scoringTimeHeading\(rankingBasis \?\? 'NONE', 'GUN_TIME', hasOfficialStanding\)/)
  assert.match(page, /scoringTimeHeading\(rankingBasis \?\? 'NONE', 'CHIP_TIME', hasOfficialStanding\)/)
})

test('standing visibility follows primary standing mode or a selected category', () => {
  assert.equal(hasOfficialStanding('ALL', false), true)
  assert.equal(hasOfficialStanding('BY_GENDER', false), true)
  assert.equal(hasOfficialStanding('NONE', false), false)
  assert.equal(hasOfficialStanding('NONE', true), true)
  assert.equal(officialStandingHeading(false), 'Официальный зачёт')
  assert.equal(officialStandingHeading(true), 'Место в категории')
})

test('NONE without a selected category removes standing UI from list and details', () => {
  assert.match(page, /resolveOfficialStanding\(/)
  assert.match(page, /hasOfficialStanding && <th>\{standingHeading\}<\/th>/)
  assert.match(page, /hasOfficialStanding && <td className="ranking-cell">/)
  assert.match(page, /hasOfficialStanding && <span className="card-achievements">/)
  assert.doesNotMatch(page, /display-place">Место/)
  assert.match(page, /hasOfficialStanding=\{hasOfficialStanding\}/)
  assert.match(details, /hasOfficialStanding && <div className="detail-ranking">/)
  assert.match(details, /hasOfficialStanding && standingRankingBasis === 'GUN_TIME'/)
  assert.match(details, /hasOfficialStanding && standingRankingBasis === 'CHIP_TIME'/)
})

test('selected category uses the category heading and category scoring basis in details', () => {
  assert.match(page, /standingHeading=\{standingHeading\}/)
  assert.match(page, /standingRankingBasis=\{hasOfficialStanding \? rankingBasis \?\? 'NONE' : 'NONE'\}/)
  assert.match(details, /<h3>\{standingHeading\}<\/h3>/)
  assert.match(details, /standingRankingBasis/)
})
