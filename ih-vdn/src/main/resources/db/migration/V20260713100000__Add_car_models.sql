-- Creates the car_model table and seeds the supported models per maker.
-- Mapped in Java by ih.domain.CarModel (Hibernate ORM). The seeded pairs
-- mirror the pricing rules in ih-models/quote/carEstimatedValue.dmn.
CREATE TABLE car_model (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    car_maker_id BIGINT NOT NULL REFERENCES car_maker (id),
    name         VARCHAR(64) NOT NULL,
    UNIQUE (car_maker_id, name)
);

INSERT INTO car_model (car_maker_id, name)
SELECT maker.id, seed.model
FROM (VALUES
    ('toyota', 'camry'),
    ('toyota', 'corolla'),
    ('toyota', 'rav4'),
    ('honda', 'civic'),
    ('honda', 'accord'),
    ('honda', 'cr-v'),
    ('ford', 'f-150'),
    ('ford', 'escape'),
    ('ford', 'explorer')
) AS seed (maker, model)
JOIN car_maker maker ON maker.name = seed.maker;
