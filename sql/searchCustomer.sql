-- 顧客検索
-- :name … 画面の name="in_name"、:pref … name="in_pref"
-- 空欄は NULL としてバインドされるので、「NULL なら条件なし」と書くと任意条件にできます。
SELECT id        AS "顧客ID",
       name      AS "名前",
       pref      AS "都道府県",
       email     AS "メール",
       joined_on AS "登録日"
  FROM customer
 WHERE (CAST(:name AS VARCHAR) IS NULL OR name LIKE '%' || CAST(:name AS VARCHAR) || '%')
   AND (CAST(:pref AS VARCHAR) IS NULL OR pref = CAST(:pref AS VARCHAR))
 ORDER BY id
