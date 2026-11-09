import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const [indexSource, appSource, appealSource, sharedPageSource, sharedApiSource, journalSource, adminApiSource] = await Promise.all([
  readFile(new URL('../index.html', import.meta.url), 'utf8'),
  readFile(new URL('../src/App.tsx', import.meta.url), 'utf8'),
  readFile(new URL('../src/pages/AppealPage.tsx', import.meta.url), 'utf8'),
  readFile(new URL('../src/pages/SharedJournalPage.tsx', import.meta.url), 'utf8'),
  readFile(new URL('../src/sharedJournalApi.ts', import.meta.url), 'utf8'),
  readFile(new URL('../src/admin/pages/AdminJournalPage.tsx', import.meta.url), 'utf8'),
  readFile(new URL('../src/admin/api.ts', import.meta.url), 'utf8'),
])

test('universal appeal route has explicit loading, error and empty states', () => {
  assert.match(appSource, /\/appeal/)
  assert.match(appealSource, /api\.appealEvents/)
  assert.match(appealSource, /Загружаем мероприятия/)
  assert.match(appealSource, /Не удалось загрузить мероприятия/)
  assert.match(appealSource, /Сейчас нет мероприятий с открытым приёмом обращений/)
})

test('appeal flow selects a single city automatically and asks for a multi-city choice', () => {
  assert.match(appealSource, /group\?\.cities\.length === 1 \? group\.cities\[0\]\.eventSlug : ''/)
  assert.match(appealSource, /selectedGroup && selectedGroup\.cities\.length > 1/)
  assert.match(appealSource, /Выберите город/)
})

test('appeal flow reuses the established inquiry and correction components', () => {
  assert.match(appealSource, /<ResultInquiryPanel/)
  assert.match(appealSource, /<ResultIssueDialog/)
  assert.match(appealSource, /kind="RESULT_CORRECTION"/)
  assert.match(appealSource, /api\.resultInquiry/)
  assert.match(appealSource, /api\.verifyResultInquiry/)
})

test('shared grant exchange is a POST and session requests use only the short-lived session header', () => {
  assert.match(sharedApiSource, /exchangeJournalShare\(token: string\)[\s\S]*request\('\/exchange', null, \{ method: 'POST'/)
  assert.match(sharedApiSource, /json\(\{ token \}\)/)
  assert.match(sharedApiSource, /if \(sessionToken\) headers\.set\('X-Journal-Share-Session', sessionToken\)/)
  assert.match(sharedApiSource, /cache: 'no-store'/)
})

test('grant token is removed from the URL and cannot be exchanged twice after state update', () => {
  assert.match(sharedPageSource, /exchangeStarted = useRef\(false\)/)
  assert.match(sharedPageSource, /if \(exchangeStarted\.current\) return/)
  const cleanup = sharedPageSource.indexOf("window.history.replaceState({}, '', '/journal-share')")
  const exchange = sharedPageSource.indexOf('exchangeJournalShare(grantToken)')
  assert.ok(cleanup >= 0 && cleanup < exchange, 'the token-bearing URL must be replaced before exchange can fail')
  assert.match(sharedPageSource, /sessionStorage\.removeItem\(TOKEN_KEY\)[\s\S]*sessionStorage\.removeItem\(CONTEXT_KEY\)[\s\S]*exchangeJournalShare/)
  assert.match(sharedPageSource, /sessionStorage\.setItem\(TOKEN_KEY, exchange\.sessionToken\)/)
  assert.doesNotMatch(sharedPageSource, /localStorage/)
})

test('referrer policy is applied before favicon and application resources are discovered', () => {
  const policy = indexSource.indexOf('<meta name="referrer" content="no-referrer" />')
  const favicon = indexSource.indexOf('<link rel="icon"')
  const application = indexSource.indexOf('<script type="module"')
  assert.ok(policy >= 0 && policy < favicon && policy < application)
})

test('revoked or expired shared sessions fail closed', () => {
  assert.match(sharedPageSource, /Ссылка недействительна, истекла или была отозвана/)
  assert.match(sharedPageSource, /sessionStorage\.removeItem\(TOKEN_KEY\)/)
  assert.match(sharedPageSource, /Сеанс доступа завершён/)
})

test('shared journal preserves the table and removes admin-only navigation and assignee directory', () => {
  assert.match(journalSource, /<table className="admin-table admin-journal-table"/)
  assert.match(journalSource, /\{!shared && <Field label="Мероприятие"/)
  assert.match(journalSource, /\{!shared && <td><select aria-label=\{`Ответственный/)
  assert.match(journalSource, /\{!shared && <>[\s\S]*?Доступ по ссылке/)
  assert.doesNotMatch(sharedApiSource, /adminUsers|users:\s*\(|apiUrl\(`\/admin/)
})

test('VIEW, EDIT and synchronization permissions independently control mutations', () => {
  assert.match(journalSource, /const editable = !shared \|\| shared\.permission === 'EDIT'/)
  assert.match(journalSource, /const canSync = !shared \|\| shared\.canSyncNow/)
  assert.match(journalSource, /disabled=\{!editable \|\| busyIssue/)
  assert.match(journalSource, /\{canSync && <button[\s\S]*?Синхронизировать сейчас/)
})

test('admin can create, copy, inspect and revoke scoped links without a recoverable token list', () => {
  assert.match(journalSource, /Доступ к журналу по ссылке/)
  assert.match(journalSource, /cityScope: cityScope \|\| null/)
  assert.match(journalSource, /permission === 'EDIT' && canSyncNow/)
  assert.match(journalSource, /type="number" min="1" max="365"/)
  assert.match(journalSource, /lifetimeDays: noExpiry \? null : lifetimeValue/)
  assert.match(journalSource, /Без срока действия/)
  assert.match(journalSource, /navigator\.clipboard\.writeText\(createdUrl\)/)
  assert.match(journalSource, /api\.revokeJournalShare\(id\)/)
  assert.match(journalSource, /Ссылка является секретом и показывается только после создания/)
  assert.doesNotMatch(adminApiSource, /rawToken/)
})

test('shared API exposes no admin login and sends no cacheable authorization URL', () => {
  assert.doesNotMatch(sharedApiSource, /Authorization|Basic|credentials/)
  assert.match(sharedApiSource, /cache: 'no-store'/)
  assert.match(sharedApiSource, /X-Journal-Share-Session/)
})
