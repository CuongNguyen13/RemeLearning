-- Grammar Library: adds a plain Vietnamese sentence translation of the correct answer, distinct
-- from explanation_vi (a grammar-rule explanation, not a meaning translation). Nullable since
-- existing rows (generated before this column existed) have no translation.
ALTER TABLE grammar_library_questions ADD COLUMN translation_vi TEXT;
