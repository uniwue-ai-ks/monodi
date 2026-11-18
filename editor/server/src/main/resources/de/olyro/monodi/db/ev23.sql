ALTER TABLE dokument RENAME COLUMN editionsstatus TO subcorpus;
UPDATE dokument SET subcorpus = CASE subcorpus
  WHEN 'ediert'        THEN 'edition'
  WHEN 'transkribiert' THEN 'transkription'
  WHEN 'zitiert'       THEN 'graduale-synopticum'
  ELSE subcorpus
END;
