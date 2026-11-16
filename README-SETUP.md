# Forge - update 4

Upload to GitHub (replace the old ones): www/  native/  scripts/  changes/
Then: Actions > Build Android app > Run workflow (keep the native box ticked).

Important: native/ now has a new folder, native/res/ (the notification design). Upload the whole native/ folder,
not only the .kt files, or the phone will keep showing the old plain notification.

Changes
1. The exercise demo now also plays for the FIRST exercise when you press Start, same screen as the other exercises
   (20 seconds, "Skip demo"). It follows the "Exercise demo" switch in Profile.
2. Rest timer is a full screen now (big countdown ring, what is next, +15s and Skip). The small rest bar at the bottom is gone.
   Before a new exercise the rest screen also shows that exercise's video and first steps.
3. New phone notification: step count, goal ring with %, kcal, km and steps to go, plus a slim goal bar when collapsed.
   It follows the phone's light/dark theme.

Files touched
- www/index.html                      (changes 1 and 2)
- changes/7-rest-demo.js              (same code, for scripts/build_html.py)
- native/com/forge/bar/StepService.kt (change 3)
- native/res/...                      (change 3, new)
- scripts/patch_android.py, scripts/build_html.py
