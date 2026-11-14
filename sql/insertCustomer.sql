-- 顧客登録
-- 画面の name="in_id" / "in_newName" / "in_newPref" / "in_email" / "in_joinedOn" がバインドされます。
-- 顧客ID が空欄（NULL）なら「最大値 + 1」で採番します。
INSERT INTO customer (id, name, pref, email, joined_on)
VALUES (COALESCE(CAST(:id AS INT), (SELECT COALESCE(MAX(id), 0) + 1 FROM customer)),
        :newName, :newPref, :email, :joinedOn)
