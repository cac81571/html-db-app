-- 編集ボタン。一覧の商品IDだけを :id に受け、登録フォーム用の列を返す
SELECT id       AS "id",
       name     AS "itemName",
       category AS "itemCategory",
       qty      AS "qty",
       note     AS "note"
  FROM CSVREAD('sample_project/CSV編集/items.csv', NULL, 'charset=UTF-8')
 WHERE id = :id
