-- 登録時に顧客IDが空欄なら、この1件を入力欄へ入れる
SELECT COALESCE(MAX(id), 0) + 1 AS id FROM customer
