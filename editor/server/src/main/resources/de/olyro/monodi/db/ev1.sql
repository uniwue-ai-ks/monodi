-- This file needs to have two dashes between every two statements.
-- This file will be read by the program and split into individual statements between the two dashes.
-- You can have multiple dashed lines next to each other since whitespace-only splits will not be turned into statements.
CREATE EXTENSION IF NOT EXISTS pgcrypto;
--
CREATE TABLE users (
  id SERIAL PRIMARY KEY,
  email TEXT NOT NULL UNIQUE,
  password TEXT NOT NULL
);
--
INSERT INTO users (email, password) VALUES (
	'herrmann@olyro.de',
	crypt('testpassword', gen_salt('bf'))
);
