-- This file needs to have two dashes between every two statements.
-- This file will be read by the program and split into individual statements between the two dashes.
-- You can have multiple dashed lines next to each other since whitespace-only splits will not be turned into statements.
CREATE TABLE quelle (
	id SERIAL PRIMARY KEY,
	quellensigle TEXT NOT NULL,
	herkunftsregion TEXT NOT NULL,
	herkunftsort TEXT NOT NULL,
	herkunftsinstitution TEXT NOT NULL,
	ordenstradition TEXT NOT NULL,
	quellentyp TEXT NOT NULL,
	bibliotheksort TEXT NOT NULL,
	bibliothek TEXT NOT NULL,
	bibliothekssignatur TEXT NOT NULL,
	kommentar TEXT NOT NULL
);
--
CREATE TABLE dokument (
	id SERIAL PRIMARY KEY,
	quelle_id INTEGER NOT NULL REFERENCES quelle(id),
	dokumenten_id TEXT NOT NULL,
	gattung1 TEXT NOT NULL,
	gattung2 TEXT NOT NULL,
	festtag TEXT NOT NULL,
	feier TEXT NOT NULL,
	textinitium TEXT NOT NULL,
	bibliographischerverweis TEXT NOT NULL,
	druckausgabe TEXT NOT NULL,
	zeilenstart TEXT NOT NULL,
	foliostart TEXT NOT NULL,
	kommentar TEXT NOT NULL
);
