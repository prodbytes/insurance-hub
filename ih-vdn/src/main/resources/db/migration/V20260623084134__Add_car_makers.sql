-- Creates the car_maker table and seeds the supported makers.
-- Mapped in Java by ih.domain.CarMaker (Hibernate ORM).
CREATE TABLE car_maker (
    id   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(64) NOT NULL UNIQUE
);

INSERT INTO car_maker (name) VALUES ('toyota'), ('honda'), ('ford');
