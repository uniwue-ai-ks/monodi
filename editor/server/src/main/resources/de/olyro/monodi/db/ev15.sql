-- This file needs to have two dashes between every two statements.
-- This file will be read by the program and split into individual statements between the two dashes.
-- You can have multiple dashed lines next to each other since whitespace-only splits will not be turned into statements.
ALTER TABLE dokument_editor
DROP CONSTRAINT dokument_editor_dokument_fkey,
ADD CONSTRAINT dokument_editor_dokument_fkey FOREIGN KEY ("dokument") REFERENCES "dokument"(id) ON DELETE CASCADE;
