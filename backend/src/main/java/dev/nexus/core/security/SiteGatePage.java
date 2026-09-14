package dev.nexus.core.security;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.web.util.HtmlUtils;

/**
 * The password page, written by the server because nothing behind the gate — the built app
 * included — may be served until it is passed. Self-contained: no script, no external asset,
 * colours copied from the app's tokens so it does not look like a different site.
 */
final class SiteGatePage {

    private static final String TEMPLATE = """
            <!doctype html>
            <html lang="en">
            <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <meta name="robots" content="noindex">
            <title>NexusLibrary</title>
            <style>
            :root { color-scheme: light dark; --bg: #f5f7fa; --surface: #ffffff; --text: #0b1220;
              --muted: #6b7a90; --border: #cbd5e3; --accent: #1f6fff; --on-accent: #ffffff; --danger: #c62334; }
            @media (prefers-color-scheme: dark) {
              :root { --bg: #0a0f1a; --surface: #0f1523; --text: #eef3fa; --muted: #7c8ca6;
                --border: #33456a; --accent: #4c8dff; --on-accent: #06101f; --danger: #ff6b78; }
            }
            * { box-sizing: border-box; }
            body { margin: 0; min-height: 100vh; display: grid; place-items: center; padding: 16px;
              background: var(--bg); color: var(--text);
              font: 15px/1.5 ui-sans-serif, system-ui, -apple-system, "Segoe UI", sans-serif; }
            main { width: 100%%; max-width: 22rem; }
            h1 { margin: 0 0 4px; font-size: 1.75rem; letter-spacing: -0.02em; }
            p { margin: 0 0 24px; color: var(--muted); }
            form { display: grid; gap: 12px; }
            input { width: 100%%; padding: 10px 12px; border: 1px solid var(--border); border-radius: 6px;
              background: var(--surface); color: var(--text); font: inherit; }
            input:focus { outline: 2px solid var(--accent); outline-offset: -1px; border-color: var(--accent); }
            button { padding: 10px 12px; border: 0; border-radius: 6px; background: var(--accent);
              color: var(--on-accent); font: inherit; font-weight: 600; cursor: pointer; }
            .error { margin: 0; color: var(--danger); font-size: 0.875rem; }
            </style>
            </head>
            <body>
            <main>
            <h1>NexusLibrary</h1>
            <p>This site is private. Enter the password to continue.</p>
            <form method="post" action="/site-gate">
            <input type="hidden" name="next" value="%s">
            <input type="password" name="password" aria-label="Password" placeholder="Password"
              autocomplete="current-password" required autofocus>
            %s<button type="submit">Continue</button>
            </form>
            </main>
            </body>
            </html>
            """;

    private SiteGatePage() {}

    static void write(HttpServletResponse response, int status, String next, String error) throws IOException {
        String errorLine = error == null ? "" : "<p class=\"error\" role=\"alert\">" + HtmlUtils.htmlEscape(error) + "</p>\n";

        response.setStatus(status);
        response.setContentType("text/html");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(TEMPLATE.formatted(HtmlUtils.htmlEscape(next), errorLine));
    }
}
