// Dev only - fills the local DB with the demo data from src/data/seed.ts. It goes through the real API,
// so all the backend rules still apply. Don't run it against production.
//
// Needs the Super Admin from the bootstrap. The repo is public, so users and passwords come from env vars:
//   SEED_SUPER_ADMIN_USERNAME, SEED_SUPER_ADMIN_PASSWORD (+ optional SEED_SUPER_ADMIN_NAME, SEED_SUPER_ADMIN_EMAIL)
//   SEED_ADMIN_USERNAME, SEED_ADMIN_PASSWORD, SEED_ADMIN_EMAIL (+ optional SEED_ADMIN_NAME)
//   SEED_API if the backend isn't on http://localhost:8080
// Then from frontend/: npx tsx scripts/seed-dev-data.ts
// Fine to run again, it skips whatever is already there.
import { randomUUID } from 'node:crypto'
import { createSeed } from '../src/data/seed'
import { addDays } from '../src/lib/dates'

const API = process.env.SEED_API ?? 'http://localhost:8080'

const SUPER_ADMIN = { username: need('SEED_SUPER_ADMIN_USERNAME'), displayName: process.env.SEED_SUPER_ADMIN_NAME, email: process.env.SEED_SUPER_ADMIN_EMAIL }
const ADMIN = { username: need('SEED_ADMIN_USERNAME'), displayName: process.env.SEED_ADMIN_NAME, email: need('SEED_ADMIN_EMAIL') }

const superAdminPassword = need('SEED_SUPER_ADMIN_PASSWORD')
const adminPassword = need('SEED_ADMIN_PASSWORD')

function need(name: string): string {
  const value = process.env[name]
  if (!value) {
    console.error(`Set ${name} first.`)
    process.exit(1)
  }
  return value
}

// fetch doesn't keep cookies, so we hold on to the session and XSRF-TOKEN ourselves
const jar = new Map<string, string>()

// eslint-disable-next-line @typescript-eslint/no-explicit-any
async function call(method: string, path: string, body?: unknown, form?: FormData): Promise<any> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (jar.size) headers.Cookie = [...jar].map(([k, v]) => `${k}=${v}`).join('; ')
  const xsrf = jar.get('XSRF-TOKEN')
  if (method !== 'GET' && xsrf) headers['X-XSRF-TOKEN'] = decodeURIComponent(xsrf)
  let payload: BodyInit | undefined = form
  if (!form && body !== undefined) {
    headers['Content-Type'] = 'application/json'
    payload = JSON.stringify(body)
  }
  const res = await fetch(API + path, { method, headers, body: payload, redirect: 'manual' })
  for (const c of res.headers.getSetCookie()) {
    const pair = c.split(';')[0]
    const i = pair.indexOf('=')
    const name = pair.slice(0, i)
    const value = pair.slice(i + 1)
    if (value) jar.set(name, value)
    else jar.delete(name)
  }
  const text = await res.text()
  if (!res.ok) throw new Error(`${method} ${path} -> ${res.status} ${text}`)
  return text ? JSON.parse(text) : undefined
}
const get = (path: string) => call('GET', path)
const post = (path: string, body?: unknown) => call('POST', path, body ?? {})
const put = (path: string, body: unknown) => call('PUT', path, body)

// tiny valid PDF so the agreement pages have something to download
const PDF = Buffer.from('%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n'
  + '3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 595 842]>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF\n')

const upper = (s: string) => s.toUpperCase().replace(/ /g, '_')

