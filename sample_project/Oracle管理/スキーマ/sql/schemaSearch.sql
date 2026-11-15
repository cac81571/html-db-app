-- 顧客検索
-- :name … 画面の name="in_name"、:pref … name="in_pref"
-- 空欄は NULL としてバインドされるので、「NULL なら条件なし」と書くと任意条件にできます。
SELECT
    username  AS "スキーマ名"
    , created AS "作成日時"
FROM
    dba_users 
WHERE
    oracle_maintained = 'N' 
    AND LOWER(username) like '%' || LOWER(:schemaName) || '%' 
ORDER BY
    username;
