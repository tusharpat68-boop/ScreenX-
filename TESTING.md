# ScreenX AI test checklist

Automatic tests (run in CI or with `gradle :app:testDebugUnitTest`):
time formatting, save-folder name cleaning, and the recording state holder.
Everything else needs a real phone, so use this list. Tick items as you go and send back
anything that fails with: phone model, Android version, the step number, and what you saw.

Use at least one phone on Android 14 or 15 and, if you can, one on Android 10 to 12.

## 1. Install and launch
- [ ] App installs and opens to Home with the dark theme and the pulsing record button
- [ ] All 5 bottom tabs open: Home, Recordings, Screenshots, Editor, Settings
- [ ] Rotating the phone does not crash or reset the screen

## 2. Permissions
- [ ] First Start: Android asks for notifications (Android 13+), then shows the screen-capture dialog
- [ ] Deny notifications: recording still starts, with a short message
- [ ] Choose Microphone audio, deny the permission: recording starts without sound and says so
- [ ] Face camera on, deny camera: recording starts without the camera and says so
- [ ] Settings > Privacy dashboard matches what you granted; "Manage" opens Android settings

## 3. Recording
- [ ] Start, wait 10 s, Stop: a "Recording saved" card appears and Play opens the video
- [ ] The file is in Movies/ScreenX (or your chosen folder) and shows in the Recordings tab
- [ ] Notification shows the running timer with Pause and Stop
- [ ] Pause, then Resume (in app and from the notification): timer stops and restarts; the video skips the paused part
- [ ] Stop right after Start: a "too short" message and no broken file
- [ ] Each of 480p / 720p / 1080p and 24 / 30 / 60 FPS records and plays
- [ ] Press Home while recording, use another app, come back: still recording
- [ ] Stop sharing from Android's own sharing indicator: the video is still saved
- [ ] Rotate the phone while recording: the recording keeps going (black bars are expected)
- [ ] Fill storage to under about 50 MB while recording (or test on a nearly full phone): it stops and saves
- [ ] Force-close the app mid-recording, reopen: no stray partial file in the library

## 4. Audio
- [ ] Microphone: talk, record, play back with your voice
- [ ] Internal: play music in another app, record, hear it (Android 10+)
- [ ] Mic + Internal: both in the same video
- [ ] Voice stays in sync with the picture after 2-3 minutes
- [ ] Pause and Resume while talking: sound still matches the picture
- [ ] Internal audio from a call or a protected app is silent (expected; not a bug)

## 5. Floating controls, face camera, drawing, watermark
- [ ] Floating bubble appears over other apps, expands, pauses, resumes, stops, hides (X), and comes back from the notification
- [ ] Bubble can be dragged and remembers its spot
- [ ] Face camera shows your face, flips on tap, turns off on long-press; Android's camera indicator turns off with it
- [ ] Drawing: pen, highlighter, eraser, arrow, circle, rectangle, colors, sizes, undo, clear
- [ ] Cursor mode lets you use other apps while drawings stay visible
- [ ] Watermark text appears in the chosen corner and is in the recorded video
- [ ] Bubble, camera and drawings are gone from the last frames when you stop

## 6. Countdown, auto-stop, quick start
- [ ] Countdown 3 s: app goes to the background, 3-2-1 shows, then recording begins
- [ ] Auto-stop 1 min: recording stops by itself and saves
- [ ] Quick Settings tile starts the start flow and stops a running recording
- [ ] Reminder set a minute ahead: notification appears; tapping it starts the start flow
- [ ] Beep and vibration settings work and the beep is not heard in the video

## 7. Library
- [ ] Thumbnails, durations, sizes and dates look right
- [ ] Search, each sort, favorites and the Favorites filter work
- [ ] Rename (also to an existing name: refused), Share, Details, Delete
- [ ] After reinstalling the app, old files may be hidden by Android (expected)

## 8. Editor
- [ ] Open a recording; preview plays; trim handles move; playback stays inside the trim
- [ ] Rotate left/right and each crop preset show correctly in the preview
- [ ] Export at Low, Medium, High and Original; each result plays and is a new file
- [ ] Extract audio produces a playable .m4a in Music/ScreenX
- [ ] Cancel an export: no hang, no stray file
- [ ] Extract audio is disabled on a video recorded without audio

## 9. Screenshots
- [ ] Capture with a 3 s delay, switch apps, screenshot appears in the gallery
- [ ] Preview, Share, Rename, Details, Delete work
- [ ] Capture button is disabled while recording

## 10. Settings, language, theme
- [ ] Switch to বাংলা and back: all screens change
- [ ] Dark, Light, System theme and each accent color apply everywhere
- [ ] Change save folder: new recordings, screenshots and exports go there
- [ ] Device capabilities lists your encoder and supported resolutions

## Known limits (not bugs)
- Android asks for screen-capture permission every time. It cannot be skipped or hidden.
- Internal audio only exists on Android 10+ and only for apps that allow capture.
- The video size is fixed at start, so rotating mid-recording adds black bars.
- Floating bubble, camera, drawings and watermark are part of the screen, so they appear in the video.
- Screenshots cannot be taken while a recording is running.
- Reminders are inexact and are lost when the phone restarts.
- After reinstalling, Android hides media saved by the previous install from the app.
