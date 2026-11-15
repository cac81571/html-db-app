# HTML DB App

**画面は HTML、処理は SQL を書くだけ** で動く、デスクトップ用の Web DB アプリです。

- 技術: Java 17+ / Groovy 4 / Maven / Swing + FlatLaf / JavaFX WebView（Swing に JFXPanel で埋め込み）
- 利用者は画面フォルダ（例: `顧客管理/`）に HTML、その中の `sql/` に SQL を置くだけ。Java のコードを書く必要はありません。
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
| `data-open="customerForm.html"` | その HTML 画面を開く（`id` が `btn_` ではないボタン）。SQL は実行しない |
| `data-from="out_searchCustomer"` | 開くとき、その結果欄で選択している行の値を渡す |
| `data-fields="顧客ID=id,名前=newName"` | 結果の列名を、同じ画面の `name="in_id"` などに対応付ける |
| `data-row-edit="#customerForm"` | 各行の先頭に「編集」。押すと `data-fields` の対応で入力欄へ入れる（値はスクロール先） |
| `data-row-delete="deleteCustomer"` | となりに「削除」。その行の値で SQL を実行する。成功し、登録フォームのキーが削除した行と同じなら、そのフォームを空にして「登録」に戻す |
| `data-row-then="searchCustomer"` | 行の削除が成功したあとに続けて実行する |
| `data-row-confirm="削除しますか？"` | 行を削除する前の確認 |
| `data-clear="#customerForm"` | その要素の中にある `in_*` を空にする。キーは入力可に戻り、ボタンは「登録」 |
| `data-key` | 手入力のキー。ラベルに ＊ を付ける。編集中は変更不可。空欄なら登録結果と同じ位置に「必須です」と出す |
| `data-auto="nextCustomerId"` | 登録のたびに `sql/nextCustomerId.sql` の結果をキーへ入れる。欄は非活性で、＊ は付かない。手入力とは併用しない |
| `data-increment` | DB の identity で採番するキー。欄は非活性で、＊ は付かない。登録 SQL ではその列を書かず、採番された値を後から欄へ入れる |
| `data-insert="insertCustomer"` | 登録モードで実行する SQL。ボタン文言は「登録」 |
| `data-update="updateCustomer"` | 更新モードで実行する SQL。ボタン文言は「更新」 |
| `data-keep`（`<body>`） | 別画面へ移る直前の入力値・結果・選択行を覚え、戻ったときにそのまま表示する。F5（再読込）では破棄する |

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

### 一覧と登録を同じ画面に置く

サンプルの `customer.html` は、上に一覧、下に登録です。このアプリは画面を1枚ずつ表示するので、上下に並べて見せるときは1つの HTML にまとめます。

検索条件と登録項目は別の名前にします。同じ `in_name` が2つあると、ボタンを押したときに後の値で上書きされます。サンプルでは検索が `in_name`、登録の名前が `in_newName` です。

```html
<div id="out_searchCustomer"
     data-fields="顧客ID=id,名前=newName,都道府県=newPref,メール=email,登録日=joinedOn"
     data-row-edit="#customerForm"
     data-row-delete="deleteCustomer"
     data-row-then="searchCustomer"
     data-row-confirm="この顧客を削除します。よろしいですか？"></div>
```

「編集」はその行の値を下の入力欄へ入れます。手入力のキー（`data-key` のみ）のラベルには ＊ が付き、編集中は変更できません。空欄や登録の失敗は、登録パネルの上に1か所だけ出ます。`data-auto` のキーは登録のたびに SQL の結果（サンプルは最大値 + 1）が入り、欄は非活性です。列自体が identity のときは `data-increment` を付け、登録 SQL からその列を外します。サンプルは `sample_project/顧客管理/identity.html`（表 `ticket`）です。どちらも手入力とは併用しません。ボタンは「更新」になり `sql/updateCustomer.sql` を実行します。「クリア」で「登録」に戻り、`sql/insertCustomer.sql` を実行します。

別の HTML へ移すときは、`btn_` ではないボタンに `data-open="別画面.html"` を付けます。`<body data-keep>` を付けると、離れる前の入力と結果を覚えて、戻ったときに復元します。

### 結果表示の CSS クラス

`table.htmldb-result`（結果表）、`td.htmldb-null`（NULL セル）、`.htmldb-count`（件数）、`.htmldb-update`（更新件数）、
`.htmldb-error`（エラー）、`#msg.htmldb-msg-ok` / `#msg.htmldb-msg-error`（メッセージ）。サンプルの `sample_project/顧客管理/style.css` を参照してください。

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

1. 左の「プロジェクトフォルダ」は、最初は起動フォルダの `sample_project` です。ツリーはその直下の画面フォルダ（`顧客管理` など）から表示します。フォルダの入れ子は、画面フォルダのグループになります。`customer.html` は自動で開きます。
2. 接続先が「サンプル H2」になっていることを確認（DB は `%USERPROFILE%\.htmldbapp\sample.mv.db` に作られます）。
3. 「サンプルテーブルを作成」ボタン → テーブル作成・データ投入・検索まで実行されます。
4. 下の欄に入力して「登録」で保存します。登録に成功するとボタンは「更新」になります。行の「編集」でもその顧客を読み込めます。顧客IDは変更できません。行の「削除」で消せます。欄を空にして「登録」に戻すときは「クリア」です。

