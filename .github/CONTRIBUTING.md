# Contributing to Kicker

Bug reports, feature requests, translations, accessibility improvements, documentation, and code contributions are welcome. Please follow the [Code of Conduct](CODE_OF_CONDUCT.md) when participating.

## Report a bug or suggest a feature

1. Check [existing issues](https://github.com/lechixy/Kicker/issues) and the [roadmap](https://app.notion.com/p/lechixy/3f79ce1d06e580d99d7ac8f36ea9b953?v=2689ce1d06e58207971c0840ab5893c6&source=copy_link) for related work.
2. Choose the relevant [issue template](https://github.com/lechixy/Kicker/issues/new/choose). For a bug, include the Kicker version, installation source, Android version, device model, steps to reproduce, and expected and actual behavior. Remove cookies, tokens, personal information, and private chat content from logs and screenshots.
3. Use the [Security Policy](SECURITY.md) for suspected vulnerabilities.

## Open a pull request

1. Discuss larger changes in an issue before starting. Fork the repository and make your change on a branch based on `main`.
2. Follow the [build instructions](../README.MD#build-from-source) for Android Studio, JDK 21, and the required Android SDK.
3. For code changes, run the relevant tests. Use `./gradlew testDebugUnitTest` for unit tests and `./gradlew connectedDebugAndroidTest` for instrumented tests with a connected device or emulator. On Windows, use `gradlew.bat` with the same tasks.
4. For UI changes, check the affected screens on a phone and a tablet when applicable. For translation changes, follow the [language support instructions](../README.MD#language-support). When shared documentation changes, update the corresponding English and Turkish text where both versions cover it.
5. Open a [pull request](https://github.com/lechixy/Kicker/pulls) against `main`. Keep it focused, explain the change, link related issues, and describe how you verified it.
