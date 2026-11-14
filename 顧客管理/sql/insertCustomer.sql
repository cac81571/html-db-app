-- 顧客の登録（ボタンが「登録」のとき）
-- 画面の name="in_id" / "in_newName" / "in_newPref" / "in_email" / "in_joinedOn"
INSERT INTO customer (id, name, pref, email, joined_on)
VALUES (CAST(:id AS INT), :newName, :newPref, :email, :joinedOn)
