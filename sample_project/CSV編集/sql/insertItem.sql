-- 商品を1行追加して CSV へ書き戻す
-- 同じ接続の中だけで使う一時表に読み、変更してから CSVWRITE する
CREATE LOCAL TEMPORARY TABLE htmldb_csv_edit AS 
SELECT * FROM CSVREAD('sample_project/CSV編集/items.csv', NULL, 'charset=UTF-8 caseSensitiveColumnNames=true');

INSERT INTO htmldb_csv_edit ("id", "name", "category", "qty", "note")
VALUES (CAST(:id AS VARCHAR), :itemName, :itemCategory, CAST(:qty AS VARCHAR), :note);

CREATE LOCAL TEMPORARY TABLE htmldb_csv_written AS
SELECT CSVWRITE('sample_project/CSV編集/items.csv',
'SELECT * FROM htmldb_csv_edit ORDER BY CAST("id" AS INT)',
'charset=UTF-8') AS written;
