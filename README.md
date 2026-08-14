# MTG Card Scanner

Android端末のカメラでMagic: The Gatheringのカードを読み取り、所持カードを整理するアプリです。

## 主な機能

- CameraXによるカード撮影
- ML Kit OCRによるカード名、セットコード、コレクター番号、言語の候補抽出
- Scryfall APIによるカード情報、カード画像、USD参考価格の取得
- 認識結果の手動修正と再検索
- 通常版・Foil版の区別と端末内保存
- MTG Arenaを意識した2列コレクショングリッド
- カード検索、所持枚数の変更、削除、参考総額の表示

## 画面

1. スキャン画面 — 撮影、OCR、Scryfall照合、コレクション登録
2. コレクション画面 — 保存カードの検索、確認、枚数管理

## ビルド

Android Studioでプロジェクトを開き、Android SDK 35とJDK 17を設定してください。

```text
minSdk: 26
targetSdk: 35
```

## 外部サービス

カード情報・画像・価格には[Scryfall API](https://scryfall.com/docs/api)を使用します。価格はScryfallが提供するUSD参考価格であり、実際の売買価格や日本国内価格を保証するものではありません。

このアプリは非公式のファンプロジェクトであり、Wizards of the CoastおよびScryfallとは提携していません。
