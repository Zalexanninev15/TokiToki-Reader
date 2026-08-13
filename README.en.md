# TokiToki Reader

![platform](https://img.shields.io/badge/platform-Android_8.0%2B-3DDC84.svg?logo=android)
![version](https://img.shields.io/badge/version-0.4_alpha-orange)
![license](https://img.shields.io/badge/license-MIT-blue)

[Русский](README.md) · **English** · [日本語](README.ja.md)

A reader for Mastodon and Misskey timelines in one app. A merged feed, a tab per account, and read-state pushed back to the server wherever that is possible at all.

> [!WARNING]
> Version 0.4 alpha. Plenty is missing and some of it is rough. Telegram is not supported yet.

## Features

**Feed**
- Merged timeline across every connected account, sorted by time
- A tab per account, switched by swiping
- In the merged view each post is labelled with its origin: `Misskey:user` under the handle
- Loads 10 posts at a time as you scroll
- Local cache, so the feed opens without a connection
- Saving posts for offline: one post or the whole feed via long-press on a tab
- Content warnings, boosts, quotes, replies
- Feed search and filters: with media, unread, no boosts
- Author profiles open in-app — tap an avatar
- Posting, replies, boosts and reactions, with visibility and content warnings

**Read state**
- A post counts as read once it has actually been on screen, not when it was fetched
- Mastodon: position is pushed to the server through `markers`
- Misskey: local only, the server has no such API

**Images**
- Correct aspect ratio in the feed, full screen with zoom
- Swipe down to dismiss
- Save to Downloads

**Accounts**
- Several Mastodon and Misskey accounts at once
- Subscriptions list with avatars, exportable to JSON
- Any account can be switched off without removing it

**Appearance**
- Material You with dynamic colours (Android 12+)
- Themes: system, light, dark, AMOLED (`#000000`)
- Four text sizes
- Russian, English, Japanese

## Install

Grab the APK from [releases](https://github.com/Zalexanninev15/TokiToki-Reader/releases). It is built automatically on every push to `main`.

Build it yourself (JDK 17 required):

```bash
gradle wrapper --gradle-version 8.10.2
./gradlew :app:assembleDebug
```

## Connecting accounts

**Mastodon** — enter the instance address and confirm in the browser. No prior app registration needed.

**Misskey** — the same through MiAuth. Sharkey, Iceshrimp and CherryPick work too; the app detects the fork and version itself.

## Limitations

| Service | Read state |
|---|---|
| Mastodon | Timeline position, shared with all your clients |
| Misskey | Local only |

> [!NOTE]
> On Mastodon this is one position for the whole timeline rather than a flag per post — that is how their API works. The position never moves backwards, so it will not undo progress set by another client.

> [!IMPORTANT]
> Misskey has no API for marking timeline posts as read. Only notifications and mentions are acknowledged. Misskey also serves roughly the last 30 days of the timeline; older history is unavailable.

Pleroma, Akkoma and GoToSocial may not implement `markers`, in which case the app falls back to local-only.

## Not implemented

Telegram, notifications, attachments when posting.

## Feedback

[Open an issue](https://github.com/Zalexanninev15/TokiToki-Reader/issues/new) with the service, instance, Android version and what went wrong. Attach `adb logcat` for crashes.

Author: [@voltmor](https://mastodon.ml/@voltmor) on Mastodon, [@qkon4](https://shitpost.poridge.club/@qkon4) on Sharkey. [Contact](https://z15.neocities.org/contacts) · [Donate](https://z15.neocities.org/donate/)

## License

MIT.