async function main() {
  const s = createSeed()

  await get('/api/v1/auth/csrf')
  const me = await post('/api/v1/auth/login', { username: SUPER_ADMIN.username, password: superAdminPassword })
  await get('/api/v1/auth/csrf') // new session, new token

  const self = await get(`/api/v1/users/${me.id}`)
  // leave the profile alone unless a name or email was given
  if (SUPER_ADMIN.displayName || SUPER_ADMIN.email) {
    await put(`/api/v1/users/${me.id}`, { displayName: SUPER_ADMIN.displayName ?? self.displayName, email: SUPER_ADMIN.email ?? self.email, version: self.version })
  }
  const found = await get(`/api/v1/users?q=${encodeURIComponent(ADMIN.username)}`)
  if (!found.content.some((u: { username: string }) => u.username === ADMIN.username.toLowerCase())) {
    await post('/api/v1/users', { username: ADMIN.username, displayName: ADMIN.displayName ?? ADMIN.username, email: ADMIN.email, password: adminPassword, role: 'ADMIN' })
  }
  console.log('users ok')

  const company = await get('/api/v1/settings/company')
  await put('/api/v1/settings/company', { ...s.settings.company, supplierCreditDays: s.settings.supplierCreditDays, version: company.version })
  const methods = new Map<string, number>((await get('/api/v1/settings/payment-methods')).map((m: { name: string; id: number }) => [m.name, m.id]))
  const units = new Map<string, number>((await get('/api/v1/settings/units')).map((u: { name: string; id: number }) => [u.name, u.id]))
  const categories = new Map<string, number>((await get('/api/v1/settings/expense-categories'))
    .map((c: { group: string; name: string; id: number }) => [`${c.group}/${c.name}`, c.id]))
  const method = (name: string) => methods.get(name) ?? fail(`payment method ${name}`)
  const unit = (name: string) => units.get(name) ?? fail(`unit ${name}`)

  const oldClients = await byKey('/api/v1/clients', 'name')
  const clientIds = new Map<string, number>()
  for (const c of s.clients) {
    const r = oldClients.get(c.name) ?? await post('/api/v1/clients', { name: c.name, kind: upper(c.kind), phone: c.phone, email: c.email, address: c.address, city: c.city, notes: c.notes })
    clientIds.set(c.id, r.id)
  }

  const oldProjects = await byKey('/api/v1/projects', 'name')
  const projectIds = new Map<string, number>()
  const installmentIds = new Map<string, number>()
  for (const p of s.projects) {
    const schedule = s.installments.filter((i) => i.projectId === p.id)
    const d = p.delay?.enabled ? p.delay : undefined
    const old = oldProjects.get(p.name)
    if (old) {
      projectIds.set(p.id, old.id)
      const saved: { id: number; description: string; dueDate: string }[] = await get(`/api/v1/projects/${old.id}/schedule`)
      for (const i of schedule) installmentIds.set(i.id, saved.find((x) => x.description === i.description && x.dueDate === i.dueDate)!.id)
      continue
    }
    const r = await post('/api/v1/projects', {
      name: p.name, type: upper(p.type), clientId: clientIds.get(p.clientId), location: p.location, description: p.description,
      contractValue: p.contractValue, startDate: p.startDate, expectedCompletion: p.expectedCompletion,
      // completed and on-hold ones start in progress so we can record their history; real status is set at the end
      status: p.status === 'Planning' ? 'PLANNING' : 'IN_PROGRESS', notes: p.notes,
      schedule: schedule.map((i) => ({ description: i.description, dueDate: i.dueDate, amount: i.amount })),
      delayTerms: d && {
        contractDeadline: d.contractDeadline, rule: upper(d.rule), ratePerDay: d.ratePerDay, percentPerWeek: d.percentPerWeek,
        fixedAmount: d.fixedAmount, graceDays: d.graceDays, maxDeduction: d.maxDeduction, notes: d.notes,
      },
    })
    projectIds.set(p.id, r.id)
    const saved: { id: number; description: string; dueDate: string; version: number }[] = await get(`/api/v1/projects/${r.id}/schedule`)
    for (const i of schedule) {
      const row = saved.find((x) => x.description === i.description && x.dueDate === i.dueDate) ?? fail(`installment ${i.id}`)
      installmentIds.set(i.id, row.id)
      if (i.billed) await post(`/api/v1/projects/${r.id}/installments/${row.id}/bill`, { billedOn: i.billedOn, version: row.version })
    }
    if (p.agreement) {
      const form = new FormData()
      form.append('file', new Blob([PDF], { type: 'application/pdf' }), p.agreement.fileName)
      await call('PUT', `/api/v1/projects/${r.id}/agreement`, undefined, form)
    }
  }
  const project = (id?: string) => (id ? projectIds.get(id) ?? fail(`project ${id}`) : undefined)
  console.log('clients, projects, schedules ok')

  const receiptsDone = (await get('/api/v1/other-income?size=1')).totalElements > 0
  for (const cp of receiptsDone ? [] : s.clientPayments) {
    const inst = s.installments.find((i) => i.id === cp.installmentId)!
    await post(`/api/v1/projects/${project(inst.projectId)}/installments/${installmentIds.get(inst.id)}/receipts`, {
      requestId: randomUUID(), paymentDate: cp.date, amount: cp.amount, paymentMethodId: method(cp.method), reference: cp.reference,
    })
  }
  for (const oi of receiptsDone ? [] : s.otherIncome) {
    const r = await post('/api/v1/other-income', { date: oi.date, description: oi.description, payer: oi.payer, amount: oi.amount, dueDate: oi.dueDate, reference: oi.reference })
    for (const rc of s.otherIncomeReceipts.filter((x) => x.incomeId === oi.id)) {
      await post(`/api/v1/other-income/${r.id}/receipts`, { requestId: randomUUID(), paymentDate: rc.date, amount: rc.amount, paymentMethodId: method(rc.method) })
    }
  }
  console.log('receipts, other income ok')

  const oldSuppliers = await byKey('/api/v1/suppliers', 'name')
  const supplierIds = new Map<string, number>()
  for (const sp of s.suppliers) {
    const r = oldSuppliers.get(sp.name) ?? await post('/api/v1/suppliers', { name: sp.name, contactPerson: sp.contactPerson, phone: sp.phone, email: sp.email, address: sp.address, supplies: sp.supplies, creditDays: sp.creditDays })
    supplierIds.set(sp.id, r.id)
  }
  const oldMaterials = await byKey('/api/v1/inventory/materials', 'sku')
  const materialIds = new Map<string, number>()
  for (const m of s.materials) {
    const r = oldMaterials.get(m.sku) ?? await post('/api/v1/inventory/materials', { sku: m.sku, name: m.name, category: m.category, unitId: unit(m.unit), defaultUnitCost: m.unitCost })
    materialIds.set(m.id, r.id)
  }

  type Step = { date: string; order: number; run: () => Promise<void> }
  const steps: Step[] = []
  const oldInvoices = await byKey('/api/v1/supplier-invoices', 'invoiceNo')
  for (const inv of s.supplierInvoices) {
    steps.push({
      date: inv.date, order: 0, run: async () => {
        const old = oldInvoices.get(inv.invoiceNo)
        const id = old?.id ?? (await post('/api/v1/supplier-invoices', {
          supplierId: supplierIds.get(inv.supplierId), invoiceNo: inv.invoiceNo, invoiceDate: inv.date, dueDate: inv.dueDate,
          allocation: inv.projectId ? 'PROJECT' : 'STOCK', projectId: project(inv.projectId), notes: inv.notes,
          lines: inv.lines.map((l) => ({ materialId: inv.projectId ? undefined : materialIds.get(l.materialId!), description: l.description, quantity: l.qty, unitId: unit(l.unit), unitPrice: l.unitPrice })),
        })).invoice.id
        if (old && old.paid > 0) return
        for (const pay of s.supplierPayments.filter((x) => x.invoiceId === inv.id)) {
          await post(`/api/v1/supplier-invoices/${id}/payments`, { requestId: randomUUID(), paymentDate: pay.date, amount: pay.amount, paymentMethodId: method(pay.method), reference: pay.reference })
        }
      },
    })
  }
  const posted = new Set<string>()
  for (const type of ['ISSUED', 'ADJUSTMENT']) {
    const page = await get(`/api/v1/inventory/movements?type=${type}&size=100`)
    for (const m of page.content) posted.add(`${m.materialId}|${m.date}|${type === 'ISSUED' ? m.reference ?? '' : ''}`)
  }
  const isPosted = (materialId: string, date: string, ref?: string) => posted.has(`${materialIds.get(materialId)}|${date}|${ref ?? ''}`)
  for (const mv of s.stockMovements) {
    if (mv.type === 'Issued' && !isPosted(mv.materialId, mv.date, mv.reference)) {
      steps.push({
        date: mv.date, order: 1, run: async () => {
          // the demo issues more than it bought in a few places and the backend won't go negative
          const m = await get(`/api/v1/inventory/materials/${materialIds.get(mv.materialId)}`)
          const quantity = Math.min(-mv.qty, m.quantityOnHand)
          if (quantity < -mv.qty) console.log(`  ${mv.reference}: only ${quantity} of ${-mv.qty} ${m.unit} ${m.name} in stock, issuing that`)
          if (quantity > 0) await post('/api/v1/inventory/issues', { requestId: randomUUID(), materialId: m.id, projectId: project(mv.projectId), date: mv.date, quantity, reference: mv.reference })
        },
      })
    } else if (mv.type === 'Adjustment' && mv.qty !== 0 && !isPosted(mv.materialId, mv.date)) {
      // a count with no difference isn't a movement on the backend
      steps.push({ date: mv.date, order: 1, run: () => post('/api/v1/inventory/adjustments', { requestId: randomUUID(), materialId: materialIds.get(mv.materialId), date: mv.date, quantityChange: mv.qty, reason: mv.reason, unitCost: mv.qty > 0 ? mv.unitCost : undefined }) })
    }
  }
  // invoices and issues go in date order, otherwise the FIFO costs come out wrong
  steps.sort((a, b) => a.date.localeCompare(b.date) || a.order - b.order)
  for (const step of steps) await step.run()
  console.log('suppliers, invoices, stock ok')

  const expensesDone = (await get('/api/v1/expenses?size=1')).totalElements > 0
  for (const e of expensesDone ? [] : s.expenses) {
    const group = e.scope === 'Company' ? 'COMPANY' : upper(e.group)
    const paid = s.expensePayments.filter((x) => x.expenseId === e.id)
    const [first, ...rest] = paid
    const r = await post('/api/v1/expenses', {
      date: e.date, scope: upper(e.scope), projectId: project(e.projectId), categoryId: categories.get(`${group}/${e.category}`) ?? fail(`category ${e.category}`),
      description: e.description, payee: e.payee, amount: e.amount, dueDate: e.dueDate, notes: e.notes, machineType: e.details?.Machine,
      initialPayment: first && { requestId: randomUUID(), paymentDate: first.date, amount: first.amount, paymentMethodId: method(first.method) },
    })
    for (const pay of rest) {
      await post(`/api/v1/expenses/${r.id}/payments`, { requestId: randomUUID(), paymentDate: pay.date, amount: pay.amount, paymentMethodId: method(pay.method) })
    }
  }
  console.log('expenses ok')

  const oldEmployees = await byKey('/api/v1/employees', 'name')
  const labourDone = oldEmployees.size > 0
  const employeeIds = new Map<string, number>()
  for (const w of s.employees) {
    const r = oldEmployees.get(w.name) ?? await post('/api/v1/employees', { name: w.name, role: w.role, phone: w.phone, dailyWage: w.dailyRate, overtimeRate: w.overtimeRate, joinedOn: w.joinedOn })
    employeeIds.set(w.id, r.id)
  }
  const days = [...new Set(s.attendance.map((a) => a.date))].sort()
  for (const day of days) {
    await put(`/api/v1/attendance/${day}`, {
      entries: s.attendance.filter((a) => a.date === day).map((a) => ({ employeeId: employeeIds.get(a.employeeId), status: upper(a.status), projectId: project(a.projectId), notes: a.notes })),
    })
  }
  // attendance replaces the whole day so resending is fine; overtime and advances would double up
  for (const ot of labourDone ? [] : s.overtime) {
    const r = await post('/api/v1/overtime', { employeeId: employeeIds.get(ot.employeeId), date: ot.date, projectId: project(ot.projectId), hours: ot.hours, hourlyRate: ot.rate, recordedBy: ot.recordedBy, notes: ot.notes })
    if (ot.approved) await put(`/api/v1/overtime/${r.id}/approval`, { approved: true, version: r.version })
  }
  for (const ad of labourDone ? [] : s.advances) {
    await post('/api/v1/payroll/advances', { requestId: randomUUID(), employeeId: employeeIds.get(ad.employeeId), paymentDate: ad.date, amount: ad.amount, paymentMethodId: method(ad.method), notes: ad.notes })
  }
  // pay the weeks the demo paid, with the backend's numbers. w5 and w6 are 10k short in the last one
  const short = new Set([employeeIds.get('w5'), employeeIds.get('w6')])
  for (let week = '2026-04-06'; week <= '2026-10-12'; week = addDays(week, 7)) {
    const w = await get(`/api/v1/payroll/weeks/${week}`)
    for (const line of w.lines) {
      const amount = week === '2026-10-12' && short.has(line.employeeId) ? line.remaining - 10_000 : line.remaining
      if (amount <= 0) continue
      await post(`/api/v1/payroll/weeks/${week}/employees/${line.employeeId}/payments`, { requestId: randomUUID(), paymentDate: w.weekEnd, amount, paymentMethodId: method('Cash') })
    }
  }
  for (const w of s.employees.filter((x) => x.status === 'Inactive')) {
    const e = await get(`/api/v1/employees/${employeeIds.get(w.id)}`)
    if (e.status !== 'INACTIVE') await put(`/api/v1/employees/${e.id}/status`, { status: 'INACTIVE', version: e.version })
  }
  console.log('labour and payroll ok')

  for (const [pid, rows] of Map.groupBy(s.commissions, (c) => c.projectId)) {
    const confirm = rows.every((c) => c.status === 'Confirmed')
    if ((await get(`/api/v1/projects/${project(pid)}/commissions`)).arrangements.length) continue
    const r = await put(`/api/v1/projects/${project(pid)}/commissions`, {
      confirm, acceptPolicyException: false,
      arrangements: rows.map((c) => ({ recipient: c.recipient, role: c.role, ratePct: c.rate })),
    })
    for (const c of rows) {
      const saved = r.arrangements.find((a: { recipient: string; role: string }) => a.recipient === c.recipient && a.role === c.role) ?? fail(`commission ${c.id}`)
      for (const pay of s.commissionPayments.filter((x) => x.arrangementId === c.id)) {
        await post(`/api/v1/commissions/${saved.id}/payments`, { requestId: randomUUID(), paymentDate: pay.date, amount: pay.amount, paymentMethodId: method(pay.method), reference: pay.reference })
      }
    }
  }
  console.log('commissions ok')

  // real statuses last, then the delay deduction on the finished house
  for (const p of s.projects.filter((x) => x.status === 'Completed' || x.status === 'On Hold')) {
    const cur = await get(`/api/v1/projects/${project(p.id)}`)
    if (cur.status !== upper(p.status)) await put(`/api/v1/projects/${cur.id}/status`, { status: upper(p.status), version: cur.version })
  }
  for (const p of s.projects.filter((x) => x.delay?.appliedAmount)) {
    const terms = await get(`/api/v1/projects/${project(p.id)}/delay-terms`)
    if (!terms.appliedAmount) await post(`/api/v1/projects/${project(p.id)}/delay-terms/deduction`, {
      completionDate: p.delay!.actualCompletion, installmentId: installmentIds.get(p.delay!.appliedToInstallmentId!),
      amount: p.delay!.appliedAmount, notes: p.delay!.notes, version: terms.version,
    })
  }
  console.log('statuses and delay deduction ok')
  await post('/api/v1/auth/logout')
  console.log('done')
}

// what's already saved, keyed by one field (the demo has under 100 of each)
// eslint-disable-next-line @typescript-eslint/no-explicit-any
async function byKey(path: string, key: string): Promise<Map<string, any>> {
  const page = await get(`${path}?size=100`)
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  return new Map(page.content.map((x: any) => [x[key], x]))
}

function fail(what: string): never {
  throw new Error(`not found: ${what}`)
}

main().catch((e) => {
  console.error(e instanceof Error ? e.message : e)
  process.exit(1)
})
