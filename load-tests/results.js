import http from 'k6/http'
import { check } from 'k6'
import { Counter, Rate, Trend } from 'k6/metrics'

const baseUrl = __ENV.BASE_URL || 'http://127.0.0.1:8081'
const eventId = __ENV.EVENT_ID
const raceId = __ENV.RACE_ID
const categoryId = __ENV.CATEGORY_ID

if (!eventId || !raceId || !categoryId) {
  throw new Error('EVENT_ID, RACE_ID and CATEGORY_ID are required')
}

const duration = __ENV.DURATION || '30s'
const vus = Number(__ENV.VUS || 12)
const labels = ['a', 'b', 'c', 'd', 'e', 'f']
const timings = Object.fromEntries(labels.map((label) => [label, new Trend(`scenario_${label}_duration`, true)]))
const errors = Object.fromEntries(labels.map((label) => [label, new Rate(`scenario_${label}_errors`)]))
const requests = Object.fromEntries(labels.map((label) => [label, new Counter(`scenario_${label}_requests`)]))

export const options = {
  summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
  scenarios: {
    public_protocol: { executor: 'constant-vus', vus, duration, gracefulStop: '5s' },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1000'],
  },
}

function request(label, path) {
  const response = http.get(`${baseUrl}${path}`, { tags: { scenario_name: label } })
  const ok = check(response, { [`${label}: status 200`]: (result) => result.status === 200 })
  timings[label].add(response.timings.duration)
  errors[label].add(!ok)
  requests[label].add(1)
}

export default function () {
  request('a', `/api/events/${eventId}/results?page=0&size=50`)
  request('b', `/api/events/${eventId}/results?raceId=${raceId}&gender=female&sort=place&direction=asc&page=0&size=50`)
  request('c', `/api/events/${eventId}/results?raceId=${raceId}&categoryId=${categoryId}&sort=place&direction=asc&page=0&size=50`)
  request('d', `/api/events/${eventId}/results?name=${encodeURIComponent('иван петров 4242')}&page=0&size=50`)
  request('e', `/api/events/${eventId}/results?bib=042424&page=0&size=50`)
  request('f', '/api/events?year=2027&city=%D0%95%D0%BA%D0%B0%D1%82%D0%B5%D1%80%D0%B8%D0%BD%D0%B1%D1%83%D1%80%D0%B3&date=2027-06-15&page=0&size=50')
}
