# Rollcall 0.9.1 — by iniexe

## Install in Chrome or Edge — select the folder

**The Load unpacked window only selects folders. It is normal for `manifest.json` to be hidden there.**

1. Download the **rollcall-v0.9.3-browser.zip** asset from the latest release.
2. Extract the ZIP with your file manager. Do not open it as an archive inside Chrome.
3. Open `chrome://extensions` (or `edge://extensions`) and enable Developer mode.
4. Click **Load unpacked**, open the extracted folder, then select the **extracted folder containing manifest.json** and click **Select Folder / Open**. You do not select a JSON file in Chrome.
5. Refresh MyCamu. The panel footer should say **0.9.3**.

If you downloaded GitHub's **Source code (zip)** instead, select the **extension** folder inside the extracted repository. Selecting the outer repository folder causes “Manifest file is missing or unreadable”.

| Download | Folder to select in Chrome / Edge |
| --- | --- |
| `rollcall-v0.9.3-browser.zip` | The folder you extracted the ZIP into |
| GitHub Source code ZIP / cloned repository | `extension` |
| Old project ZIP | Inner `extension` folder; prefer the latest browser ZIP |

**Firefox is different:** Load Temporary Add-on selects the `manifest.json` **file** inside that folder.

## Installation

Use a current desktop browser. No Node.js, terminal commands or account setup are needed to install Rollcall. This is an unpacked extension, not a browser-store release.

1. Download and extract the project ZIP. From a GitHub repository, use **Code → Download ZIP**, then extract it.
2. Locate the **extension** folder containing `manifest.json`. With the v0.9.3 browser ZIP, manifest.json is directly inside the folder you extract into.
3. Keep that folder somewhere permanent. Select this inner folder, not the ZIP or the outer project folder.
4. Follow the browser instructions below, then open MyCamu and refresh its page.

### Google Chrome

1. Enter `chrome://extensions` in the address bar.
2. Enable **Developer mode**.
3. Click **Load unpacked** and select the folder containing `manifest.json`.
4. Confirm Rollcall is enabled; open MyCamu → Attendance.

[Official Chrome installation guide](https://developer.chrome.com/docs/extensions/get-started/tutorial/hello-world).

### Microsoft Edge

1. Enter `edge://extensions` in the address bar.
2. Enable **Developer mode**.
3. Click **Load unpacked** and select the extension folder.
4. Refresh MyCamu.

[Official Edge sideloading guide](https://learn.microsoft.com/en-us/microsoft-edge/extensions/getting-started/extension-sideloading).

### Mozilla Firefox

1. Enter `about:debugging#/runtime/this-firefox` in the address bar.
2. Click **Load Temporary Add-on**.
3. Open the extension folder and select **manifest.json**.
4. Refresh MyCamu. The footer should show **0.9.3**.

Firefox removes temporary add-ons when the browser restarts. Repeat these steps after a restart. This project does not yet include a signed package for permanent Firefox installation. [Official Firefox temporary-installation guide](https://extensionworkshop.com/documentation/develop/temporary-installation-in-firefox/).

### Other browsers

Brave, Vivaldi and Opera are Chromium-based candidates, but Rollcall has not been verified in them. Open the browser's Extensions manager, look for **Developer mode → Load unpacked**, and select the same extension folder if those controls are available. Treat these as experimental installations, not confirmed support.

Safari has no ready-to-install package in this project. Mobile browsers and the native MyCamu app are outside this release's scope. Android source is excluded.

## How to use Rollcall

### Check attendance

1. Log into MyCamu normally and select the correct semester.
2. Open **Attendance → Subject-wise**.
3. In Rollcall, select **Attendance → Scan attendance** and wait for the scan to finish.
4. Compare the captured courses with the chart. If a course is missing, hover its bar to expose its tooltip.
5. Use **Find a course** to search by name or code. Toggle **At risk** to show courses below 75% or where the next missed period would cross below 75%.
6. Expand **What if I…** inside a card to preview attending or missing the next counted period. This never changes your actual counts.

| Card message | Meaning |
| --- | --- |
| You can miss N more periods | Those additional absences keep attendance at or above 75%; the following absence drops it below. |
| Missing the next period drops you below 75% | No absence margin remains. |
| Attend the next N periods | Attend N consecutive counted periods to recover to at least 75%. |

Calculations use **counted periods, not days**. A timetable slot's duration does not establish how many attendance periods MyCamu counts.

### Read the daily timetable

1. Scan attendance first so there are counts to match.
2. Navigate directly to **Timetable in the same tab**.
3. Choose a date in MyCamu's daily timetable; close **Weekly Schedule** if open.
4. Select Rollcall's **Today → Read day**.
5. Verify the displayed date and course list. **NOW** identifies an ongoing entry; **NEXT** identifies the next entry when the selected date is today.
6. After changing the selected day, click **Read day** again. A day other than today is explicitly labelled.

Course matches use exact course codes. If attendance is unavailable or older than 15 minutes, return to Attendance and scan again. A reload clears in-memory captures, so scan again after refreshing. No readable classes does **not** establish a holiday or free day.

### Move, collapse and reset

- Drag **Move** to reposition the panel.
- Double-click Move, or focus it and press Enter, to reset its position.
- Click the **Rollcall header** to collapse or expand it.
- Use **Attendance → Troubleshooting → Clear results** to discard captured data.
- Use **Download diagnostics** when reporting a reading problem.

### Update or uninstall

For updates, replace the files in the installed folder, reload Rollcall from your browser's extension manager, then refresh MyCamu. In Firefox, use **Reload** beside the temporary add-on. If the footer still shows an old version, remove the old installation and load the new folder. Keep only one Rollcall installation active.

To uninstall, remove Rollcall in the browser's extension manager. Refresh MyCamu to remove the already-loaded panel.

### Troubleshooting

| Problem | What to try |
| --- | --- |
| Manifest missing | Extract the ZIP first; select the inner folder containing `manifest.json`. Firefox requires selecting the file itself. |
| No panel | Confirm the extension is enabled, refresh MyCamu, and open Attendance or Timetable. The panel is limited to those pages. |
| No courses or incomplete results | Open Subject-wise, scan, then hover missing chart bars. |
| Date unavailable | Select one day in the daily timetable, close the weekly view, and click Read day again. |
| Timetable course has no attendance | Scan attendance in the same tab; verify that the course codes match. |
| Wrong account or semester data | Refresh MyCamu after switching account or semester and capture again. |
| Still failing | Include the Rollcall version, browser, diagnostics and a screenshot of the affected view in your report. |



### v0.9.3 installation check
The release pipeline extracts the browser ZIP and loads it into Chromium as an unpacked extension. Publication requires the panel to mount on a synthetic MyCamu URL. This checks manifest loading, not live account behaviour or Edge compatibility.
