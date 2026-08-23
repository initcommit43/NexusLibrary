import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { App } from './App'
import { AuthProvider } from './auth/AuthProvider'
import { applyTheme, storedTheme } from './theme'

// Self-hosted rather than loaded from Google Fonts: hotlinking sends every visitor's IP to
// a third party, which the DSGVO groundwork carried over from v1 does not allow, and an
// installed PWA has to render offline.
import '@fontsource-variable/inter'
import '@fontsource/krub/400.css'
import '@fontsource/krub/500.css'
import '@fontsource/krub/600.css'

import './styles/tokens.css'
import './styles/themes.css'
import './styles/base.css'
import './index.css'

// Before the first paint, so a stored choice never flashes the other palette.
applyTheme(storedTheme())

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <AuthProvider>
        <App />
      </AuthProvider>
    </BrowserRouter>
  </StrictMode>,
)
