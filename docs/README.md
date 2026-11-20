# Documentation index

Everything in `docs/`, in one place. The README links here so that none of these documents is
orphaned.

## Read first

| Document | What it is |
| --- | --- |
| [../README.md](../README.md) | What the app is, how to install it, how to build it |
| [../PRIVACY.md](../PRIVACY.md) | The complete list of network paths, permissions and incognito rules |
| [../SECURITY.md](../SECURITY.md) | How to report a vulnerability privately |
| [../CONTRIBUTING.md](../CONTRIBUTING.md) | Ground rules, build steps, code style |
| [../PLUGINS.md](../PLUGINS.md) | The plugin and theme file format, with all 45 bundled definitions |
| [../NOTICE.md](../NOTICE.md) | Third-party asset licences and required attribution |

## Rights and release

| Document | What it is |
| --- | --- |
| [signing-and-updates.md](signing-and-updates.md) | Signing state of every published release, the migration for existing users, the maintainer checklist |
| [artwork-provenance.md](artwork-provenance.md) | One row per bundled image: source, rights holder, licence, attribution, status. Authoritative. |
| [../THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md) | The wallpaper set specifically, and the three withheld images |
| [../fdroid/SUBMISSION.md](../fdroid/SUBMISSION.md) | State of the F-Droid submission |
| [../fdroid/metadata/com.genz.keyboard.yml](../fdroid/metadata/com.genz.keyboard.yml) | The F-Droid build recipe draft |
| [screenshots/README.md](screenshots/README.md) | How to capture the screenshots the README and the store listing need |

## Design and implementation records — `research/`

These are working documents from the project's own development. They describe intent at the time
of writing; where one conflicts with the code, the code is correct. The newest first, roughly.

| Document | Subject |
| --- | --- |
| [research/THEME_STUDIO_LLD.md](research/THEME_STUDIO_LLD.md) | Low-level design of the theme editor |
| [research/ADVANCED_THEME_ENGINE_LLD.md](research/ADVANCED_THEME_ENGINE_LLD.md) | Skin parsing, palette sampling, rendering |
| [research/FONTS_AND_TEXT_STYLES_LLD.md](research/FONTS_AND_TEXT_STYLES_LLD.md) | Font handling and text styles |
| [research/EMOJI_GIF_QUOTES_LLD.md](research/EMOJI_GIF_QUOTES_LLD.md) | Emoji, GIF and quote panels |
| [research/GENZ_DICTIONARY_LLD.md](research/GENZ_DICTIONARY_LLD.md) | Prediction, dictionaries, learning |
| [research/ANIME_THEME_COLLECTION.md](research/ANIME_THEME_COLLECTION.md) | The anime theme set and its palettes |
| [research/ANDROID_API_VERIFICATION.md](research/ANDROID_API_VERIFICATION.md) | Which platform APIs the app relies on, per API level |
| [research/FEATURE_IMPLEMENTATION_SPECS.md](research/FEATURE_IMPLEMENTATION_SPECS.md) | Feature specifications |
| [research/FEATURE_SCORING_CORRECTED.md](research/FEATURE_SCORING_CORRECTED.md) | Prioritisation of proposed features |
| [research/GENZ_KEYBOARD_1_1_0_LLD.md](research/GENZ_KEYBOARD_1_1_0_LLD.md) | Design for the 1.1.0 cycle |
| [research/GENZ_KEYBOARD_1_1_0_ROADMAP.md](research/GENZ_KEYBOARD_1_1_0_ROADMAP.md) | Plan for the 1.1.0 cycle |
| [research/GENZ_KEYBOARD_1_1_0_RELEASE_CHECKLIST.md](research/GENZ_KEYBOARD_1_1_0_RELEASE_CHECKLIST.md) | Release checklist for 1.1.0 |
| [research/GENZ_KEYBOARD_RELEASE_ROADMAP.md](research/GENZ_KEYBOARD_RELEASE_ROADMAP.md) | Earlier release roadmap |
| [research/PHASE_1_IMPLEMENTATION_PLAN.md](research/PHASE_1_IMPLEMENTATION_PLAN.md) | Phase 1 implementation plan |
| [research/PHASE_1_TECHNICAL_DESIGN.md](research/PHASE_1_TECHNICAL_DESIGN.md) | Phase 1 technical design |
| [research/PHASE_1_TEST_PLAN.md](research/PHASE_1_TEST_PLAN.md) | Phase 1 test plan |
| [research/PRIVACY_MILESTONE_ACCEPTANCE.md](research/PRIVACY_MILESTONE_ACCEPTANCE.md) | Acceptance criteria for the privacy milestone |
| [research/PRIVACY_REGRESSION_AUDIT.md](research/PRIVACY_REGRESSION_AUDIT.md) | Privacy regression audit |
| [research/DEVICE_TEST_CHECKLIST.md](research/DEVICE_TEST_CHECKLIST.md) | What to check by hand on a real device |
| [research/REPOSITORY_AUDIT.md](research/REPOSITORY_AUDIT.md) | A repository audit of commit `78630d0` (v1.0.5). **Stale**: it describes 71 Kotlin files; the tree now has 112. Kept as history. |

## Adding a document

Put design and research material in `research/`, and add a row above so it can be found. A
document that nothing links to may as well not exist.
