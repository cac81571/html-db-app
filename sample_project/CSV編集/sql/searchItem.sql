-- 商品CSVの検索。H2 の CSVREAD でファイルを表として読む（データベースには表を作らない）
-- ファイルの場所は、アプリを起動したフォルダからの相対パス。文字コードは UTF-8
SELECT id       AS "商品ID",
       name     AS "品名",
       category AS "分類",
       qty      AS "数量",
       note     AS "メモ"
  FROM CSVREAD('sample_project/CSV編集/items.csv', NULL, 'charset=UTF-8')
 WHERE (CAST(:name AS VARCHAR) IS NULL OR name LIKE '%' || CAST(:name AS VARCHAR) || '%')
   AND (CAST(:category AS VARCHAR) IS NULL OR category = CAST(:category AS VARCHAR))
   AND (CAST(:noteQ AS VARCHAR) IS NULL OR note LIKE '%' || CAST(:noteQ AS VARCHAR) || '%')
 ORDER BY CAST(id AS INT)
