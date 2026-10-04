import { Link } from 'react-router-dom'
import { LegalLayout } from './LegalLayout'

/**
 * Renders one of the legal documents from the Markdown the backend serves to the app.
 *
 * <p>The same file, imported at build time rather than fetched. The words used to live here as
 * JSX and the version lived in application.yml, which is two copies of one thing in two
 * languages; the app forced them into one file, and there is no reason for the web to keep a
 * third. Build time rather than a request because these pages are read by people who are not
 * signed in, sometimes from a sign-up form they have not submitted, and a policy that cannot
 * be read because an API call failed is worse than one that is a release behind. The two
 * deploy together from one image anyway, so "a release behind" is not a state that exists.
 *
 * <p>The renderer below handles only the constructs these four documents use.
 * {@code AgreementDocumentsTest} fails the build if one of them grows a construct this cannot
 * draw, which is what keeps "only what we author" from quietly becoming "whatever was written".
 */
export const LegalDocument = ({ raw }: { raw: string }) => {
  const { title, updated, body } = parseDocument(raw)

  return (
    <LegalLayout title={title} updated={updated}>
      {renderBlocks(body)}
    </LegalLayout>
  )
}

type Parsed = { title: string; updated: string; body: string }

/** Splits the `---` front matter off the top. The heading is metadata, not the first paragraph. */
const parseDocument = (raw: string): Parsed => {
  const text = raw.replace(/\r\n/g, '\n')
  const end = text.indexOf('\n---', 3)
  const header = text.slice(4, end)
  const value = (key: string) =>
    header.match(new RegExp(`^${key}:\\s*(.+)$`, 'm'))?.[1].trim() ?? ''

  return { title: value('title'), updated: value('updated'), body: text.slice(end + 4).trim() }
}

/**
 * Blocks are separated by blank lines, and a block's own line breaks are where the source file
 * was wrapped for reading — not breaks the reader should see, so they close up into spaces.
 */
const renderBlocks = (body: string) =>
  body.split(/\n{2,}/).map((block, index) => {
    const key = String(index)

    if (block.startsWith('## ')) {
      return <h2 key={key}>{inline(block.slice(3))}</h2>
    }
    if (block.startsWith('### ')) {
      return <h3 key={key}>{inline(block.slice(4))}</h3>
    }
    if (block.startsWith('- ')) {
      return (
        <ul key={key}>
          {items(block).map((item, i) => (
            <li key={i}>{inline(item)}</li>
          ))}
        </ul>
      )
    }
    return <p key={key}>{inline(unwrap(block))}</p>
  })

/** One entry per `- `, with the indented continuation lines belonging to the entry above. */
const items = (block: string) =>
  block
    .split(/\n(?=- )/)
    .map((item) => unwrap(item.replace(/^- /, '')))

const unwrap = (text: string) => text.split('\n').map((line) => line.trim()).join(' ')

const INLINE = /`([^`]+)`|\[([^\]]+)\]\(([^)]+)\)/g

/** Inline code and links. Everything else in these documents is plain prose. */
const inline = (text: string) => {
  const nodes: React.ReactNode[] = []
  let last = 0

  for (const match of text.matchAll(INLINE)) {
    const at = match.index
    if (at > last) nodes.push(text.slice(last, at))

    const [, code, label, href] = match
    if (code !== undefined) {
      nodes.push(<code key={at}>{code}</code>)
    } else if (href.startsWith('/')) {
      // Internal: routed, so following one does not reload the app to read one more page.
      nodes.push(
        <Link key={at} to={href}>
          {label}
        </Link>,
      )
    } else {
      nodes.push(
        <a key={at} href={href} target="_blank" rel="noreferrer">
          {label}
        </a>,
      )
    }
    last = at + match[0].length
  }

  if (last < text.length) nodes.push(text.slice(last))
  return nodes
}
