# TokiToki Reader

![platform](https://img.shields.io/badge/platform-Android_8.0%2B-3DDC84.svg?logo=android)
![version](https://img.shields.io/badge/version-0.4_alpha-orange)
![license](https://img.shields.io/badge/license-MIT-blue)

[Русский](README.md) · [English](README.en.md) · **日本語**

Mastodon と Misskey のタイムラインをひとつのアプリで読むためのリーダーです。統合タイムライン、アカウントごとのタブ、そして可能な範囲でサーバーへ既読を反映します。

> [!WARNING]
> バージョン 0.4 alpha。未実装の機能が多く、粗い部分もあります。Telegram には未対応です。

## 機能

**タイムライン**
- 接続したすべてのアカウントを時系列で統合表示
- アカウントごとのタブ、スワイプで切り替え
- 統合表示では投稿ごとに出所を表示（ハンドルの下に `Misskey:user`）
- スクロールに応じて 10 件ずつ読み込み
- ローカルキャッシュにより、オフラインでも閲覧可能
- オフライン保存：投稿単位、またはタブ長押しでタイムライン全体
- CW、ブースト、引用、返信に対応
- タイムライン検索とフィルター（メディアあり・未読・ブースト以外）
- 作者のプロフィールをアプリ内で表示（アイコンをタップ）
- 投稿・返信・ブースト・リアクション（公開範囲と閲覧注意に対応）

**既読の同期**
- 実際に画面に表示された時点で既読として扱います（取得時ではありません）
- Mastodon: `markers` でタイムライン位置をサーバーへ送信
- Misskey: 端末内のみ。サーバーに該当する API がありません

**画像**
- タイムライン内で正しい縦横比、全画面表示とズーム
- 下方向スワイプで閉じる
- ダウンロードフォルダーへ保存

**アカウント**
- Mastodon と Misskey を複数同時に接続可能
- アイコン付きのフォロー一覧、JSON へのエクスポート
- 削除せずに一時的に無効化可能

**外観**
- Material You、ダイナミックカラー対応（Android 12 以降）
- テーマ: システム / ライト / ダーク / AMOLED（`#000000`）
- 文字サイズ 4 段階
- 日本語・英語・ロシア語

## インストール

APK は [リリース](https://github.com/Zalexanninev15/TokiToki-Reader/releases) から入手できます。`main` への push ごとに自動ビルドされます。

自分でビルドする場合（JDK 17 が必要）:

```bash
gradle wrapper --gradle-version 8.10.2
./gradlew :app:assembleDebug
```

## アカウントの接続

**Mastodon** — インスタンスのアドレスを入力し、ブラウザーで承認します。事前のアプリ登録は不要です。

**Misskey** — MiAuth で同様に接続します。Sharkey、Iceshrimp、CherryPick にも対応しており、フォークとバージョンは自動判別されます。

## 制限事項

| サービス | 既読 |
|---|---|
| Mastodon | タイムライン位置。すべてのクライアントで共有されます |
| Misskey | 端末内のみ |

> [!NOTE]
> Mastodon では投稿ごとのフラグではなく、タイムライン全体に対する 1 つの位置です。これは API の仕様です。他のクライアントで進めた位置を戻さないよう、位置が後退することはありません。

> [!IMPORTANT]
> Misskey にはタイムラインの既読を記録する API がありません。通知とメンションのみが既読になります。また、タイムラインは概ね直近 30 日分までで、それより古い履歴は取得できません。

Pleroma、Akkoma、GoToSocial は `markers` に対応していない場合があり、そのときは端末内のみの記録に切り替わります。

## 未実装

Telegram、通知、投稿時の添付。

## フィードバック

[Issue](https://github.com/Zalexanninev15/TokiToki-Reader/issues/new) にサービス名、インスタンス、Android のバージョン、発生した状況をご記入ください。クラッシュの場合は `adb logcat` を添付してください。

作者: Mastodon [@voltmor](https://mastodon.ml/@voltmor)、Sharkey [@qkon4](https://shitpost.poridge.club/@qkon4)。[連絡先](https://z15.neocities.org/contacts) · [支援](https://z15.neocities.org/donate/)

## ライセンス

MIT.
