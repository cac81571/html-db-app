-- 顧客の更新（ボタンが「更新」のとき）
-- 画面の name="in_id" / "in_newName" / "in_newPref" / "in_email" / "in_joinedOn"
-- 顧客ID は編集時に読み取り専用。この ID の行を上書きします。
UPDATE customer
   SET name      = :newName,
       pref      = :newPref,
       email     = :email,
       joined_on = :joinedOn
 WHERE id = :id
