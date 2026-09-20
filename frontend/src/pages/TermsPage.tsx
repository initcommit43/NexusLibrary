import raw from '../../../backend/src/main/resources/agreements/terms.md?raw'
import { LegalDocument } from '../components/LegalDocument'

/** The text is in terms.md, which the app is served over the API and the web imports here. */
export const TermsPage = () => <LegalDocument raw={raw} />
