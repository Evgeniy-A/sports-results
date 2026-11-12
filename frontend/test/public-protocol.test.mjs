import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import {
  activeRankingAchievements,
  genderAchievementClass,
  publicStandingPresentation,
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

test('participant search stays primary while gender uses three compact choices', () => {
  assert.match(page, /className="result-primary-filters"/)
  assert.match(page, /aria-label="Поиск по ФИО"/)
  assert.match(page, /aria-label="Точный стартовый номер"/)
  assert.match(page, /aria-label="Пол участника"/)
  assert.match(page, /aria-pressed=\{gender === ''\}[\s\S]*Все участники/)
  assert.match(page, /aria-pressed=\{gender === 'male'\}[\s\S]*Мужчины/)
  assert.match(page, /aria-pressed=\{gender === 'female'\}[\s\S]*Женщины/)
  assert.doesNotMatch(page, /<select aria-label="Пол"/)
})

test('secondary filters are grouped without changing their backend values', () => {
  assert.match(page, /<details className="advanced-filters">/)
  assert.match(page, /<summary>Расширенные фильтры<\/summary>/)
  assert.match(page, /setClusterId\(changeEvent\.target\.value\)/)
  assert.match(page, /selectedRace\.rules\?\.categoryEnabled/)
  assert.match(page, /setStatus\(changeEvent\.target\.value\)/)
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

test('one public protocol embeds official achievements without a second award view', () => {
  assert.match(page, /showStandingColumn && <th>\{standingColumnTitle\}<\/th>/)
  assert.match(page, /showStandingColumn && <td className="ranking-cell">/)
  assert.match(page, /RankingAchievements achievements=\{result\.rankingAchievements\} mode=\{rankingMode\}/)
  assert.doesNotMatch(page, /ProtocolTab|Наградной зачёт|api\.awards|result\.place/)
  assert.doesNotMatch(client, /\/awards|AwardStanding/)
})

test('results workspace keeps appeal visible and avoids repeating the selected race in every row', () => {
  assert.match(page, /className="results-appeal-link" href="\/appeal"/)
  assert.match(page, /Есть вопрос по результату\?/)
  assert.doesNotMatch(page, /<th>Старт \/ дистанция<\/th>/)
  assert.doesNotMatch(page, /<td>\{selectedRace\.name\}<\/td>/)
  assert.match(page, /№ \{result\.bib \?\? '—'\} · \{selectedRace\.name\}/)
})

test('primary standing mode and selected category independently control official standing visibility', () => {
  const all = publicStandingPresentation('ALL', false, '')
  const byGender = publicStandingPresentation('BY_GENDER', false, '')
  const none = publicStandingPresentation('NONE', false, '')
  const categoryOverview = publicStandingPresentation('NONE', true, '')
  const selectedCategory = publicStandingPresentation('NONE', true, '42')

  assert.equal(all.primaryStandingEnabled, true)
  assert.equal(all.showStanding, true)
  assert.equal(all.mode, 'PRIMARY')
  assert.equal(byGender.primaryStandingEnabled, true)
  assert.equal(byGender.showStanding, true)
  assert.equal(none.showStanding, false)
  assert.equal(categoryOverview.showStanding, false)
  assert.equal(selectedCategory.primaryStandingEnabled, false)
  assert.equal(selectedCategory.categoryStandingSelected, true)
  assert.equal(selectedCategory.showStanding, true)
  assert.equal(selectedCategory.mode, 'CATEGORY')
  assert.equal(selectedCategory.title, 'Место в категории')
  assert.doesNotMatch(page, /absolutePrizePlaces/)
})

test('no current standing removes desktop and mobile achievement containers instead of showing a fake place', () => {
  assert.match(page, /\{showStandingColumn && <th>\{standingColumnTitle\}<\/th>\}<th>Стартовый №<\/th>/)
  assert.match(page, /\{showStandingColumn && <td className="ranking-cell"><RankingAchievements/)
  assert.match(page, /\{showStandingColumn && <span className="card-achievements"><RankingAchievements/)
  assert.doesNotMatch(page, /result\.displayPosition/)
  assert.doesNotMatch(page, /Место \{result\.displayPosition/)
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
  assert.match(page, /gender: gender \|\| undefined/)
  assert.doesNotMatch(page, /Promise\.all[\s\S]*api\.results/)
})

test('sorting offers user fields without the technical protocol-order option', () => {
  assert.doesNotMatch(page, /Порядок протокола|<option value="place">/)
  assert.match(page, /<option value="gunTime">Официальное время/)
  assert.match(page, /<option value="chipTime">Чистое время/)
  assert.match(page, /<option value="displayName">ФИО<\/option>/)
  assert.match(page, /<option value="bib">Стартовый номер<\/option>/)
  assert.match(page, /не меняется от фильтров или сортировки/)
})

test('ranking basis drives scoring time only after the current standing mode is enabled', () => {
  assert.match(types, /'CHIP_TIME' \| 'GUN_TIME' \| 'NONE'/)
  assert.equal(scoringTimeHeading('GUN_TIME', 'GUN_TIME'), '⏱ Зачёт (офиц. время)')
  assert.equal(scoringTimeHeading('GUN_TIME', 'CHIP_TIME'), 'Чистое время')
  assert.equal(scoringTimeHeading('CHIP_TIME', 'CHIP_TIME'), '⏱ Зачёт (чистое время)')
  assert.equal(scoringTimeHeading('CHIP_TIME', 'GUN_TIME'), 'Официальное время')
  assert.match(page, /activeRankingBasis = showStandingColumn \? \(rankingBasis \?\? 'NONE'\) : 'NONE'/)
  assert.match(page, /scoringTimeHeading\(activeRankingBasis, 'GUN_TIME'\)/)
  assert.match(page, /scoringTimeHeading\(activeRankingBasis, 'CHIP_TIME'\)/)
  assert.match(page, /primaryStandingMode/)
  assert.match(page, /categoryStandingSelected/)
})

test('result details use the same resolved standing visibility, title, mode and basis as the list', () => {
  assert.match(page, /showOfficialStanding=\{showStandingColumn\}/)
  assert.match(page, /officialStandingTitle=\{standingColumnTitle\}/)
  assert.match(page, /activeRankingBasis=\{activeRankingBasis\}/)
  assert.match(details, /showOfficialStanding && <div className="detail-ranking">/)
  assert.match(details, /<h3>\{officialStandingTitle\}<\/h3>/)
  assert.match(details, /activeRankingBasis === 'GUN_TIME'/)
  assert.match(details, /activeRankingBasis === 'CHIP_TIME'/)
})
