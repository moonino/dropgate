CREATE TABLE auth.users (
    id            uuid         PRIMARY KEY,
    kakao_id      bigint       NOT NULL UNIQUE,
    nickname      varchar(50)  NOT NULL,
    role          varchar(20)  NOT NULL DEFAULT 'USER' CHECK (role IN ('USER', 'ADMIN')),
    created_at    timestamptz  NOT NULL DEFAULT now(),
    last_login_at timestamptz  NOT NULL DEFAULT now()
);
