-- This file needs to have two dashes between every two statements.
-- This file will be read by the program and split into individual statements between the two dashes.
-- You can have multiple dashed lines next to each other since whitespace-only splits will not be turned into statements.
--
ALTER TABLE dokument ADD COLUMN notes JSON;
--
UPDATE dokument SET notes = '{"kind":"RootContainer","children":[{"kind":"FormteilContainer","children":[{"kind":"ZeileContainer","children":[{"text":"","endsWord":true,"notes":{"spaced":[{"nonSpaced":[{"grouped":[{"base":"A","linquescent":false,"noteType":"-","octave":4,"focus":false}]}]}]}}]}]}]}';
