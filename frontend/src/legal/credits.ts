/**
 * Who the data comes from, and what each of them requires us to say about it.
 *
 * <p>These are contractual obligations, not courtesies. Four of the seven providers require
 * attribution in some form and three of those require it beside the data itself rather than
 * only here — see `sourceLink` for that half. This file is the credits page's copy and the
 * one place a provider's required wording is written down.
 */

export type Credit = {
  name: string
  /** Where the provider itself lives, for the link on their name. */
  url: string
  /** What we use them for, in the reader's terms rather than the contract's. */
  use: string
  /**
   * Wording the provider's terms actually demand. Rendered verbatim and never paraphrased:
   * TMDB's is quoted from their API terms and changing a word of it breaks the requirement.
   */
  required?: string
}

export const CREDITS: Credit[] = [
  {
    name: 'TMDB',
    url: 'https://www.themoviedb.org/',
    use: 'Film and TV metadata, posters and backdrops.',
    // TMDB API Terms of Use: this notice must appear prominently, and their own guidance
    // names an About or Credits section as the place for it.
    required:
      'This product uses the TMDB API but is not endorsed or certified by TMDB.',
  },
  {
    name: 'IGDB',
    url: 'https://www.igdb.com/',
    use: 'Game metadata, cover art and release dates.',
    // Twitch Developer Services Agreement VII.C requires a clear path to the source from
    // displays of the data; the per-item link carries that, this names the source.
    required: 'Game data provided by IGDB. IGDB is a Twitch company.',
  },
  {
    name: 'Steam',
    url: 'https://store.steampowered.com/',
    use: 'Your own library, playtimes and achievement progress, when you connect the account.',
    // Steam Web API Terms of Use: Valve marks and links belong on pages using Steam data.
    // Naming them here as well costs nothing and the terms forbid implying endorsement.
    required:
      'Powered in part by Steam. Steam and the Steam logo are trademarks of Valve Corporation. ' +
      'NexusLibrary is not affiliated with or endorsed by Valve.',
  },
  {
    name: 'AniList',
    url: 'https://anilist.co/',
    use: 'Anime and manga metadata, airing schedules, and your own list when you connect the account.',
  },
  {
    name: 'MyAnimeList',
    url: 'https://myanimelist.net/',
    use: 'Your own anime and manga list, when you connect the account.',
    // MAL's agreement permits their marks for attribution but §17 forbids wording that
    // implies endorsement — so this says where the data came from and nothing more.
    required: 'Data from MyAnimeList. NexusLibrary is not affiliated with MyAnimeList.',
  },
  {
    name: 'Simkl',
    url: 'https://simkl.com/',
    use: 'Your own film and TV list, when you connect the account.',
  },
  {
    name: 'Open Library',
    url: 'https://openlibrary.org/',
    use: 'Book metadata and cover images.',
    // CC0, so nothing is owed. Credited because a nonprofit giving its catalogue away
    // deserves saying so.
    required: 'Book data from Open Library, an initiative of the Internet Archive.',
  },
]

/** The typefaces, which are somebody's work too and are licensed on the same terms as code. */
export const FONT_CREDITS = [
  { name: 'Inter', url: 'https://rsms.me/inter/', licence: 'SIL Open Font License 1.1' },
  { name: 'Krub', url: 'https://fonts.google.com/specimen/Krub', licence: 'SIL Open Font License 1.1' },
]
