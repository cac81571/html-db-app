-- 商品を1行削除して CSV へ書き戻す
CREATE LOCAL TEMPORARY TABLE htmldb_csv_edit AS 
SELECT * FROM CSVREAD('sample_project/CSV編集/items.csv', NULL, 'charset=UTF-8 caseSensitiveColumnNames=true');

DELETE FROM htmldb_csv_edit WHERE "id" = CAST(:id AS VARCHAR);

CREATE LOCAL TEMPORARY TABLE htmldb_csv_written AS
SELECT CSVWRITE('sample_project/CSV編集/items.csv',
'SELECT * FROM htmldb_csv_edit ORDER BY CAST("id" AS INT)',
'charset=UTF-8') AS written;
