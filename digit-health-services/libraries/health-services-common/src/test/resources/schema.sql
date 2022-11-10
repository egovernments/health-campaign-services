CREATE TABLE IF NOT EXISTS employee (
    id    INTEGER,
    name VARCHAR(50),
    phonenumber VARCHAR(50),
    price INTEGER,
    currency VARCHAR(50)
    );

INSERT INTO employee VALUES(1, 'JON', '100', 1500, null);
INSERT INTO employee VALUES(2, 'DOE', '100', 1000, 'INR');
INSERT INTO employee VALUES(3, 'KO', '100', 1000, 'DOLLAR');