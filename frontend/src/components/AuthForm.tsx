import { useState, type FormEvent } from 'react'
import { ApiError } from '../api/client'

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
}

export const AuthForm = ({ title, submitLabel, fields, onSubmit, footer }: Props) => {
  const [values, setValues] = useState<Record<string, string>>({})
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    setBusy(true)
    setFormError(null)
    setFieldErrors({})

    try {
      await onSubmit(values)
    } catch (err) {
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
    <div className="page-centered">
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

        <button type="submit" disabled={busy}>
          {busy ? 'Working…' : submitLabel}
        </button>

        <p className="muted">{footer}</p>
      </form>
    </div>
  )
}
