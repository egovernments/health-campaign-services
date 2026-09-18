
ALTER TABLE household_member
    ALTER COLUMN id SET NOT NULL;
ALTER TABLE household_member
    ADD CONSTRAINT pk_household_member PRIMARY KEY (id);
