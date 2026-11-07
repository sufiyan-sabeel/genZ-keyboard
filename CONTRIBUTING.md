# Contributing to GenZ Keyboard

Thanks for helping. This project is a fork of
[Expressive Keyboard](https://github.com/kotlalipopus/Expressive-Keyboard); upstream fixes
that apply to both projects are best offered upstream as well.

## Before you start

- Search existing issues first.
- For anything larger than a bug fix, open an issue describing the change and why it belongs
  in a keyboard, before writing the code.

## Ground rules

1. **Privacy is not negotiable.** No analytics, no tracking, no ad SDKs, no remote logging, no
   network call that carries typed text. A pull request that adds any of these will be closed.
2. **No fake functionality.** Do not add a button that does nothing, a settings toggle that
   changes no behaviour, or sample data that pretends to be real.
3. **No new dependencies without a reason.** Every dependency is a permission and an attack
   surface. Say what it buys in the pull request.
4. **Keep the architecture.** Settings are data (`KeyboardSettings`), themes are derived from a
   seed colour, text goes out through `InputConnection`. Follow those patterns.
5. **No secrets in the repository.** No keystores, no API keys, no `local.properties`.

## Building and testing

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

Add a unit test for anything you fix in prediction, autocorrection, glide typing or dictionary
loading — those are testable on the JVM without a device. State clearly in your pull request
what you ran and what you could only test by hand.

## Code style

- Kotlin, 4-space indent, `kotlin.code.style=official`.
- Compose: keep composables small and stateless where practical; hoist state.
- Name things for what they do. Comments explain *why*, not *what*.

## Commit messages

`type: short summary` — `fix:`, `feat:`, `perf:`, `docs:`, `refactor:`, `chore:`. Explain the
user-visible effect in the body if it is not obvious.

## Reporting a bug

Include: device model, Android version, app version, steps to reproduce, and what you expected.
Do not attach logs containing text you typed — redact first.

## License

By contributing you agree your work is licensed under the MIT License in `LICENSE`.
