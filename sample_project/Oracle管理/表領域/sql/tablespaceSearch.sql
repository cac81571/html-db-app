-- 顧客検索
-- :name … 画面の name="in_name"、:pref … name="in_pref"
-- 空欄は NULL としてバインドされるので、「NULL なら条件なし」と書くと任意条件にできます。
SELECT
    t.tablespace_name
    , t.status
    , ROUND(SUM(d.bytes) / 1024 / 1024) AS size_mb
    , ROUND(NVL(MAX(f.free), 0) / 1024 / 1024) AS free_mb 
FROM
    dba_tablespaces t 
    LEFT JOIN dba_data_files d 
        ON d.tablespace_name = t.tablespace_name 
    LEFT JOIN ( 
        SELECT
            tablespace_name
            , SUM(bytes) free 
        FROM
            dba_free_space 
        GROUP BY
            tablespace_name
    ) f 
        ON f.tablespace_name = t.tablespace_name 
WHERE LOWER(t.tablespace_name) like '%' || LOWER(:tablespaceName) || '%'
GROUP BY
    t.tablespace_name
    , t.status 
ORDER BY
    t.tablespace_name;
