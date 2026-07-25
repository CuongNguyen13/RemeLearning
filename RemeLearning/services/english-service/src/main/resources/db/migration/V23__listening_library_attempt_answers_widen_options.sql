-- selected_option/correct_option were originally sized for a single letter key ("A"-"D"), but the
-- FE submits (and the service now persists) the full option text it displayed - VARCHAR(8) overflows
-- on any option longer than 8 characters (e.g. "A cup of tea.").
ALTER TABLE listening_library_attempt_answers ALTER COLUMN selected_option TYPE VARCHAR(500);
ALTER TABLE listening_library_attempt_answers ALTER COLUMN correct_option TYPE VARCHAR(500);
