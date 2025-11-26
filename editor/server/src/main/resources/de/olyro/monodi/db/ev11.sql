-- This file needs to have two dashes between every two statements.
-- This file will be read by the program and split into individual statements between the two dashes.
-- You can have multiple dashed lines next to each other since whitespace-only splits will not be turned into statements.
--
ALTER TABLE quelle ALTER COLUMN id drop DEFAULT;
--
ALTER TABLE dokument ALTER COLUMN id drop DEFAULT;
--
DROP FUNCTION next_max_quelle_id();
--
DROP FUNCTION next_max_dokument_id();
--
ALTER TABLE dokument DROP CONSTRAINT dokument_quelle_id_fkey;
--
DROP TABLE  quelle_editor;
--
DROP TABLE  dokument_editor;
--
ALTER TABLE quelle ALTER COLUMN id TYPE TEXT USING id::TEXT;
--
ALTER TABLE dokument ALTER COLUMN id TYPE TEXT USING id::TEXT;
--
ALTER TABLE dokument ALTER COLUMN quelle_id TYPE TEXT USING quelle_id::TEXT;
--
ALTER TABLE dokument ADD CONSTRAINT dokument_quelle_id_fkey FOREIGN KEY (quelle_id) REFERENCES quelle(id) ON DELETE CASCADE ON UPDATE CASCADE;
--
CREATE TABLE dokument_editor (
	account  TEXT NOT NULL REFERENCES account(email),
	dokument TEXT NOT NULL REFERENCES dokument(id),
	PRIMARY KEY (account, dokument)
);
