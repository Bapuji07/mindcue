-- Who said what: who must act on a memory (a speaker label such as "Speaker 2", or a name),
-- whether that is the person using the app, and which speaker in a conversation is that person.
ALTER TABLE memory ADD COLUMN owner VARCHAR(120);
ALTER TABLE memory ADD COLUMN owner_is_self BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE memory_session ADD COLUMN self_speaker VARCHAR(100);
