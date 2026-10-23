import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const source = async (path) => readFile(new URL(path, import.meta.url), 'utf8')
const [app, api, events, users, user, types, css, eventPage, coreTabs] = await Promise.all([
  source('../src/admin/AdminApp.tsx'),
  source('../src/admin/api.ts'),
  source('../src/admin/pages/AdminEventsPage.tsx'),
  source('../src/admin/pages/AdminUsersPage.tsx'),
  source('../src/admin/pages/AdminUserPage.tsx'),
  source('../src/admin/types.ts'),
  source('../src/admin/admin.css'),
  source('../src/admin/pages/AdminEventPage.tsx'),
  source('../src/admin/pages/EventCoreTabs.tsx'),
])

test('current identity drives role-aware navigation and event-list wording', () => {
  assert.match(api, /me:\s*\(\)\s*=>\s*request<CurrentAdminUser>\('\/admin\/me'\)/)
  assert.match(types, /AdminUserRole = 'ORGANIZER' \| 'TIMEKEEPER'/)
  assert.match(app, /currentUser\.role === 'ORGANIZER'/)
  assert.match(app, /organizer \? 'Мероприятия' : 'Мои мероприятия'/)
  assert.match(events, /currentUser\.role === 'ORGANIZER'/)
  assert.match(events, /organizer \? 'Мероприятия' : 'Мои мероприятия'/)
  assert.match(events, /\{organizer && <section className="admin-events-create-actions"/)
})

test('organizer-only navigation and routes stay hidden from timekeepers', () => {
  for (const path of ['/admin/templates', '/admin/users', '/admin/support', '/admin/events/new', '/admin/events/bulk']) {
    assert.ok(app.includes(`location.pathname.startsWith('${path}')`) || app.includes(`location.pathname === '${path}'`))
  }
  assert.match(app, /!organizer && organizerOnlyRoute/)
  assert.match(app, /<AccessDeniedPage timekeeper=\{!organizer\}/)
  assert.match(app, /К моим мероприятиям/)
  assert.match(app, /\{organizer && <>.*Шаблоны.*Пользователи.*Журнал обращений/s)
})

test('403 has a dedicated access-denied path and does not use the logout callback', () => {
  assert.match(api, /if \(response\.status === 403\) onForbidden\?\.\(\)/)
  assert.match(api, /notifyUnauthorized\(response\.status, onUnauthorized\)/)
  assert.match(app, /createAdminApi\(credentials, onLogout, \(\) => setForbidden\(true\)\)/)
  assert.match(app, /<h1>Нет доступа<\/h1>/)
  assert.doesNotMatch(api, /response\.status === 403[^\n]*onUnauthorized/)
})

test('users UI exposes localized roles without returning password data', () => {
  assert.match(users, /<h1>Пользователи<\/h1>/)
  assert.match(users, /Добавить пользователя/)
  assert.match(users, /Организатор/)
  assert.match(users, /Хронометражист/)
  assert.match(user, /Новый пароль/)
  assert.match(user, /Пароль не отображается и не возвращается API/)
  assert.doesNotMatch(types, /passwordHash/)
  assert.doesNotMatch(types, /AdminUser[^}]*password:/s)
})

test('timekeeper assignments support search, persistent selection and save', () => {
  assert.match(user, /<h2>Доступ к мероприятиям<\/h2>/)
  assert.match(user, /api\.events\(\{ name: eventSearch, page: 0, size: 100 \}\)/)
  assert.match(user, /new Set\(access\.eventIds\)/)
  assert.match(user, /setSelectedEvents\(\(current\) =>/)
  assert.match(user, /api\.replaceUserEvents\(userId, \[\.\.\.selectedEvents\]\)/)
  assert.match(user, /Организатор имеет доступ ко всем мероприятиям/)
  assert.match(css, /\.admin-user-event-options[^}]*overflow-y:\s*auto/)
})

test('users API covers CRUD, passwords and assignment replacement', () => {
  for (const contract of [
    /users:\s*\(\)\s*=>/,
    /user:\s*\(userId:/,
    /createUser:/,
    /updateUser:/,
    /updateUserPassword:/,
    /userEvents:/,
    /replaceUserEvents:/,
  ]) assert.match(api, contract)
  assert.match(api, /method: 'PUT'/)
  assert.match(api, /eventIds/)
})

test('assigned Event details do not request organizer-only templates for a timekeeper', () => {
  assert.match(eventPage, /currentUser\.role === 'ORGANIZER' \? api\.eventSeries\(\) : Promise\.resolve\(\[\]\)/)
  assert.match(eventPage, /canChangeSeries=\{currentUser\.role === 'ORGANIZER'\}/)
  assert.match(coreTabs, /canChangeSeries\s*\? <select/)
  assert.match(coreTabs, /<input value=\{event\.eventSeriesName\} readOnly/)
})
