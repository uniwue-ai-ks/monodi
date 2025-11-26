-- This file needs to have two dashes between every two statements.
-- This file will be read by the program and split into individual statements between the two dashes.
-- You can have multiple dashed lines next to each other since whitespace-only splits will not be turned into statements.
CREATE OR REPLACE FUNCTION next_max_quelle_id() RETURNS int LANGUAGE SQL AS $$ SELECT Max(id) + 1 FROM quelle; $$;
--
CREATE OR REPLACE FUNCTION next_max_dokument_id() RETURNS int LANGUAGE SQL AS $$ SELECT Max(id) + 1 FROM dokument; $$;
--
ALTER TABLE quelle ALTER COLUMN id SET DEFAULT next_max_quelle_id();
--
ALTER TABLE dokument ALTER COLUMN id SET DEFAULT next_max_dokument_id();
