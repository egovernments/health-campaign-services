ALTER TABLE census ADD facility_assigned boolean;
UPDATE census SET facility_assigned = false WHERE facility_assigned IS NULL;