// Excel and PDF versions of a report's table. They're built in the browser from the same rows the page
// shows (and the CSV uses), so the file always matches the screen. The libraries are only loaded when
// someone actually exports.
import { TODAY, formatDate } from './dates'

export type Table = (string | number)[][]

function save(blob: Blob, fileName: string) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = fileName
  a.click()
  setTimeout(() => URL.revokeObjectURL(url), 2000)
}

const money = (n: number) => n.toLocaleString('en-LK', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

// days and hours are counts, not rupees, so they don't get the 2-decimal money format
const isCount = (header: string | number) => /\b(days?|present|absent|hours)\b/i.test(String(header))
const show = (header: string | number, c: string | number) => (typeof c === 'number' ? (isCount(header) ? String(c) : money(c)) : String(c ?? ''))

/** First row is the header. Numbers stay numbers in Excel, so totals and filters work there. */
export async function downloadXlsx(name: string, title: string, rows: Table, company: string) {
  const { default: ExcelJS } = await import('exceljs')
  const book = new ExcelJS.Workbook()
  book.creator = company
  // sheet names can't be longer than 31 characters or contain a few symbols
  const sheet = book.addWorksheet(title.replace(/[\\/?*[\]:]/g, ' ').slice(0, 31), { views: [{ state: 'frozen', ySplit: 1 }] })
  const [header, ...body] = rows
  sheet.addRow(header)
  for (const r of body) sheet.addRow(r)
  sheet.getRow(1).font = { bold: true }
  sheet.autoFilter = { from: { row: 1, column: 1 }, to: { row: 1, column: header.length } }
  header.forEach((_, i) => {
    const col = sheet.getColumn(i + 1)
    const numeric = body.some((r) => typeof r[i] === 'number')
    if (numeric) col.numFmt = isCount(header[i]) ? '0.##' : '#,##0.00'
    const widest = Math.max(String(header[i]).length, ...body.map((r) => show(header[i], r[i]).length))
    col.width = Math.min(60, Math.max(10, widest + 2))
  })
  const buffer = await book.xlsx.writeBuffer()
  save(new Blob([buffer], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' }), `${name}.xlsx`)
}

/** A printable A4 landscape table with the company, report title and date at the top and page numbers. */
export async function downloadPdf(name: string, title: string, rows: Table, company: string, subtitle?: string) {
  const [{ jsPDF }, { autoTable }] = await Promise.all([import('jspdf'), import('jspdf-autotable')])
  const doc = new jsPDF({ orientation: 'landscape', unit: 'pt', format: 'a4' })
  const [header, ...body] = rows
  const numeric = header.map((_, i) => body.some((r) => typeof r[i] === 'number'))
  doc.setFontSize(14)
  doc.text(company, 40, 40)
  doc.setFontSize(11)
  doc.text(title, 40, 58)
  doc.setFontSize(9)
  doc.setTextColor(110)
  doc.text(`${subtitle ? `${subtitle} · ` : ''}Generated ${formatDate(TODAY)}`, 40, 73)
  autoTable(doc, {
    startY: 86,
    head: [header.map(String)],
    body: body.map((r) => r.map((c, i) => show(header[i], c))),
    styles: { fontSize: 8, cellPadding: 4 },
    headStyles: { fillColor: [38, 38, 38] },
    columnStyles: Object.fromEntries(numeric.map((n, i) => [i, n ? { halign: 'right' } : {}])),
    // columnStyles skip the header row, so line the headings up with their numbers here
    didParseCell: (c) => { if (c.section === 'head' && numeric[c.column.index]) c.cell.styles.halign = 'right' },
    didDrawPage: () => {
      const page = doc.getNumberOfPages()
      doc.setFontSize(8)
      doc.setTextColor(110)
      doc.text(`Page ${page}`, doc.internal.pageSize.getWidth() - 70, doc.internal.pageSize.getHeight() - 20)
    },
  })
  save(doc.output('blob'), `${name}.pdf`)
}
