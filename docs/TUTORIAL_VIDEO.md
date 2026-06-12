# Tutorial Video

ChatMod Mobile has a source-backed product tutorial/promo video artifact for beta onboarding and launch-site use.

This is not the OAuth review demo video. The Google review video must still be recorded from a real consent screen, real backend OAuth callback, and real YouTube test stream if Google requests it.

## Current Artifact

- Final local artifact: `chatmod-mobile-promo/renders/chatmod-mobile-tutorial.mp4`
- Review draft artifact: `chatmod-mobile-promo/renders/chatmod-mobile-tutorial-draft.mp4`
- Midpoint thumbnail: `chatmod-mobile-promo/renders/chatmod-mobile-tutorial-midpoint.jpg`
- Duration: 20.0 seconds
- Frame size: 1920 x 1080
- Product promise: "Your channel. Your bot. Live chat handled."

## Source Files

- `chatmod-mobile-promo/index.html` is the HyperFrames composition.
- `chatmod-mobile-promo/DESIGN.md` defines the visual direction.
- `chatmod-mobile-promo/STORYBOARD.md` defines the five-beat structure.
- `chatmod-mobile-promo/SCRIPT.md` and `chatmod-mobile-promo/narration.txt` define the spoken copy.
- `chatmod-mobile-promo/assets/` contains the ChatMod mark exports.

The video deliberately uses product UI built from the Android dashboard direction instead of stock footage or vague AI-marketing visuals.

## Free Tooling

- HyperFrames renders the HTML/GSAP composition.
- `ffmpeg-static` provides a project-local FFmpeg binary for MP4 output.
- `ffprobe-static` provides optional local media metadata checks.
- No paid video host, editor, asset pack, or AI video service is required for the beta artifact.

## Render And Check

From the repo root:

```powershell
npm run promo-video:check
```

From the video project:

```powershell
cd chatmod-mobile-promo
npm install
npm run check
$env:Path = "$(Resolve-Path node_modules\ffmpeg-static);$(Resolve-Path node_modules\ffprobe-static\bin\win32\x64);$env:Path"
npm run render -- --quality draft --output renders/chatmod-mobile-tutorial.mp4
```

The checked artifact was rendered locally on 2026-06-11 after HyperFrames lint, validate, and inspect passed. HyperFrames reported one composition-size warning only; the composition still inspected cleanly across sampled frames.
