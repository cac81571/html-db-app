-- 編集ボタン。一覧の番号だけを :id に受け、登録フォーム用の列を返す
SELECT id    AS "id",
       title AS "title"
  FROM ticket
 WHERE id = :id
