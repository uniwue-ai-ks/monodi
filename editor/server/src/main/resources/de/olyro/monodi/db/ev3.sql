-- This file needs to have two dashes between every two statements.
-- This file will be read by the program and split into individual statements between the two dashes.
-- You can have multiple dashed lines next to each other since whitespace-only splits will not be turned into statements.
DROP TABLE role;
--
DROP TYPE account_role;
--
CREATE TYPE account_role AS ENUM ('admin');
--
CREATE TABLE role (
	account_id INTEGER NOT NULL REFERENCES account(id),
	role account_role NOT NULL,
	PRIMARY KEY (account_id, role)
);
--
INSERT INTO role VALUES (1, 'admin');

