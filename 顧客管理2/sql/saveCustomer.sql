-- 顧客の登録または更新（ボタン「登録／更新」）
-- 画面の name="in_id" / "in_newName" / "in_newPref" / "in_email" / "in_joinedOn"
-- 顧客ID が空欄（NULL）なら「最大値 + 1」で新規登録、入っていればその ID を上書きします。
-- 検索欄の in_name / in_pref とは別名にして、同じ画面に置いても値が混ざらないようにしています。

UPDATE customer
   SET name = :newName,
       pref = :newPref,
       email = :email,
       joined_on = :joinedOn
 WHERE id = :id;

INSERT INTO customer (id, name, pref, email, joined_on)
SELECT COALESCE(CAST(:id AS INT), (SELECT COALESCE(MAX(id), 0) + 1 FROM customer)),
       :newName, :newPref, :email, :joinedOn
 WHERE CAST(:id AS INT) IS NULL;
