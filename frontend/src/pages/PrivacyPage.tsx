import raw from '../../../backend/src/main/resources/agreements/privacy.md?raw'
import { LegalDocument } from '../components/LegalDocument'

/** The text is in privacy.md, which the app is served over the API and the web imports here. */
export const PrivacyPage = () => <LegalDocument raw={raw} />
