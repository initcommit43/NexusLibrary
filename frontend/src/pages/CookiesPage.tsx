import raw from '../../../backend/src/main/resources/agreements/cookies.md?raw'
import { LegalDocument } from '../components/LegalDocument'

/** The text is in cookies.md, which the app is served over the API and the web imports here. */
export const CookiesPage = () => <LegalDocument raw={raw} />
