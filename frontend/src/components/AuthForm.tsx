import { useEffect, useId, useState, type FormEvent } from 'react'
import { ApiError, api } from '../api/client'
import { AuthBrand } from './AuthBrand'
import { Footer } from './Footer'
import { Turnstile } from './Turnstile'
import { useNarrowScreen } from './useNarrowScreen'

export type AuthField = {
  name: string
  label: string
  type: string
  autoComplete: string
}

type Props = {
  title: string
  submitLabel: string
  fields: AuthField[]
  onSubmit: (values: Record<string, string>) => Promise<void>
  footer: React.ReactNode
  /**
   * What the reader has to agree to before the form will send, if anything. Registration
   * passes the terms and privacy policy; signing in passes nothing, because agreeing again
   * to something you agreed to when you registered is a click that means nothing.
   */
  consent?: React.ReactNode
  /**
   * Whether this form is one anyone can reach without an account, and so has to prove it is
   * being filled in by a person. Opt-in rather than automatic: signing in and spending a
   * reset link both already hold something only the right reader has.
   */
  botCheck?: boolean
  /** What a phone sets under the send button, such as the way to a forgotten password. */
  underSubmit?: React.ReactNode
}

const EyeIcon = ({ crossed }: { crossed: boolean }) => (
  <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" aria-hidden>
    <path
      d="M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12Z"
      strokeWidth="1.8"
      strokeLinejoin="round"
    />
    <circle cx="12" cy="12" r="3" strokeWidth="1.8" />
    {crossed && <path d="m4 4 16 16" strokeWidth="1.8" strokeLinecap="round" />}
  </svg>
)

/**
 * The fields as one grouped card, as the native sign-in draws them: each named by its
 * placeholder, a password with an eye to show it, and any message under the card, since one
 * inside a row would break the rows' single height.
 */
const FieldGroup = ({
  fields,
  values,
  errors,
  onChange,
}: {
  fields: AuthField[]
  values: Record<string, string>
  errors: Record<string, string>
  onChange: (name: string, value: string) => void
}) => {
  const errorId = useId()
  const [revealed, setRevealed] = useState<Record<string, boolean>>({})

  return (
    <>
      <div className="field-group">
        {fields.map((field) => {
          const shown = revealed[field.name] ?? false
          return (
            <label key={field.name}>
              <input
                type={shown ? 'text' : field.type}
                name={field.name}
                autoComplete={field.autoComplete}
                aria-label={field.label}
                placeholder={field.label}
                value={values[field.name] ?? ''}
                aria-invalid={Boolean(errors[field.name])}
                aria-describedby={errors[field.name] ? `${errorId}-${field.name}` : undefined}
                onChange={(e) => onChange(field.name, e.target.value)}
              />
              {field.type === 'password' && (
                <button
                  type="button"
                  className="icon-button auth-reveal"
                  aria-label={`Show ${field.label.toLowerCase()}`}
                  aria-pressed={shown}
                  onClick={() => setRevealed((r) => ({ ...r, [field.name]: !shown }))}
                >
                  <EyeIcon crossed={shown} />
                </button>
              )}
            </label>
          )
        })}
      </div>

      {fields
        .filter((field) => errors[field.name])
        .map((field) => (
          <small key={field.name} id={`${errorId}-${field.name}`} className="field-error">
            {errors[field.name]}
          </small>
        ))}
    </>
  )
}

export const AuthForm = ({
  title,
  submitLabel,
  fields,
  onSubmit,
  footer,
  consent,
  botCheck,
  underSubmit,
}: Props) => {
  const narrow = useNarrowScreen()
  const [values, setValues] = useState<Record<string, string>>({})
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [consented, setConsented] = useState(false)
  const [botToken, setBotToken] = useState('')
  const [challengeActive, setChallengeActive] = useState(false)
  // Bumped after a failed send to remount the widget. Turnstile tokens are single-use, so
  // the one just spent cannot be sent again and the reader would be stuck on a dead form.
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    if (!botCheck) return
    // A deployment with no site key is not checking, and blocking the button on a widget
    // that will never appear would make the form unsendable.
    api
      .publicConfig()
      .then((config) => setChallengeActive(Boolean(config.turnstileSiteKey)))
      .catch(() => setChallengeActive(false))
  }, [botCheck])

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    setBusy(true)
    setFormError(null)
    setFieldErrors({})

    try {
      await onSubmit({ ...values, turnstileToken: botToken })
    } catch (err) {
      setBotToken('')
      setAttempt((n) => n + 1)

      if (err instanceof ApiError) {
        setFieldErrors(err.fieldErrors)
        // A field-level message is already shown inline; repeating it above is noise.
        setFormError(Object.keys(err.fieldErrors).length ? null : err.message)
      } else {
        setFormError('Could not reach the server. Is the backend running?')
      }
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="page-centered auth-page">
      <form className="card auth-form" onSubmit={handleSubmit} noValidate>
        {narrow && <AuthBrand />}
        {/*
          * Signing in and registering are named by the button under the fields, so a phone
          * keeps their heading for a screen reader only, as the native screens have none.
          */}
        <h1 className={narrow && title === submitLabel ? 'auth-title-quiet' : undefined}>
          {title}
        </h1>

        {formError && (
          <p className="alert" role="alert">
            {formError}
          </p>
        )}

        {narrow && (
          <FieldGroup
            fields={fields}
            values={values}
            errors={fieldErrors}
            onChange={(name, value) => setValues((v) => ({ ...v, [name]: value }))}
          />
        )}

        {!narrow && fields.map((field) => (
          <label key={field.name} className="field">
            <span>{field.label}</span>
            <input
              type={field.type}
              name={field.name}
              autoComplete={field.autoComplete}
              value={values[field.name] ?? ''}
              aria-invalid={Boolean(fieldErrors[field.name])}
              onChange={(e) => setValues((v) => ({ ...v, [field.name]: e.target.value }))}
            />
            {fieldErrors[field.name] && (
              <small className="field-error">{fieldErrors[field.name]}</small>
            )}
          </label>
        ))}

        {consent && (
          <label className="consent">
            <input
              type="checkbox"
              name="acceptedTerms"
              checked={consented}
              aria-invalid={Boolean(fieldErrors.acceptedTerms)}
              onChange={(e) => setConsented(e.target.checked)}
            />
            <span>{consent}</span>
          </label>
        )}

        {botCheck && <Turnstile key={attempt} onToken={setBotToken} />}

        {/*
          * Unchecked consent disables the button rather than failing on submit. The server
          * refuses it either way, but a reader should not have to press send to be told
          * about a box they have not ticked yet.
          */}
        <button
          type="submit"
          disabled={
            busy || (Boolean(consent) && !consented) || (challengeActive && !botToken)
          }
        >
          {busy ? 'Working…' : submitLabel}
        </button>

        {narrow && underSubmit && <p className="auth-under">{underSubmit}</p>}

        {/* A phone pins it to the foot of the screen, as the native app does. */}
        <p className={narrow ? 'auth-footer' : 'muted auth-aside'}>{footer}</p>
      </form>

      {/* Reachable before there is an account, which is the point of putting it here. */}
      <Footer />
    </div>
  )
}
