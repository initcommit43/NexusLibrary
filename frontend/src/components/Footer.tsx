import { Link } from 'react-router-dom'

/**
 * The legal floor of every page.
 *
 * <p>Sits below the app as well as below the sign-in card, because two of the things it
 * links to have to be reachable before anyone has an account: the privacy policy is what a
 * reader consents to when they register, and a policy you can only read once you are inside
 * is not one anyone agreed to.
 */
export const Footer = () => (
  <footer className="site-footer">
    <nav className="site-footer-links">
      <Link to="/credits">Credits</Link>
      <Link to="/privacy">Privacy</Link>
      <Link to="/terms">Terms</Link>
      <a href="https://github.com/initcommit43/NexusLibrary">GitHub</a>
    </nav>

    {/*
      * Named here as well as on the credits page. A reader who wants to know where a cover
      * came from should not have to open a second page to find out that the answer exists.
      */}
    <p className="site-footer-note">
      Metadata from IGDB, TMDB, AniList, MyAnimeList, Simkl, Steam and Open Library.
    </p>
  </footer>
)
