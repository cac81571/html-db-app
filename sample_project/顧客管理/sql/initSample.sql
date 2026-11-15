-- サンプルテーブルの作成とデータ投入（H2 用）
-- 1 ファイルに複数の文を ; 区切りで書けます。何度実行しても大丈夫なように書いています。
CREATE TABLE IF NOT EXISTS customer (
    id         INT PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    pref       VARCHAR(20),
    email      VARCHAR(200),
    joined_on  DATE
);

MERGE INTO customer (id, name, pref, email, joined_on) KEY (id) VALUES
    (1, '山田 太郎', '東京都', 'taro@example.com',   DATE '2024-04-01'),
    (2, '佐藤 花子', '大阪府', 'hanako@example.com', DATE '2024-05-15'),
    (3, '鈴木 一郎', '愛知県', 'ichiro@example.com', DATE '2025-01-10'),
    (4, '高橋 美咲', '東京都', NULL,                  DATE '2025-03-03');
