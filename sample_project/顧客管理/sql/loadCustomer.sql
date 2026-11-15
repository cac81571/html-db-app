-- 編集ボタン。一覧の顧客ID（data-row-key）だけを :id に受け、登録フォームの項目名で返す
-- 列の別名が name="in_別名" に入る。一覧の表示列（顧客ID、名前…）とは別でよい
SELECT id        AS "id",
       name      AS "newName",
       pref      AS "newPref",
       email     AS "email",
       joined_on AS "joinedOn"
  FROM customer
 WHERE id = :id
