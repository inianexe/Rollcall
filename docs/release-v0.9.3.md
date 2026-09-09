# Rollcall v0.9.3 — Direct Unpacked Installation

The browser ZIP now places manifest.json directly at its root, removing the extra nested installation folder.

1. Download rollcall-v0.9.3-browser.zip.
2. Extract all files into a new folder named Rollcall.
3. In your normal file manager, open Rollcall: manifest.json must be directly inside it, alongside core.js and assets.
4. Chrome/Edge → Extensions → Developer mode → Load unpacked → select that Rollcall folder.
5. Refresh MyCamu.

Do not select the ZIP or a parent folder. Firefox still selects manifest.json through Load Temporary Add-on.

This release validates fresh ZIP extraction and loads the extracted package as a real unpacked extension in automated Chromium before publishing. That confirms manifest/content-script loading on a synthetic page; it is not a live MyCamu or Microsoft Edge test.

Attendance/timetable behaviour is unchanged. Android excluded. If installation still fails, provide the exact error and a screenshot of the selected folder's contents/path.
