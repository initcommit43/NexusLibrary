import raw from '../../../backend/src/main/resources/agreements/eula.md?raw'
import { LegalDocument } from '../components/LegalDocument'

/** The text is in eula.md, which the app is served over the API and the web imports here. */
export const EulaPage = () => <LegalDocument raw={raw} />
