-- This file needs to have two dashes between every two statements.
-- This file will be read by the program and split into individual statements between the two dashes.
-- You can have multiple dashed lines next to each other since whitespace-only splits will not be turned into statements.
CREATE TABLE dokument_editor (
	account TEXT NOT NULL REFERENCES account(email),
	dokument INTEGER NOT NULL REFERENCES dokument(id),
	canWrite BOOLEAN NOT NULL,
	PRIMARY KEY (account, dokument)
);
--
CREATE TABLE quelle_editor (
	account TEXT NOT NULL REFERENCES account(email),
	quelle INTEGER NOT NULL REFERENCES quelle(id),
	canWrite BOOLEAN NOT NULL,
	PRIMARY KEY (account, quelle)

)
