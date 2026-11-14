# HTML DB App

**画面は HTML、処理は SQL を書くだけ** で動く、デスクトップ用の Web DB アプリです。

- 技術: Java 17+ / Groovy 4 / Maven / Swing + FlatLaf / JavaFX WebView（Swing に JFXPanel で埋め込み）
- 利用者は `screens/` に HTML、`sql/` に SQL を置くだけ。Java のコードを書く必要はありません。
- 画面項目と SQL パラメータは **ネーミングルール** で自動的に結び付きます。

---

## 1. ネーミングルール

| 画面（HTML）の書き方 | 意味 |
|---|---|
| `<button id="btn_名前">` | クリックすると `sql/名前.sql` を実行 |
| `<input name="in_xxx">`（select / textarea も可） | SQL の名前付きパラメータ `:xxx` にバインド |
| `<div id="out_名前"></div>` | `名前.sql` の結果を表示（SELECT → 表、INSERT/UPDATE/DELETE → 件数） |
| `<div id="msg"></div>` | 成功・エラーメッセージを表示（任意） |

例:

```html
<input name="in_customerId" data-type="int">
<button id="btn_searchCustomer">検索</button>
<div id="out_searchCustomer"></div>
```

```sql
-- sql/searchCustomer.sql
SELECT * FROM customer WHERE id = :customerId
```

### 追加の属性（任意）

| 属性 | 説明 |
|---|---|
| `data-type="int"` | 入力値の型。`int` / `number` / `date` / `datetime` / `time` / `bool` / `string`。`<input type="number">`→number、`type="date"`→date は自動 |
| `data-then="searchCustomer"` | 成功したら続けて `sql/searchCustomer.sql` を実行（カンマ区切りで複数可）。登録後に一覧を更新する時に便利 |
| `data-confirm="削除しますか？"` | 実行前に確認ダイアログを表示 |

### バインドの細かいルール

- ボタンを押すと、**画面内のすべての** `name="in_*"` 項目の値が集められます（form の内外は問いません）。
- **空欄は NULL** としてバインドされます。「空欄なら条件なし」は次のように書けます。
  ```sql
  WHERE (CAST(:name AS VARCHAR) IS NULL OR name LIKE '%' || CAST(:name AS VARCHAR) || '%')
  ```
- SQL にあるのに画面に無いパラメータは NULL がバインドされ、ログに警告が出ます。
- 同じパラメータを SQL 内で何度使っても OK。文字列リテラル内やコメント内の `:xxx` は無視されます（PostgreSQL の `::` キャストも OK）。
- チェックボックスはチェック時に value（未指定なら `true`）、複数チェックや複数選択 select はカンマ区切りの文字列になります。
- 1 つの SQL ファイルに `;` 区切りで複数の文を書けます（順に実行し、最後の SELECT の結果を表示）。
- SELECT の表示は最大 1000 行です。
- `btn_` / `out_` の名前に使えるのは英数字・`_`・`-` だけです。
- 画面の JavaScript から `window.__htmldb.run('searchCustomer')` で直接実行することもできます。
  実行完了時には `document` に `htmldb:done` イベント（`detail: {name, ok, message}`）が飛びます。

### 結果表示の CSS クラス

`table.htmldb-result`（結果表）、`td.htmldb-null`（NULL セル）、`.htmldb-count`（件数）、`.htmldb-update`（更新件数）、
`.htmldb-error`（エラー）、`#msg.htmldb-msg-ok` / `#msg.htmldb-msg-error`（メッセージ）。サンプルの `screens/style.css` を参照してください。

---

## 2. Windows での実行方法

### 必要なもの