画面フォルダの HTML や `sql/` の SQL を編集したら、**F5（再読込）** で反映されます（SQL は毎回ファイルから読むので再読込不要）。「追加」「削除」はプロジェクトフォルダへの参照だけを増やしたり外したりします。フォルダ自体は作りませんし、消しません。選んだプロジェクトのルート名はツリーに出さず、中のフォルダから表示します。フォルダは何階層でも置けます。画面フォルダをクリックすると、その中の HTML を表示します。HTML が複数あるときは、ツリーの先頭のファイルです。「絞り込み」の右にある「フォルダ」「html」「sql」で、文字と比べる対象を選べます。フォルダ名が一致したときはその中身をすべて表示します。Esc で文字を消します。「全て展開」「全て折りたたむ」でツリーの開閉ができます。フォルダを右クリックするとエクスプローラで開けます。ファイルを右クリックすると、外部エディタまたはサクラエディタで開けます。サクラエディタの場所は「ツール → サクラエディタのパス...」で指定できます。SQL のダブルクリックは外部エディタです。

---

## 3. フォルダ構成

```
起動フォルダ/              … アプリを起動した場所
├─ sample_project/         … 初期のプロジェクトフォルダ
│   ├─ 顧客管理/           … 画面フォルダ
│   │   ├─ customer.html       … 一覧と登録
│   │   ├─ identity.html       … DB の identity で番号を採る
│   │   ├─ style.css
│   │   └─ sql/                … この画面の SQL（ボタン名 = ファイル名）
│   │       ├─ initSample.sql
│   │       ├─ searchCustomer.sql
│   │       ├─ saveCustomer.sql    … 登録／更新
│   │       ├─ insertCustomer.sql
│   │       ├─ updateCustomer.sql
│   │       ├─ deleteCustomer.sql
│   │       ├─ nextCustomerId.sql  … 空欄の顧客IDへ入れる番号
│   │       ├─ initTicket.sql
│   │       ├─ searchTicket.sql
│   │       ├─ insertTicket.sql      … 番号列を書かない
│   │       ├─ updateTicket.sql
│   │       └─ deleteTicket.sql
│   ├─ 顧客管理2/          … 顧客管理のコピー
│   └─ CSV編集/            … CSV を H2 の CSVREAD / CSVWRITE で検索・編集するサンプル
│       ├─ items.html
│       ├─ items.csv           … 商品データ（UTF-8）
│       ├─ style.css
│       └─ sql/
│           ├─ searchItem.sql
│           ├─ insertItem.sql
│           ├─ updateItem.sql
│           ├─ deleteItem.sql
│           └─ nextItemId.sql
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

作業フォルダは **起動したカレントディレクトリ** です。初期のプロジェクトフォルダは、その中の `sample_project` です。
`java -Dhtmldbapp.home=D:\myapp -jar ...` で作業フォルダを変更できます。
`sample_project` に画面フォルダが無い状態で起動すると、サンプル「顧客管理」と「CSV編集」が自動で展開されます。古い `screens/` と `sql/` がある場合は `sample_project/顧客管理` へコピーします。

`CSV編集` はデータベースに表を作らず、H2 の `CSVREAD` で `items.csv` を検索します。登録・更新・削除は同じファイルを読み、変更した内容を `CSVWRITE` で書き戻します。パスは起動したフォルダからの相対パス（`sample_project/CSV編集/items.csv`）、文字コードは UTF-8 です。接続先は「サンプル H2」のままで動きます。

設定は `%USERPROFILE%\.htmldbapp\` に保存されます（`-Dhtmldbapp.config=...` で変更可）。

| ファイル | 内容 |
|---|---|
| `connections.json` | 接続先一覧（パスワードは AES-256-GCM で暗号化） |
| `secret.key` | 暗号鍵（初回起動時に自動生成。**削除すると保存済みパスワードは復号できなくなります**） |
| `logs/app.log` | 実行ログ（SQL・パラメータ・エラー） |
| `sakura-path.txt` | サクラエディタ (`sakura.exe`) のパス。未設定なら自動検出 |
| `projects.txt` | 追加したプロジェクトフォルダのパス。未作成のときは `sample_project` だけ |
| `last-folder.txt` | 最後に選んだプロジェクトフォルダ |

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
- 結果の表示形式は表のみ（ページングは未対応）。セルをクリックすると、その表示値をクリップボードへコピーします。行ごとの「編集」「削除」は `data-row-edit` / `data-row-delete`、別画面への移動は `data-open` で行います。
- 型指定のない値は文字列としてバインドされます。PostgreSQL など型に厳しい DB では `data-type` を付けるか SQL 側で `CAST` してください。
- ストアドプロシージャの OUT パラメータ、BLOB の表示・登録は未対応です。
- `secret.key` と `connections.json` の両方を持ち出されるとパスワードは復号できます（OS ログインユーザー単位の保護です）。
- 画面 HTML は信頼できるものだけを置いてください（HTML の JavaScript からも SQL 実行ブリッジを呼べます）。
