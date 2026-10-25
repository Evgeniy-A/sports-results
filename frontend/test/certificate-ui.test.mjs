import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const details = await readFile(new URL('../src/components/ResultDetailsDialog.tsx', import.meta.url), 'utf8')
const dialog = await readFile(new URL('../src/components/CertificateDialogContent.tsx', import.meta.url), 'utf8')
const publicStyles = await readFile(new URL('../src/App.css', import.meta.url), 'utf8')
const client = await readFile(new URL('../src/api/client.ts', import.meta.url), 'utf8')
const adminPage = await readFile(new URL('../src/admin/pages/AdminEventPage.tsx', import.meta.url), 'utf8')
const adminTab = await readFile(new URL('../src/admin/pages/EventCertificatesTab.tsx', import.meta.url), 'utf8')
const adminApi = await readFile(new URL('../src/admin/api.ts', import.meta.url), 'utf8')

test('public result card only exposes certificate action from backend availability', () => {
  assert.match(details, /api\.certificates\(resultId/)
  assert.match(details, /certificates\?\.enabled && certificates\.templates\.length > 0/)
  assert.match(details, /Скачать сертификат/)
})

test('participant selects only template and typed field enums without spoofable values', () => {
  assert.match(dialog, /api\.generateCertificate\(resultId, templateId, fields\)/)
  assert.match(client, /JSON\.stringify\(\{ fields \}\)/)
  assert.doesNotMatch(dialog, /name:\s*|time:\s*|place:\s*/)
  assert.match(dialog, /ФИО или название команды.*мероприятие.*город.*дистанц/)
})

test('unavailable optional fields are disabled and errors are accessible', () => {
  assert.match(dialog, /disabled=\{!option\.available\}/)
  assert.match(dialog, /Нет данных для этого результата/)
  assert.match(dialog, /role="alert"/)
})

test('certificate choices keep native controls inside scoped selectable rows', () => {
  assert.match(dialog, /type="radio"/)
  assert.match(dialog, /type="checkbox"/)
  assert.match(dialog, /certificate-option-copy/)
  assert.match(dialog, /certificate-field-option/)
  assert.match(publicStyles, /\.certificate-template-options input\[type="radio"\]/)
  assert.match(publicStyles, /\.certificate-field-options input\[type="checkbox"\]/)
  assert.match(publicStyles, /\.certificate-option\.is-selected/)
  assert.match(publicStyles, /\.certificate-field-option\.is-disabled/)
})

test('generation handles a PDF blob as a download and reports capacity errors', () => {
  assert.match(client, /return response\.blob\(\)/)
  assert.match(dialog, /URL\.createObjectURL\(blob\)/)
  assert.match(dialog, /CERTIFICATE_CAPACITY_EXCEEDED/)
  assert.match(dialog, /Сервис занят/)
})

test('certificate configuration is event-scoped with settings, races, preview and CRUD', () => {
  assert.match(adminPage, /\['certificates', 'Сертификаты'\]/)
  assert.match(adminPage, /EventCertificatesTab/)
  assert.match(adminTab, /Разрешить сертификаты/)
  assert.match(adminTab, /Какие данные участник сможет добавить/)
  assert.match(adminTab, /RaceChecks/)
  assert.match(adminTab, /Предпросмотр/)
  assert.match(adminApi, /\/admin\/events\/\$\{eventId\}\/certificates\/settings/)
  assert.match(adminApi, /uploadCertificateTemplate/)
  assert.match(adminApi, /deleteCertificateTemplate/)
})

test('admin layout controls cover the three maintained placement areas', () => {
  assert.match(adminTab, /Имя участника/)
  assert.match(adminTab, /Мероприятие, город и старт/)
  assert.match(adminTab, /Выбранные данные/)
  assert.match(adminTab, /Координаты задаются в PDF-пунктах/)
})
