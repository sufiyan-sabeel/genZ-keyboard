# Security policy

GenZ Keyboard is an input method. It sits in the path between a person's fingers and everything
they type, so a defect here can expose text that the person never intended to share. Reports are
taken seriously and answered.

## Reporting a vulnerability

**Do not open a public issue for a security problem.** A public issue tells everyone how to
exploit the flaw before a fix exists, and in a keyboard the flaw is usually "someone else can
read what you type".

Use GitHub's private reporting instead:

1. Open <https://github.com/sufiyan-sabeel/genZ-keyboard/security/advisories/new>.
2. Describe the problem, the version you tested and the steps to reproduce it.
3. If you have a proof of concept, attach it there — never in a public issue, a pull request or a
   discussion thread.

If private reporting is unavailable to you, open a public issue that says only *"I would like to
report a security issue privately, please contact me"*, with no technical detail. That is a
request for a channel, not a disclosure.

There is no dedicated security email address for this project, and this document will not invent
one. GitHub private advisories are the supported route.

> **Maintainer note.** GitHub's private vulnerability reporting is **not yet enabled** on this
> repository, so the advisory form above does not exist until it is. Enable it under
> *Settings → Code security → Private vulnerability reporting*. Until then the fallback — a public
> issue asking for a private channel, with no detail — is the only route, and it leaks nothing.

## What to include

- Affected version (`Settings → About`) and Android version.
- Device manufacturer and model, if the issue is device-specific.
- The smallest reproduction you can manage.
- What an attacker gains, in one sentence.

**Redact anything you typed.** Logs and screenshots from a keyboard can contain the reporter's own
private text. Replace it, do not attach it.

## What to expect

| Stage | Target |
| --- | --- |
| Acknowledgement | within 7 days |
| Initial assessment (severity, whether it is in scope) | within 14 days |
| Fix or mitigation plan | as soon as the assessment allows |
| Public disclosure | coordinated with you, after a fix is available |

This is a volunteer-maintained project, so these are targets rather than guarantees. If a report
goes unanswered for two weeks, comment on it once to ask for a status.

## Scope

In scope — anything that breaks a claim the project makes:

- Typed text, clipboard contents or the personal dictionary leaving the device other than through
  a network path documented in [PRIVACY.md](PRIVACY.md).
- A way to make the keyboard send text while incognito is on, or in a password field.
- Bypassing the plugin restrictions (HTTPS only, private addresses refused, rate limit).
- Bypassing the community backend's row-level security, or reading another account's data.
- An update path that could install or offer an APK from anywhere other than this repository's
  own release downloads.
- Extraction of a stored API key.
- Memory corruption or remote code execution in the bundled JavaScript engine (Rhino).

Out of scope:

- The behaviour of a third-party AI, GIF or translation provider, once text has been sent to it by
  an action the user took. That is described in PRIVACY.md.
- A rooted or compromised device, or a malicious keyboard installed alongside this one.
- Reports produced only by an automated scanner with no demonstrated impact.
- The artwork rights question. That is a licensing matter, recorded in
  [docs/artwork-provenance.md](docs/artwork-provenance.md), not a vulnerability.

## Supported versions

Fixes are shipped in the latest release. Only the most recent release is supported; older APKs are
not patched.

## Safe harbour

Research carried out in good faith, on your own device or an account you control, that respects
this policy will not be pursued. Do not test against other people's accounts, data or devices, and
do not run a denial-of-service test against the community backend.
