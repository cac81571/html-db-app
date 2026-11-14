-- 登録時に商品IDが空欄なら、CSV 上の最大値 + 1 を入力欄へ入れる
SELECT COALESCE(MAX(CAST(id AS INT)), 0) + 1 AS id
  FROM CSVREAD('CSV編集/items.csv', NULL, 'charset=UTF-8')
