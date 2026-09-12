import { useEffect, useState, type FormEvent } from 'react'
import { ApiError, api } from '../api/client'
import { Footer } from './Footer'
import { Turnstile } from './Turnstile'

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
}

export const AuthForm = ({
  title,
  submitLabel,
  fields,
  onSubmit,
  footer,
  consent,
  botCheck,
}: Props) => {
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
        <h1>{title}</h1>

        {formError && (
          <p className="alert" role="alert">
            {formError}
          </p>
        )}

        {fields.map((field) => (
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

        <p className="muted auth-aside">{footer}</p>
      </form>

      {/* Reachable before there is an account, which is the point of putting it here. */}
      <Footer />
    </div>
  )
}
