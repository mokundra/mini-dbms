CREATE TABLE departments (id INT PRIMARY KEY, name STRING UNIQUE);
CREATE TABLE employees (id INT PRIMARY KEY, name STRING, salary DOUBLE, hired DATE, active BOOLEAN, department_id INT, FOREIGN KEY (department_id) REFERENCES departments(id));

INSERT INTO departments VALUES (1, 'Engineering'), (2, 'Finance');
INSERT INTO employees VALUES (1, 'Nikita', 95000.0, '2026-09-01', TRUE, 1);
INSERT INTO employees (id, name, salary, hired, active, department_id) VALUES (2, 'Anna', 86000.0, '2026-09-15', TRUE, 2);
INSERT INTO employees VALUES (3, 'Ivan', 75000.0, '2026-10-01', FALSE, 1);

SELECT * FROM departments;
SELECT * FROM employees;
SELECT name, salary FROM employees WHERE salary > 80000;

CREATE INDEX idx_employees_salary ON employees (salary);
EXPLAIN SELECT * FROM employees WHERE salary = 95000;
SELECT * FROM employees WHERE salary = 95000;

UPDATE employees SET salary = 99000 WHERE id = 1;
SELECT * FROM employees WHERE id = 1;
DELETE FROM employees WHERE id = 3;
SELECT * FROM employees;
