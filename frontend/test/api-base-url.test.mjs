import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import { apiUrl, resolveApiRoot } from '../src/api/baseUrl.ts'

const [publicClient, adminClient, directUpload] = await Promise.all([
  readFile(new URL('../src/api/client.ts', import.meta.url), 'utf8'),
  readFile(new URL('../src/admin/api.ts', import.meta.url), 'utf8'),
  readFile(new URL('../src/api/directUpload.ts', import.meta.url), 'utf8'),
])

test('API URL falls back to the same-origin Vite proxy for local development', () => {
  assert.equal(resolveApiRoot(undefined), '/api')
  assert.equal(resolveApiRoot('  '), '/api')
  assert.equal(apiUrl('/events', ''), '/api/events')
})

test('Cloudflare production base URL targets the Render API', () => {
  const renderUrl = 'https://sports-results-api.onrender.com'
  assert.equal(resolveApiRoot(renderUrl), `${renderUrl}/api`)
  assert.equal(apiUrl('/events', renderUrl), `${renderUrl}/api/events`)
})

test('API base normalization avoids duplicate slashes and duplicate api paths', () => {
  assert.equal(resolveApiRoot('https://api.example.test/'), 'https://api.example.test/api')
  assert.equal(resolveApiRoot('https://api.example.test/api'), 'https://api.example.test/api')
  assert.equal(apiUrl('admin/events', 'https://api.example.test/'), 'https://api.example.test/api/admin/events')
})

test('public, admin and XLSX requests share the API URL builder while direct uploads use signed URLs', () => {
  assert.match(publicClient, /fetch\(apiUrl\(path\)/)
  assert.match(adminClient, /fetch\(apiUrl\(path\)/)
  assert.match(adminClient, /apiUrl\('\/admin\/result-issue-requests\/export\/xlsx'\)/)
  assert.doesNotMatch(`${publicClient}\n${adminClient}`, /VITE_API_ROOT|localhost|127\.0\.0\.1/)
  assert.match(directUpload, /xhr\.open\(request\.method, request\.url/)
})
