-- 件名の部分一致。空欄なら全件
SELECT id    AS "番号",
       title AS "件名"
  FROM ticket
 WHERE (CAST(:titleQ AS VARCHAR) IS NULL OR title LIKE '%' || CAST(:titleQ AS VARCHAR) || '%')
 ORDER BY id
