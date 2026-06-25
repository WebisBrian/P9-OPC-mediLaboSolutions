INSERT INTO patients (id, first_name, last_name, birth_date, gender, address, phone)
VALUES (1, 'Test', 'TestNone', '1966-12-31', 'F', '1 Brookside St', '100-222-3333')
    ON CONFLICT (id) DO NOTHING;

INSERT INTO patients (id, first_name, last_name, birth_date, gender, address, phone)
VALUES (2, 'Test', 'TestBorderline', '1945-06-24', 'M', '2 High St', '200-333-4444')
    ON CONFLICT (id) DO NOTHING;

INSERT INTO patients (id, first_name, last_name, birth_date, gender, address, phone)
VALUES (3, 'Test', 'TestInDanger', '2004-06-18', 'M', '3 Club Road', '300-444-5555')
    ON CONFLICT (id) DO NOTHING;

INSERT INTO patients (id, first_name, last_name, birth_date, gender, address, phone)
VALUES (4, 'Test', 'TestEarlyOnset', '2002-06-28', 'F', '4 Valley Dr', '400-555-6666')
    ON CONFLICT (id) DO NOTHING;