-- 商品を1行追加して CSV へ書き戻す
-- 同じ接続の中だけで使う一時表に読み、変更してから CSVWRITE する
CREATE LOCAL TEMPORARY TABLE htmldb_csv_edit (
    id VARCHAR(20) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    category VARCHAR(40),
    qty VARCHAR(20),
    note VARCHAR(200)
) AS
SELECT id, name, category, qty, note
  FROM CSVREAD('CSV編集/items.csv', NULL, 'charset=UTF-8');

INSERT INTO htmldb_csv_edit (id, name, category, qty, note)
VALUES (CAST(:id AS VARCHAR), :itemName, :itemCategory, CAST(:qty AS VARCHAR), :note);

CREATE LOCAL TEMPORARY TABLE htmldb_csv_written AS
SELECT CSVWRITE('CSV編集/items.csv',
'SELECT id AS "id", name AS "name", category AS "category", qty AS "qty", note AS "note" FROM htmldb_csv_edit ORDER BY CAST(id AS INT)',
'charset=UTF-8') AS written;
