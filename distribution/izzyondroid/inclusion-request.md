# Request to include Kicker

Kicker is an independent, native Android client for Kick. It supports live streams, recordings, clips and chat. Watching and reading chat are available to guests. Following channels and sending messages require a Kick account.

- Package ID `com.lechixy.kicker`
- Android 11 or newer
- [Source code and MIT license](https://github.com/lechixy/Kicker)
- [GitHub Releases](https://github.com/lechixy/Kicker/releases)
- [Stable release v1.2.0](https://github.com/lechixy/Kicker/releases/tag/v1.2.0)
- Universal APK naming pattern `^kicker_[0-9]+\.[0-9]+\.[0-9]+_universal_release\.apk$`
- Listing metadata in `fastlane/metadata/android` in the source repository

Kicker depends on Kick's network services and sends live viewing activity to Kick. The service carries user-generated streams and chat, including gaming and streams marked mature. Some bundled reward and tier animations are derived from Kick assets. The current universal APK is listed as 58.4 MB on GitHub, above the usual 30 MB limit.

Could you clarify whether Kicker is eligible given its dependence on Kick, live viewing events, and the types of content the service carries?
