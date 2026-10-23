/* oxlint-disable react/set-state-in-effect -- remote server state is loaded from effects */
import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import type { AdminApi } from '../api'
import type { AdminUser, AdminUserRole } from '../types'
import { navigateAdmin } from '../router'
import { adminErrorMessage, adminUserRoleLabel } from '../utils'
import { AdminNotice, Drawer, Field, Loadable, StatusBadge } from '../components/AdminUi'

export function AdminUsersPage({ api }: { api: AdminApi }) {
  const [users, setUsers] = useState<AdminUser[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [createOpen, setCreateOpen] = useState(false)
  const load = useCallback(async () => {
    setLoading(true); setError(null)
    try { setUsers(await api.users()) }
    catch (reason) { setError(adminErrorMessage(reason)) }
    finally { setLoading(false) }
  }, [api])
  useEffect(() => { void load() }, [load])

  return <>
    <div className="admin-page-heading"><div><p className="admin-eyebrow">Доступ</p><h1>Пользователи</h1><p>Учётные записи организаторов и хронометражистов.</p></div><button className="admin-button-primary" type="button" onClick={() => setCreateOpen(true)}>+ Добавить пользователя</button></div>
    <Loadable loading={loading} error={error}>
      <div className="admin-table-wrap"><table className="admin-table admin-users-table"><thead><tr><th>Пользователь</th><th>Логин</th><th>Роль</th><th>Статус</th><th>Мероприятий</th></tr></thead><tbody>
        {users.map((user) => <tr className="admin-event-row" key={user.id} role="link" tabIndex={0} onClick={() => navigateAdmin(`/admin/users/${user.id}`)} onKeyDown={(event) => {
          if (event.key !== 'Enter' && event.key !== ' ') return
          event.preventDefault(); navigateAdmin(`/admin/users/${user.id}`)
        }}><td><strong>{user.displayName}</strong></td><td>{user.username}</td><td>{adminUserRoleLabel(user.role)}</td><td><StatusBadge value={user.active ? 'ACTIVE' : 'INACTIVE'} /></td><td>{user.role === 'ORGANIZER' ? 'Все' : user.eventCount}</td></tr>)}
      </tbody></table></div>
    </Loadable>
    {createOpen && <CreateUserDrawer api={api} onClose={() => setCreateOpen(false)} onCreated={(created) => {
      setCreateOpen(false); navigateAdmin(`/admin/users/${created.id}`)
    }} />}
  </>
}

function CreateUserDrawer({ api, onClose, onCreated }: {
  api: AdminApi; onClose: () => void; onCreated: (user: AdminUser) => void
}) {
  const [displayName, setDisplayName] = useState('')
  const [username, setUsername] = useState('')
  const [role, setRole] = useState<AdminUserRole>('TIMEKEEPER')
  const [password, setPassword] = useState('')
  const [active, setActive] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const submit = async (event: FormEvent) => {
    event.preventDefault(); setBusy(true); setError(null)
    try { onCreated(await api.createUser({ displayName, username, role, password, active })) }
    catch (reason) { setError(adminErrorMessage(reason)) }
    finally { setBusy(false) }
  }
  return <Drawer title="Новый пользователь" onClose={onClose}><form className="admin-form" onSubmit={submit}>
    {error && <AdminNotice tone="danger">{error}</AdminNotice>}
    <Field label="Имя"><input required maxLength={255} value={displayName} onChange={(event) => setDisplayName(event.target.value)} /></Field>
    <Field label="Логин"><input required maxLength={160} autoComplete="username" value={username} onChange={(event) => setUsername(event.target.value)} /></Field>
    <Field label="Роль"><select value={role} onChange={(event) => setRole(event.target.value as AdminUserRole)}><option value="ORGANIZER">Организатор</option><option value="TIMEKEEPER">Хронометражист</option></select></Field>
    <Field label="Пароль" hint="Минимум 10 символов"><input required minLength={10} maxLength={200} type="password" autoComplete="new-password" value={password} onChange={(event) => setPassword(event.target.value)} /></Field>
    <label className="admin-check"><input type="checkbox" checked={active} onChange={(event) => setActive(event.target.checked)} /><span>Учётная запись активна</span></label>
    <div className="admin-form-actions"><button className="admin-button-primary" disabled={busy}>{busy ? 'Создаём…' : 'Создать'}</button><button className="admin-button-secondary" type="button" onClick={onClose}>Отмена</button></div>
  </form></Drawer>
}
