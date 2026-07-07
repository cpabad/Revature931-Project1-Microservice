-- ============================================================================
-- ers_auth: the identity service's OWN database.
-- auth-service is the only writer of users/roles anywhere in the system;
-- reimbursement-service holds a replicated, password-less reference copy in
-- ITS database (db/reimbursement/init.sql) - services never join across DBs.
-- Same schema name as the legacy monolith so the entity mappings stay put.
-- ============================================================================
CREATE SCHEMA "ExpenseReimbursementManagementSystem";
SET search_path TO "ExpenseReimbursementManagementSystem";

CREATE TABLE roles (
    roleid integer PRIMARY KEY,
    roles  character varying NOT NULL UNIQUE
);

CREATE TABLE users (
    userid        integer PRIMARY KEY,
    loginusername character varying NOT NULL UNIQUE,
    loginpassword character varying NOT NULL,
    firstname     character varying NOT NULL,
    lastname      character varying NOT NULL,
    email         character varying NOT NULL UNIQUE,
    roles         integer REFERENCES roles (roleid)
);

INSERT INTO roles VALUES (1, 'Supervisor');
INSERT INTO roles VALUES (2, 'Employee');

-- Seed rows carried verbatim from the legacy shared dump (bcrypt hashes included),
-- in the dump's original insertion order.
INSERT INTO users VALUES (1, 'admin', '$2a$10$2iPzYhJRQBBn73K38IeyAuFDOJC5QPk/qjnhTPE1tPtJ9aEtUARSq', 'A', 'A', 'a@email.com', 1);
INSERT INTO users VALUES (2, 'employee1', '$2a$10$AcxFYGGpzKK3pYbEX66hUeJcq4sK31W7t8JCkzE9G8Puie3w.9KHy', 'B', 'B', 'b@email.com', 1);
INSERT INTO users VALUES (4, 'employee3', '$2a$10$7gSd84zDIQQ5Z4ht3bCzIOCdNalXRtMhCO80a8m9JtfwP20fv8HAC', 'D', 'D', 'd@email.com', 2);
INSERT INTO users VALUES (5, 'employee4', '$2a$10$ZeextRWPH2z8A1Pv2WKrHe4SWr0SSFgeElLePgMRq7Pgp65u5ReW2', 'E', 'E', 'e@email.com', 2);
INSERT INTO users VALUES (3, 'employee02', '$2a$10$l.O.YdrDGKRrZUK9ODDK2OjyCmQLQV9yvTWdTV8FNoo3r5wmbL.ca', 'C', 'C', 'cabby@email.com', 2);
