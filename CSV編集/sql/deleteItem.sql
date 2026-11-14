-- 商品を1行削除して CSV へ書き戻す
CREATE LOCAL TEMPORARY TABLE htmldb_csv_edit (
    id VARCHAR(20) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    category VARCHAR(40),
    qty VARCHAR(20),
    note VARCHAR(200)
) AS
SELECT id, name, category, qty, note
  FROM CSVREAD('CSV編集/items.csv', NULL, 'charset=UTF-8');

DELETE FROM htmldb_csv_edit WHERE id = CAST(:id AS VARCHAR);

CREATE LOCAL TEMPORARY TABLE htmldb_csv_written AS
SELECT CSVWRITE('CSV編集/items.csv',
'SELECT id AS "id", name AS "name", category AS "category", qty AS "qty", note AS "note" FROM htmldb_csv_edit ORDER BY CAST(id AS INT)',
'charset=UTF-8') AS written;
