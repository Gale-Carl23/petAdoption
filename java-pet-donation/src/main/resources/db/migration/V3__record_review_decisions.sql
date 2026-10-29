ALTER TABLE adoption_applications
    ADD COLUMN reviewed_by VARCHAR(150),
    ADD COLUMN reviewed_at TIMESTAMP;

ALTER TABLE donations
    ADD COLUMN reviewed_by VARCHAR(150),
    ADD COLUMN reviewed_at TIMESTAMP;
