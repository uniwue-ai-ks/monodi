-- This file needs to have two dashes between every two statements.
-- This file will be read by the program and split into individual statements between the two dashes.
-- You can have multiple dashed lines next to each other since whitespace-only splits will not be turned into statements.
DROP TABLE role;
--
DROP TABLE account;
--
CREATE TABLE account (
  email TEXT NOT NULL PRIMARY KEY,
  password TEXT NOT NULL
);
--
CREATE TABLE role (
	account_email TEXT NOT NULL REFERENCES account(email),
	role account_role NOT NULL,
	PRIMARY KEY (account_email, role)
);
--
INSERT INTO account VALUES (
	'herrmann@olyro.de',
	crypt('testpassword', gen_salt('bf'))
);
--
INSERT INTO role VALUES ('herrmann@olyro.de', 'admin');
