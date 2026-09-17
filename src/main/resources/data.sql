INSERT INTO department(id, name) VALUES
    (10, 'North Sales'),
    (20, 'South Sales');

INSERT INTO app_user(id, username, display_name, data_scope, department_id, active) VALUES
    (1, 'admin', 'Qiqi Admin', 'ALL', NULL, TRUE),
    (2, 'alice', 'Alice North', 'DEPARTMENT', 10, TRUE),
    (3, 'bob', 'Bob South', 'DEPARTMENT', 20, TRUE);

INSERT INTO customer(id, name, segment, city) VALUES
    (101, 'Acme Studio', 'ENTERPRISE', 'Beijing'),
    (102, 'Bright Retail', 'SMB', 'Shanghai'),
    (103, 'Cloud Market', 'SMB', 'Shenzhen'),
    (104, 'Delta Labs', 'ENTERPRISE', 'Hangzhou'),
    (105, 'Evergreen Shop', 'SMB', 'Chengdu');

INSERT INTO product(id, sku, name, category, unit_price) VALUES
    (1001, 'ANA-START', 'Analytics Starter', 'Software', 1200.00),
    (1002, 'ANA-PRO', 'Analytics Pro', 'Software', 3200.00),
    (1003, 'DATA-SETUP', 'Data Setup Service', 'Service', 1800.00),
    (1004, 'TRAIN-TEAM', 'Team Training', 'Service', 900.00),
    (1005, 'SENSOR-A', 'Edge Sensor A', 'Hardware', 450.00),
    (1006, 'GATEWAY-X', 'Edge Gateway X', 'Hardware', 1500.00);

INSERT INTO sales_order(id, order_no, order_date, customer_id, department_id, status, total_amount) VALUES
    (2001, 'SO-2026-001', DATE '2026-01-05', 101, 10, 'PAID',  5000.00),
    (2002, 'SO-2026-002', DATE '2026-01-12', 102, 10, 'PAID',  2100.00),
    (2003, 'SO-2026-003', DATE '2026-01-18', 103, 20, 'PAID',  3600.00),
    (2004, 'SO-2026-004', DATE '2026-01-26', 104, 20, 'PAID',  4700.00),
    (2005, 'SO-2026-005', DATE '2026-02-03', 105, 10, 'PAID',  2700.00),
    (2006, 'SO-2026-006', DATE '2026-02-09', 101, 10, 'PAID',  6400.00),
    (2007, 'SO-2026-007', DATE '2026-02-16', 102, 20, 'PAID',  3900.00),
    (2008, 'SO-2026-008', DATE '2026-02-24', 103, 20, 'PAID',  5400.00),
    (2009, 'SO-2026-009', DATE '2026-03-02', 104, 10, 'PAID',  6800.00),
    (2010, 'SO-2026-010', DATE '2026-03-08', 105, 10, 'PAID',  4200.00),
    (2011, 'SO-2026-011', DATE '2026-03-14', 101, 20, 'PAID',  7200.00),
    (2012, 'SO-2026-012', DATE '2026-03-21', 102, 20, 'PENDING', 3100.00);

INSERT INTO sales_order_item(id, order_id, product_id, quantity, unit_price, line_amount) VALUES
    (3001, 2001, 1002, 1, 3200.00, 3200.00), (3002, 2001, 1003, 1, 1800.00, 1800.00),
    (3003, 2002, 1001, 1, 1200.00, 1200.00), (3004, 2002, 1004, 1,  900.00,  900.00),
    (3005, 2003, 1005, 4,  450.00, 1800.00), (3006, 2003, 1003, 1, 1800.00, 1800.00),
    (3007, 2004, 1002, 1, 3200.00, 3200.00), (3008, 2004, 1006, 1, 1500.00, 1500.00),
    (3009, 2005, 1001, 1, 1200.00, 1200.00), (3010, 2005, 1006, 1, 1500.00, 1500.00),
    (3011, 2006, 1002, 2, 3200.00, 6400.00),
    (3012, 2007, 1002, 1, 3200.00, 3200.00), (3013, 2007, 1004, 1,  700.00,  700.00),
    (3014, 2008, 1003, 3, 1800.00, 5400.00),
    (3015, 2009, 1002, 1, 3200.00, 3200.00), (3016, 2009, 1003, 2, 1800.00, 3600.00),
    (3017, 2010, 1001, 2, 1200.00, 2400.00), (3018, 2010, 1004, 2,  900.00, 1800.00),
    (3019, 2011, 1002, 1, 3200.00, 3200.00), (3020, 2011, 1005, 4,  450.00, 1800.00),
    (3021, 2011, 1006, 1, 1500.00, 1500.00), (3022, 2011, 1004, 1,  700.00,  700.00),
    (3023, 2012, 1001, 1, 1200.00, 1200.00), (3024, 2012, 1003, 1, 1900.00, 1900.00);
