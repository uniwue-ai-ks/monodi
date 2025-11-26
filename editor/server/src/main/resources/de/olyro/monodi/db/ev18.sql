-- This file needs to have two dashes between every two statements.
-- This file will be read by the program and split into individual statements between the two dashes.
-- You can have multiple dashed lines next to each other since whitespace-only splits will not be turned into statements.
--

CREATE TABLE similarity_score (
    document_1 TEXT REFERENCES dokument(id) on DELETE CASCADE,
    document_2 TEXT REFERENCES dokument(id) on DELETE CASCADE,
    similarity NUMERIC NOT NULL,
    PRIMARY KEY (document_1, document_2)
);