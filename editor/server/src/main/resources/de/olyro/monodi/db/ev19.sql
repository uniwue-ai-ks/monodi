ALTER TABLE dokument ADD COLUMN publish TEXT NOT NULL DEFAULT 'none';
ALTER TABLE quelle ADD COLUMN publish TEXT NOT NULL DEFAULT 'none';

UPDATE dokument SET publish = 'all' WHERE editionsstatus = 'ediert';
UPDATE dokument SET publish = 'meta-only' WHERE editionsstatus = 'inventarisiert';
UPDATE quelle SET publish = 'all' WHERE status = 'true';
