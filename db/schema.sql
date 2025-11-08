-- Enable pgcrypto for gen_random_uuid
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS users (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  username TEXT UNIQUE NOT NULL,
  password_hash TEXT NOT NULL,
  group_id UUID,
  share NUMERIC(5,4) DEFAULT 1.0
);

CREATE TABLE IF NOT EXISTS sessions (
  sid TEXT PRIMARY KEY,
  username TEXT NOT NULL REFERENCES users(username),
  created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE IF NOT EXISTS transactions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id),
  date_epoch_day BIGINT NOT NULL,
  category TEXT,
  description TEXT,
  amount NUMERIC(14,2) NOT NULL
);

CREATE TABLE IF NOT EXISTS budgets (
  user_id UUID NOT NULL REFERENCES users(id),
  category TEXT NOT NULL,
  limit_amount NUMERIC(14,2) NOT NULL,
  spent NUMERIC(14,2) NOT NULL DEFAULT 0,
  PRIMARY KEY (user_id, category)
);

CREATE TABLE IF NOT EXISTS goals (
  user_id UUID PRIMARY KEY REFERENCES users(id),
  name TEXT NOT NULL,
  target_amount NUMERIC(14,2) NOT NULL,
  current_amount NUMERIC(14,2) NOT NULL
);

CREATE TABLE IF NOT EXISTS reminders (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id),
  due_epoch_day BIGINT NOT NULL,
  message TEXT NOT NULL,
  amount NUMERIC(14,2),
  sent BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS groups (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS group_members (
  group_id UUID NOT NULL REFERENCES groups(id),
  user_id UUID NOT NULL REFERENCES users(id),
  share NUMERIC(5,4) NOT NULL,
  PRIMARY KEY (group_id, user_id)
);