- **Java 17 以上**（例: [Eclipse Temurin 21](https://adoptium.net/ja/)。インストール時に「PATH に追加」を有効に）
- ビルドする場合のみ **Maven 3.9 以上**（[Apache Maven](https://maven.apache.org/download.cgi) を展開して `bin` を PATH に追加）

コマンドプロンプトで確認:

```bat
java -version
mvn -v
```

### A. 同梱の Windows 用 JAR をそのまま使う（ビルド不要）

1. zip を展開します（例: `C:\htmldbapp`）。
2. `run.bat` をダブルクリック、またはコマンドプロンプトで:
   ```bat
   cd C:\htmldbapp
   java -jar dist\htmldbapp-1.0.0-win.jar
   ```

### B. 自分でビルドして使う

```bat
cd C:\htmldbapp
mvn package
java -jar target\htmldbapp-1.0.0-all.jar
```

`target\htmldbapp-1.0.0-all.jar` が全部入りの実行可能 JAR です（ビルドした OS 用の JavaFX が自動で入ります）。
開発中は `mvn compile exec:java` でも起動できます。

> 起動時に `WARNING: Unsupported JavaFX configuration: classes were loaded from 'unnamed module'` と表示されますが、
> fat JAR で JavaFX を使う場合の既知の警告で、動作に問題はありません。

### 初めて起動したら

1. 左の画面一覧から `customer.html` を選択（最初は自動で選ばれます）。
2. 接続先が「サンプル H2」になっていることを確認（DB は `%USERPROFILE%\.htmldbapp\sample.mv.db` に作られます）。
3. 「サンプルテーブルを作成」ボタン → テーブル作成・データ投入・検索まで実行されます。
4. 検索・登録・削除を試してみてください。

`screens/` の HTML や `sql/` の SQL を編集したら、**F5（再読込）** で反映されます（SQL は毎回ファイルから読むので再読込不要）。

---

## 3. フォルダ構成

```
htmldbapp/
├─ screens/            … 画面 HTML（ここに .html を追加すると一覧に出ます）。CSS・画像も置けます
│   ├─ customer.html
│   └─ style.css
├─ sql/                … SQL（ボタン名 = ファイル名）
│   ├─ initSample.sql
│   ├─ searchCustomer.sql
│   ├─ insertCustomer.sql
│   └─ deleteCustomer.sql
├─ drivers/            … 追加 JDBC ドライバの JAR 置き場（再ビルド不要）
├─ dist/               … ビルド済み Windows 用 JAR
├─ src/main/java       … Swing 画面・接続管理・WebView ブリッジ（Java）
├─ src/main/groovy     … ネーミングルール・SQL 実行（Groovy / groovy.sql.Sql）
├─ src/main/resources  … 画面に注入する配線用 JavaScript（htmldb-bridge.js）
├─ src/test/java       … 単体テスト（JUnit 5 + インメモリ H2）
├─ pom.xml
├─ run.bat / run.sh
└─ README.md
```

作業フォルダ（`screens/` `sql/` `drivers/`）は **起動したカレントディレクトリ** です。
`java -Dhtmldbapp.home=D:\myapp -jar ...` で変更できます。
`screens/` や `sql/` が無いフォルダで起動すると、サンプルが自動で展開されます。

設定は `%USERPROFILE%\.htmldbapp\` に保存されます（`-Dhtmldbapp.config=...` で変更可）。

| ファイル | 内容 |
|---|---|
| `connections.json` | 接続先一覧（パスワードは AES-256-GCM で暗号化） |
| `secret.key` | 暗号鍵（初回起動時に自動生成。**削除すると保存済みパスワードは復号できなくなります**） |
| `logs/app.log` | 実行ログ（SQL・パラメータ・エラー） |

---

## 4. DB 接続先の管理

メニュー「接続 → 接続先の管理...」またはツールバーの「管理...」から、接続先の追加・複製・編集・削除・接続テストができます。
「テンプレート」で H2 / PostgreSQL / MySQL / Oracle / SQL Server の URL 例を入力できます。
使う接続先はツールバーのコンボボックスで選択します（最後に選んだものを記憶します）。

### JDBC ドライバの追加

H2 は同梱済みです。その他の DB は次のどちらかで追加します。

1. **再ビルドなし**: ドライバの JAR（`ojdbc11.jar`、`postgresql-42.x.jar` など）を `drivers/` に置き、
   アプリを再起動（またはメニュー「接続 → drivers フォルダを再読込」）。
2. **JAR に同梱**: `pom.xml` の PostgreSQL / MySQL / Oracle / SQL Server の依存のコメントを外して `mvn package`。

ドライバクラス欄は通常空欄で OK（URL から自動判定）。判定できない場合は `oracle.jdbc.OracleDriver` などを入力してください。

---

## 5. ビルドとテスト

```bash
mvn package                       # コンパイル + テスト + target/htmldbapp-1.0.0-all.jar 作成
mvn test                          # テストのみ
mvn package -Djavafx.platform=win # 他 OS（win / linux / mac / mac-aarch64）用の JAR を作る
java -jar target/htmldbapp-1.0.0-all.jar --smoke-test   # サンプル画面で自動動作確認（成功で終了コード 0）
```

テスト内容: ネーミングルール（名前の取り出し・パス不正の拒否）、SQL 解析（パラメータ抽出・複数文分割）、
型変換、インメモリ H2 での実行（INSERT/SELECT・NULL 補完・複数文・行数上限・エラー）、
同梱サンプル SQL を使った検索・登録・削除の一連の流れ、接続先の暗号化保存。

---

## 6. 仕組み（概要）

1. 画面を WebView で読み込み終わると、アプリが `window.htmldbBridge`（Java オブジェクト）と配線用 JS を注入します。
2. 配線用 JS は `btn_*` のクリックを拾い、`in_*` の値と型を JSON にして `htmldbBridge.execute(名前, JSON)` を呼びます。
3. Java 側は別スレッドで `sql/名前.sql` を読み、`groovy.sql.Sql` の名前付きパラメータで実行します。
4. 結果を HTML（表または件数）に変換し、`out_名前` と `msg` に表示します。エラーは `msg` とログ欄・ログファイルに出ます。

## 7. 制限事項（MVP）

- SQL 実行のたびに接続を開閉します（コネクションプールなし）。トランザクションは自動コミット（複数文ファイルも 1 文ずつコミット）。
- 結果の表示形式は表のみ（行クリックで入力欄へ反映、ページングなどは未対応）。
- 型指定のない値は文字列としてバインドされます。PostgreSQL など型に厳しい DB では `data-type` を付けるか SQL 側で `CAST` してください。
- ストアドプロシージャの OUT パラメータ、BLOB の表示・登録は未対応です。
- `secret.key` と `connections.json` の両方を持ち出されるとパスワードは復号できます（OS ログインユーザー単位の保護です）。
- 画面 HTML は信頼できるものだけを置いてください（HTML の JavaScript からも SQL 実行ブリッジを呼べます）。
