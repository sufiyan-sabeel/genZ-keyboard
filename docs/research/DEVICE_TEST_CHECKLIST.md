# Device Test Checklist — Incognito, Plugins, Autofill, GIF

Everything here requires a real Android device or emulator. **None of it has been run.** The
environment that produced this repository has no device, no emulator and no KVM, so every claim in
the audit documents is source-, unit- or build-verified only.

Record for each item: device model, Android version, host app, and pass/fail. Attach a screenshot
where the item is visual.

---

## 1. Manual incognito from the toolbar

- [ ] Open any text field, tap the incognito button on the toolbar.
- [ ] The toast reads "Incognito: words and clipboard aren't remembered".
- [ ] Type a new word, press space twice, close and reopen the keyboard.
- [ ] **Expect:** the word is not offered as a prediction (it was not learned).
- [ ] Copy a piece of text, open the clipboard panel.
- [ ] **Expect:** the copied text is not in the history.
- [ ] Tap incognito off, type the same word again, reopen.
- [ ] **Expect:** learning resumes.

## 2. Manual incognito from Settings

This path was broken until the preference listener was added — test it separately.

- [ ] With the keyboard open in a field, open Settings → Typing → Privacy → Incognito.
- [ ] Switch it on, return to the field **without** changing fields.
- [ ] **Expect:** the toolbar incognito icon reflects the new state immediately.
- [ ] Install a test mod that logs every event it receives (see item 5).
- [ ] **Expect:** no `press`, `word`, `key`, `selection` or `pointer` events arrive after the toggle.

## 3. Password-field entry and exit

- [ ] Focus a password field in a browser and in a password manager.
- [ ] **Expect:** no suggestions, no autocorrect, no clipboard offer, no inline autofill chip.
- [ ] Type a word, leave the field for a plain field, reopen the keyboard.
- [ ] **Expect:** the word typed in the password field is never suggested.
- [ ] With a mod installed, confirm no content events arrive while in the password field.
- [ ] Move back to a plain field and confirm events resume.

## 4. Inline autofill visibility and late responses

Requires an autofill service (e.g. Google Password Manager) with a saved login.

- [ ] Focus a login field. **Expect:** an autofill chip appears, themed to the keyboard.
- [ ] Focus a field with no autofill service configured. **Expect:** no chip, and no empty gap.
- [ ] Focus a login field, then immediately toggle incognito.
- [ ] **Expect:** the chip disappears at once, not at the next field change.
- [ ] Focus a login field and rotate the device while the chip is visible.
- [ ] **Expect:** no stale or duplicated chip after rotation.
- [ ] On Android 8–10 (API 26–29): confirm the clipboard paste offer still works and no chip
      space is reserved.

## 5. Plugin events before and after a privacy transition

Install a mod that writes received events to its own log, then:

- [ ] In a normal field, type a few characters.
- [ ] **Expect:** `press`/`key`/`word` events are logged.
- [ ] Toggle incognito on **while staying in the same field**.
- [ ] Type more characters.
- [ ] **Expect:** no further content events; `panel`/`shift`/`language` still arrive.
- [ ] Toggle incognito off and type again.
- [ ] **Expect:** content events resume.
- [ ] Leave a mod that only draws effects (tilt/haptics) installed, and confirm it still works
      in incognito — non-content events must keep flowing.
- [ ] Note anything the mod logged *before* the toggle: that data cannot be recalled, and this
      checklist does not claim otherwise.

## 6. GIF search in normal and private modes

- [ ] With a GIF key configured, open GIF search and search a term.
- [ ] **Expect:** network results appear.
- [ ] Toggle incognito on, reopen GIF search.
- [ ] **Expect:** the line "Incognito: built-in GIFs only" appears, and the built-in catalogue
      loads. Search still filters locally.
- [ ] Watch a network proxy or use airplane mode with Wi-Fi off.
- [ ] **Expect:** in incognito, **no** request is attempted at all — the panel works offline.
- [ ] Toggle incognito off. **Expect:** network results return, and the notice disappears.
- [ ] With no GIF key configured (but not in incognito), confirm the notice does **not** appear.

## 7. Typo-Undo after editor content changes

- [ ] Type a misspelling that autocorrects, then tap the undo pill.
- [ ] **Expect:** the original word is restored exactly.
- [ ] Type a misspelling, let it correct, then move the cursor before tapping undo.
- [ ] **Expect:** the pill is gone, or tapping it does nothing. No text is deleted.
- [ ] Type a misspelling, let it correct, wait ~8 seconds, then tap undo.
- [ ] **Expect:** the offer has expired; nothing is deleted.
- [ ] In an app that rewrites its own text (Termux, a code editor): type, let it correct, then
      tap undo. **Expect:** no corruption; at worst nothing happens.
- [ ] In incognito: tap undo, then check the personal dictionary.
- [ ] **Expect:** the undone word was not recorded as "don't correct".

## 8. Keyboard view recreation and rotation

- [ ] With the keyboard open in a text field, rotate the device.
- [ ] **Expect:** the keyboard redraws correctly, with no duplicated keys and no ghost strip.
- [ ] Rotate with an inline autofill chip visible.
- [ ] **Expect:** no stale chip survives.
- [ ] Rotate with incognito on, in a password field.
- [ ] **Expect:** the privacy state is preserved across the recreation.
- [ ] Switch theme while incognito is on, then type.
- [ ] **Expect:** no learning, no clipboard capture, no content events to mods.
- [ ] Put the device under memory pressure (open many apps), return to the keyboard.
- [ ] **Expect:** the service restarts cleanly and privacy is recomputed for the current field.

---

## What this checklist cannot establish

- That a script which already received text before the toggle has forgotten it — it has not, and
  cannot be made to.
- That the target app retains nothing. The keyboard controls only itself.
- Reproducibility of F-Droid's build. That is verified by their build servers, not here.
