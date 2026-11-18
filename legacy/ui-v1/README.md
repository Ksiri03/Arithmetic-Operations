# Legacy UI - version 1

This folder preserves the original editorial-style frontend exactly as it was before the simplified student UI replaced it.

Files:

- `index.html`, `styles.css`, `app.js` - the original static frontend.
- `fonts/` - the Newsreader, Archivo and IBM Plex Mono files used by that design.

The matching lab record is in [`docs/legacy/`](../../docs/legacy/). The same version is available from Git:

```powershell
git checkout legacy-ui-v1
```

To use this frontend with the current Java API, copy these files over `src/main/resources/static/`, build, and run the application. The API contract is unchanged.
