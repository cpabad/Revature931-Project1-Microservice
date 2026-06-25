--
-- PostgreSQL database dump
--

\restrict vZLhJzk0wkAxCnjn5cZ5iMyVeesyhUg6Sp6wukbQC0bfQsQcNTSiejkpPLnGI0K

-- Dumped from database version 16.14 (Ubuntu 16.14-0ubuntu0.24.04.1)
-- Dumped by pg_dump version 16.14 (Ubuntu 16.14-0ubuntu0.24.04.1)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: ExpenseReimbursementManagementSystem; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA "ExpenseReimbursementManagementSystem";


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: approval_confirmation; Type: TABLE; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

CREATE TABLE "ExpenseReimbursementManagementSystem".approval_confirmation (
    confirmationid integer NOT NULL,
    confirmationdate date NOT NULL,
    approvalid integer,
    CONSTRAINT approval_confirmation_confirmationdate_check CHECK ((confirmationdate <= CURRENT_DATE))
);


--
-- Name: approval_confirmation_confirmationid_seq; Type: SEQUENCE; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

CREATE SEQUENCE "ExpenseReimbursementManagementSystem".approval_confirmation_confirmationid_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: approval_confirmation_confirmationid_seq; Type: SEQUENCE OWNED BY; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

ALTER SEQUENCE "ExpenseReimbursementManagementSystem".approval_confirmation_confirmationid_seq OWNED BY "ExpenseReimbursementManagementSystem".approval_confirmation.confirmationid;


--
-- Name: city_state_postal; Type: TABLE; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

CREATE TABLE "ExpenseReimbursementManagementSystem".city_state_postal (
    postalcode integer NOT NULL,
    city character varying NOT NULL,
    state character varying NOT NULL
);


--
-- Name: employee_supervisor_jt; Type: TABLE; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

CREATE TABLE "ExpenseReimbursementManagementSystem".employee_supervisor_jt (
    hierarchyid integer NOT NULL,
    useridsupervisor integer NOT NULL,
    useridemployee integer NOT NULL
);


--
-- Name: employee_supervisor_jt_hierarchyid_seq; Type: SEQUENCE; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

CREATE SEQUENCE "ExpenseReimbursementManagementSystem".employee_supervisor_jt_hierarchyid_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: employee_supervisor_jt_hierarchyid_seq; Type: SEQUENCE OWNED BY; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

ALTER SEQUENCE "ExpenseReimbursementManagementSystem".employee_supervisor_jt_hierarchyid_seq OWNED BY "ExpenseReimbursementManagementSystem".employee_supervisor_jt.hierarchyid;


--
-- Name: event_location; Type: TABLE; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

CREATE TABLE "ExpenseReimbursementManagementSystem".event_location (
    locationid integer NOT NULL,
    street_number integer NOT NULL,
    street_name character varying NOT NULL,
    postalcode integer NOT NULL
);


--
-- Name: event_location_locationid_seq; Type: SEQUENCE; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

CREATE SEQUENCE "ExpenseReimbursementManagementSystem".event_location_locationid_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: event_location_locationid_seq; Type: SEQUENCE OWNED BY; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

ALTER SEQUENCE "ExpenseReimbursementManagementSystem".event_location_locationid_seq OWNED BY "ExpenseReimbursementManagementSystem".event_location.locationid;


--
-- Name: reimbursement; Type: TABLE; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

CREATE TABLE "ExpenseReimbursementManagementSystem".reimbursement (
    reimbursementid integer NOT NULL,
    amount double precision,
    dateawarded date,
    finalapprovalid integer,
    statusid integer,
    CONSTRAINT reimbursement_amount_check CHECK ((amount >= (0)::double precision)),
    CONSTRAINT reimbursement_dateawarded_check CHECK ((dateawarded <= CURRENT_DATE))
);


--
-- Name: reimbursement_confirmation; Type: TABLE; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

CREATE TABLE "ExpenseReimbursementManagementSystem".reimbursement_confirmation (
    confirmationid integer NOT NULL,
    confirmationdate date NOT NULL,
    reimbursementid integer,
    CONSTRAINT reimbursement_confirmation_confirmationdate_check CHECK ((confirmationdate <= CURRENT_DATE))
);


--
-- Name: reimbursement_confirmation_confirmationid_seq; Type: SEQUENCE; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

CREATE SEQUENCE "ExpenseReimbursementManagementSystem".reimbursement_confirmation_confirmationid_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: reimbursement_confirmation_confirmationid_seq; Type: SEQUENCE OWNED BY; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

ALTER SEQUENCE "ExpenseReimbursementManagementSystem".reimbursement_confirmation_confirmationid_seq OWNED BY "ExpenseReimbursementManagementSystem".reimbursement_confirmation.confirmationid;


--
-- Name: reimbursement_reimbursementid_seq; Type: SEQUENCE; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

CREATE SEQUENCE "ExpenseReimbursementManagementSystem".reimbursement_reimbursementid_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: reimbursement_reimbursementid_seq; Type: SEQUENCE OWNED BY; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

ALTER SEQUENCE "ExpenseReimbursementManagementSystem".reimbursement_reimbursementid_seq OWNED BY "ExpenseReimbursementManagementSystem".reimbursement.reimbursementid;


--
-- Name: reimbursement_status; Type: TABLE; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

CREATE TABLE "ExpenseReimbursementManagementSystem".reimbursement_status (
    statusid integer NOT NULL,
    status character varying NOT NULL
);


--
-- Name: reimbursement_status_statusid_seq; Type: SEQUENCE; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

CREATE SEQUENCE "ExpenseReimbursementManagementSystem".reimbursement_status_statusid_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: reimbursement_status_statusid_seq; Type: SEQUENCE OWNED BY; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

ALTER SEQUENCE "ExpenseReimbursementManagementSystem".reimbursement_status_statusid_seq OWNED BY "ExpenseReimbursementManagementSystem".reimbursement_status.statusid;


--
-- Name: request; Type: TABLE; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

CREATE TABLE "ExpenseReimbursementManagementSystem".request (
    requestid integer NOT NULL,
    amount double precision,
    eventdate date NOT NULL,
    eventlocation integer,
    requestedevent character varying NOT NULL,
    requesteruserid integer,
    statusid integer,
    CONSTRAINT request_amount_check CHECK ((amount >= (0)::double precision)),
    CONSTRAINT request_eventdate_check CHECK ((eventdate <= CURRENT_DATE))
);


--
-- Name: request_confirmation; Type: TABLE; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

CREATE TABLE "ExpenseReimbursementManagementSystem".request_confirmation (
    confirmationid integer NOT NULL,
    confirmationdate date NOT NULL,
    requestid integer,
    CONSTRAINT request_confirmation_confirmationdate_check CHECK ((confirmationdate <= CURRENT_DATE))
);


--
-- Name: request_confirmation_confirmationid_seq; Type: SEQUENCE; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

CREATE SEQUENCE "ExpenseReimbursementManagementSystem".request_confirmation_confirmationid_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: request_confirmation_confirmationid_seq; Type: SEQUENCE OWNED BY; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

ALTER SEQUENCE "ExpenseReimbursementManagementSystem".request_confirmation_confirmationid_seq OWNED BY "ExpenseReimbursementManagementSystem".request_confirmation.confirmationid;


--
-- Name: request_image; Type: TABLE; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

CREATE TABLE "ExpenseReimbursementManagementSystem".request_image (
    imageid integer NOT NULL,
    filename character varying NOT NULL,
    requestid integer
);


--
-- Name: request_image_imageid_seq; Type: SEQUENCE; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

CREATE SEQUENCE "ExpenseReimbursementManagementSystem".request_image_imageid_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: request_image_imageid_seq; Type: SEQUENCE OWNED BY; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

ALTER SEQUENCE "ExpenseReimbursementManagementSystem".request_image_imageid_seq OWNED BY "ExpenseReimbursementManagementSystem".request_image.imageid;


--
-- Name: request_requestid_seq; Type: SEQUENCE; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

CREATE SEQUENCE "ExpenseReimbursementManagementSystem".request_requestid_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: request_requestid_seq; Type: SEQUENCE OWNED BY; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

ALTER SEQUENCE "ExpenseReimbursementManagementSystem".request_requestid_seq OWNED BY "ExpenseReimbursementManagementSystem".request.requestid;


--
-- Name: request_status; Type: TABLE; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

CREATE TABLE "ExpenseReimbursementManagementSystem".request_status (
    statusid integer NOT NULL,
    status character varying NOT NULL
);


--
-- Name: request_status_statusid_seq; Type: SEQUENCE; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

CREATE SEQUENCE "ExpenseReimbursementManagementSystem".request_status_statusid_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: request_status_statusid_seq; Type: SEQUENCE OWNED BY; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

ALTER SEQUENCE "ExpenseReimbursementManagementSystem".request_status_statusid_seq OWNED BY "ExpenseReimbursementManagementSystem".request_status.statusid;


--
-- Name: roles; Type: TABLE; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

CREATE TABLE "ExpenseReimbursementManagementSystem".roles (
    roleid integer NOT NULL,
    roles character varying NOT NULL
);


--
-- Name: roles_roleid_seq; Type: SEQUENCE; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

CREATE SEQUENCE "ExpenseReimbursementManagementSystem".roles_roleid_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: roles_roleid_seq; Type: SEQUENCE OWNED BY; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

ALTER SEQUENCE "ExpenseReimbursementManagementSystem".roles_roleid_seq OWNED BY "ExpenseReimbursementManagementSystem".roles.roleid;


--
-- Name: supervisor_approval; Type: TABLE; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

CREATE TABLE "ExpenseReimbursementManagementSystem".supervisor_approval (
    approvalid integer NOT NULL,
    dateofpreviousupdate date NOT NULL,
    requestid integer,
    hierarchyid integer,
    statusid integer,
    approval boolean,
    CONSTRAINT supervisor_approval_dateofpreviousupdate_check CHECK ((dateofpreviousupdate <= CURRENT_DATE))
);


--
-- Name: supervisor_approval_approvalid_seq; Type: SEQUENCE; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

CREATE SEQUENCE "ExpenseReimbursementManagementSystem".supervisor_approval_approvalid_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: supervisor_approval_approvalid_seq; Type: SEQUENCE OWNED BY; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

ALTER SEQUENCE "ExpenseReimbursementManagementSystem".supervisor_approval_approvalid_seq OWNED BY "ExpenseReimbursementManagementSystem".supervisor_approval.approvalid;


--
-- Name: supervisor_approval_status; Type: TABLE; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

CREATE TABLE "ExpenseReimbursementManagementSystem".supervisor_approval_status (
    statusid integer NOT NULL,
    status character varying NOT NULL
);


--
-- Name: supervisor_approval_status_statusid_seq; Type: SEQUENCE; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

CREATE SEQUENCE "ExpenseReimbursementManagementSystem".supervisor_approval_status_statusid_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: supervisor_approval_status_statusid_seq; Type: SEQUENCE OWNED BY; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

ALTER SEQUENCE "ExpenseReimbursementManagementSystem".supervisor_approval_status_statusid_seq OWNED BY "ExpenseReimbursementManagementSystem".supervisor_approval_status.statusid;


--
-- Name: users; Type: TABLE; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

CREATE TABLE "ExpenseReimbursementManagementSystem".users (
    userid integer NOT NULL,
    loginusername character varying NOT NULL,
    loginpassword character varying NOT NULL,
    firstname character varying NOT NULL,
    lastname character varying NOT NULL,
    email character varying NOT NULL,
    roles integer
);


--
-- Name: users_userid_seq; Type: SEQUENCE; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

CREATE SEQUENCE "ExpenseReimbursementManagementSystem".users_userid_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: users_userid_seq; Type: SEQUENCE OWNED BY; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

ALTER SEQUENCE "ExpenseReimbursementManagementSystem".users_userid_seq OWNED BY "ExpenseReimbursementManagementSystem".users.userid;


--
-- Name: approval_confirmation confirmationid; Type: DEFAULT; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

ALTER TABLE ONLY "ExpenseReimbursementManagementSystem".approval_confirmation ALTER COLUMN confirmationid SET DEFAULT nextval('"ExpenseReimbursementManagementSystem".approval_confirmation_confirmationid_seq'::regclass);


--
-- Name: employee_supervisor_jt hierarchyid; Type: DEFAULT; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

ALTER TABLE ONLY "ExpenseReimbursementManagementSystem".employee_supervisor_jt ALTER COLUMN hierarchyid SET DEFAULT nextval('"ExpenseReimbursementManagementSystem".employee_supervisor_jt_hierarchyid_seq'::regclass);


--
-- Name: event_location locationid; Type: DEFAULT; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

ALTER TABLE ONLY "ExpenseReimbursementManagementSystem".event_location ALTER COLUMN locationid SET DEFAULT nextval('"ExpenseReimbursementManagementSystem".event_location_locationid_seq'::regclass);


--
-- Name: reimbursement reimbursementid; Type: DEFAULT; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

ALTER TABLE ONLY "ExpenseReimbursementManagementSystem".reimbursement ALTER COLUMN reimbursementid SET DEFAULT nextval('"ExpenseReimbursementManagementSystem".reimbursement_reimbursementid_seq'::regclass);


--
-- Name: reimbursement_confirmation confirmationid; Type: DEFAULT; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

ALTER TABLE ONLY "ExpenseReimbursementManagementSystem".reimbursement_confirmation ALTER COLUMN confirmationid SET DEFAULT nextval('"ExpenseReimbursementManagementSystem".reimbursement_confirmation_confirmationid_seq'::regclass);


--
-- Name: reimbursement_status statusid; Type: DEFAULT; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

ALTER TABLE ONLY "ExpenseReimbursementManagementSystem".reimbursement_status ALTER COLUMN statusid SET DEFAULT nextval('"ExpenseReimbursementManagementSystem".reimbursement_status_statusid_seq'::regclass);


--
-- Name: request requestid; Type: DEFAULT; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

ALTER TABLE ONLY "ExpenseReimbursementManagementSystem".request ALTER COLUMN requestid SET DEFAULT nextval('"ExpenseReimbursementManagementSystem".request_requestid_seq'::regclass);


--
-- Name: request_confirmation confirmationid; Type: DEFAULT; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

ALTER TABLE ONLY "ExpenseReimbursementManagementSystem".request_confirmation ALTER COLUMN confirmationid SET DEFAULT nextval('"ExpenseReimbursementManagementSystem".request_confirmation_confirmationid_seq'::regclass);


--
-- Name: request_image imageid; Type: DEFAULT; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

ALTER TABLE ONLY "ExpenseReimbursementManagementSystem".request_image ALTER COLUMN imageid SET DEFAULT nextval('"ExpenseReimbursementManagementSystem".request_image_imageid_seq'::regclass);


--
-- Name: request_status statusid; Type: DEFAULT; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

ALTER TABLE ONLY "ExpenseReimbursementManagementSystem".request_status ALTER COLUMN statusid SET DEFAULT nextval('"ExpenseReimbursementManagementSystem".request_status_statusid_seq'::regclass);


--
-- Name: roles roleid; Type: DEFAULT; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

ALTER TABLE ONLY "ExpenseReimbursementManagementSystem".roles ALTER COLUMN roleid SET DEFAULT nextval('"ExpenseReimbursementManagementSystem".roles_roleid_seq'::regclass);


--
-- Name: supervisor_approval approvalid; Type: DEFAULT; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

ALTER TABLE ONLY "ExpenseReimbursementManagementSystem".supervisor_approval ALTER COLUMN approvalid SET DEFAULT nextval('"ExpenseReimbursementManagementSystem".supervisor_approval_approvalid_seq'::regclass);


--
-- Name: supervisor_approval_status statusid; Type: DEFAULT; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

ALTER TABLE ONLY "ExpenseReimbursementManagementSystem".supervisor_approval_status ALTER COLUMN statusid SET DEFAULT nextval('"ExpenseReimbursementManagementSystem".supervisor_approval_status_statusid_seq'::regclass);


--
-- Name: users userid; Type: DEFAULT; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

ALTER TABLE ONLY "ExpenseReimbursementManagementSystem".users ALTER COLUMN userid SET DEFAULT nextval('"ExpenseReimbursementManagementSystem".users_userid_seq'::regclass);


--
-- Data for Name: approval_confirmation; Type: TABLE DATA; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

INSERT INTO "ExpenseReimbursementManagementSystem".approval_confirmation VALUES (1, '2021-02-06', 5);


--
-- Data for Name: city_state_postal; Type: TABLE DATA; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

INSERT INTO "ExpenseReimbursementManagementSystem".city_state_postal VALUES (1, 'EVERY', 'WHERE');
INSERT INTO "ExpenseReimbursementManagementSystem".city_state_postal VALUES (2, 'EVERY', 'WHERE');
INSERT INTO "ExpenseReimbursementManagementSystem".city_state_postal VALUES (3, 'ANY', 'WHERE');
INSERT INTO "ExpenseReimbursementManagementSystem".city_state_postal VALUES (4, 'NO', 'WHERE');


--
-- Data for Name: employee_supervisor_jt; Type: TABLE DATA; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

INSERT INTO "ExpenseReimbursementManagementSystem".employee_supervisor_jt VALUES (1, 1, 2);
INSERT INTO "ExpenseReimbursementManagementSystem".employee_supervisor_jt VALUES (2, 1, 3);
INSERT INTO "ExpenseReimbursementManagementSystem".employee_supervisor_jt VALUES (3, 2, 3);
INSERT INTO "ExpenseReimbursementManagementSystem".employee_supervisor_jt VALUES (4, 2, 4);
INSERT INTO "ExpenseReimbursementManagementSystem".employee_supervisor_jt VALUES (5, 1, 4);


--
-- Data for Name: event_location; Type: TABLE DATA; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

INSERT INTO "ExpenseReimbursementManagementSystem".event_location VALUES (1, 100, 'MAIN AVE', 1);
INSERT INTO "ExpenseReimbursementManagementSystem".event_location VALUES (2, 200, 'FREEWAY RD', 1);


--
-- Data for Name: reimbursement; Type: TABLE DATA; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

INSERT INTO "ExpenseReimbursementManagementSystem".reimbursement VALUES (1, 100.01, '2000-01-01', 2, 2);
INSERT INTO "ExpenseReimbursementManagementSystem".reimbursement VALUES (2, 100.01, '2000-01-01', 3, 2);
INSERT INTO "ExpenseReimbursementManagementSystem".reimbursement VALUES (3, 10000.99, '2000-01-01', 4, 2);
INSERT INTO "ExpenseReimbursementManagementSystem".reimbursement VALUES (4, 1.99, '2021-02-10', 5, 1);


--
-- Data for Name: reimbursement_confirmation; Type: TABLE DATA; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

INSERT INTO "ExpenseReimbursementManagementSystem".reimbursement_confirmation VALUES (1, '2021-02-10', 4);


--
-- Data for Name: reimbursement_status; Type: TABLE DATA; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

INSERT INTO "ExpenseReimbursementManagementSystem".reimbursement_status VALUES (1, 'Resolved');
INSERT INTO "ExpenseReimbursementManagementSystem".reimbursement_status VALUES (2, 'Pending');
INSERT INTO "ExpenseReimbursementManagementSystem".reimbursement_status VALUES (3, 'More information required');
INSERT INTO "ExpenseReimbursementManagementSystem".reimbursement_status VALUES (4, 'Request amount changed');


--
-- Data for Name: request; Type: TABLE DATA; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

INSERT INTO "ExpenseReimbursementManagementSystem".request VALUES (1, 100.01, '2021-01-14', 1, 'Anime Convention', 2, 2);
INSERT INTO "ExpenseReimbursementManagementSystem".request VALUES (2, 100.01, '2021-01-14', 1, 'Anime Convention', 4, 2);
INSERT INTO "ExpenseReimbursementManagementSystem".request VALUES (3, 10000.99, '2021-01-17', 2, 'Magic Tricks Boot Camp', 2, 2);
INSERT INTO "ExpenseReimbursementManagementSystem".request VALUES (4, 1.99, '2021-02-01', 2, 'Topology Crash Course', 3, 1);


--
-- Data for Name: request_confirmation; Type: TABLE DATA; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

INSERT INTO "ExpenseReimbursementManagementSystem".request_confirmation VALUES (1, '2021-01-14', 1);
INSERT INTO "ExpenseReimbursementManagementSystem".request_confirmation VALUES (2, '2021-01-14', 2);
INSERT INTO "ExpenseReimbursementManagementSystem".request_confirmation VALUES (3, '2021-01-17', 3);
INSERT INTO "ExpenseReimbursementManagementSystem".request_confirmation VALUES (4, '2021-02-01', 4);


--
-- Data for Name: request_image; Type: TABLE DATA; Schema: ExpenseReimbursementManagementSystem; Owner: -
--



--
-- Data for Name: request_status; Type: TABLE DATA; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

INSERT INTO "ExpenseReimbursementManagementSystem".request_status VALUES (1, 'Resolved');
INSERT INTO "ExpenseReimbursementManagementSystem".request_status VALUES (2, 'Pending');
INSERT INTO "ExpenseReimbursementManagementSystem".request_status VALUES (3, 'More information required');


--
-- Data for Name: roles; Type: TABLE DATA; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

INSERT INTO "ExpenseReimbursementManagementSystem".roles VALUES (1, 'Supervisor');
INSERT INTO "ExpenseReimbursementManagementSystem".roles VALUES (2, 'Employee');


--
-- Data for Name: supervisor_approval; Type: TABLE DATA; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

INSERT INTO "ExpenseReimbursementManagementSystem".supervisor_approval VALUES (1, '2000-01-01', 1, 3, 2, false);
INSERT INTO "ExpenseReimbursementManagementSystem".supervisor_approval VALUES (2, '2000-01-01', 1, 2, 2, false);
INSERT INTO "ExpenseReimbursementManagementSystem".supervisor_approval VALUES (4, '2000-01-01', 2, 5, 2, false);
INSERT INTO "ExpenseReimbursementManagementSystem".supervisor_approval VALUES (5, '2021-02-06', 4, 3, 1, true);
INSERT INTO "ExpenseReimbursementManagementSystem".supervisor_approval VALUES (6, '2021-02-06', 4, 2, 1, true);
INSERT INTO "ExpenseReimbursementManagementSystem".supervisor_approval VALUES (3, '2000-01-01', 2, 4, 1, true);


--
-- Data for Name: supervisor_approval_status; Type: TABLE DATA; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

INSERT INTO "ExpenseReimbursementManagementSystem".supervisor_approval_status VALUES (1, 'Resolved');
INSERT INTO "ExpenseReimbursementManagementSystem".supervisor_approval_status VALUES (2, 'Pending');
INSERT INTO "ExpenseReimbursementManagementSystem".supervisor_approval_status VALUES (3, 'More information required');


--
-- Data for Name: users; Type: TABLE DATA; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

INSERT INTO "ExpenseReimbursementManagementSystem".users VALUES (1, 'admin', '$2a$10$2iPzYhJRQBBn73K38IeyAuFDOJC5QPk/qjnhTPE1tPtJ9aEtUARSq', 'A', 'A', 'a@email.com', 1);
INSERT INTO "ExpenseReimbursementManagementSystem".users VALUES (2, 'employee1', '$2a$10$AcxFYGGpzKK3pYbEX66hUeJcq4sK31W7t8JCkzE9G8Puie3w.9KHy', 'B', 'B', 'b@email.com', 1);
INSERT INTO "ExpenseReimbursementManagementSystem".users VALUES (4, 'employee3', '$2a$10$7gSd84zDIQQ5Z4ht3bCzIOCdNalXRtMhCO80a8m9JtfwP20fv8HAC', 'D', 'D', 'd@email.com', 2);
INSERT INTO "ExpenseReimbursementManagementSystem".users VALUES (5, 'employee4', '$2a$10$ZeextRWPH2z8A1Pv2WKrHe4SWr0SSFgeElLePgMRq7Pgp65u5ReW2', 'E', 'E', 'e@email.com', 2);
INSERT INTO "ExpenseReimbursementManagementSystem".users VALUES (3, 'employee02', '$2a$10$l.O.YdrDGKRrZUK9ODDK2OjyCmQLQV9yvTWdTV8FNoo3r5wmbL.ca', 'C', 'C', 'cabby@email.com', 2);


--
-- Name: approval_confirmation_confirmationid_seq; Type: SEQUENCE SET; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

SELECT pg_catalog.setval('"ExpenseReimbursementManagementSystem".approval_confirmation_confirmationid_seq', 1, true);


--
-- Name: employee_supervisor_jt_hierarchyid_seq; Type: SEQUENCE SET; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

SELECT pg_catalog.setval('"ExpenseReimbursementManagementSystem".employee_supervisor_jt_hierarchyid_seq', 5, true);


--
-- Name: event_location_locationid_seq; Type: SEQUENCE SET; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

SELECT pg_catalog.setval('"ExpenseReimbursementManagementSystem".event_location_locationid_seq', 2, true);


--
-- Name: reimbursement_confirmation_confirmationid_seq; Type: SEQUENCE SET; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

SELECT pg_catalog.setval('"ExpenseReimbursementManagementSystem".reimbursement_confirmation_confirmationid_seq', 1, true);


--
-- Name: reimbursement_reimbursementid_seq; Type: SEQUENCE SET; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

SELECT pg_catalog.setval('"ExpenseReimbursementManagementSystem".reimbursement_reimbursementid_seq', 4, true);


--
-- Name: reimbursement_status_statusid_seq; Type: SEQUENCE SET; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

SELECT pg_catalog.setval('"ExpenseReimbursementManagementSystem".reimbursement_status_statusid_seq', 4, true);


--
-- Name: request_confirmation_confirmationid_seq; Type: SEQUENCE SET; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

SELECT pg_catalog.setval('"ExpenseReimbursementManagementSystem".request_confirmation_confirmationid_seq', 4, true);


--
-- Name: request_image_imageid_seq; Type: SEQUENCE SET; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

SELECT pg_catalog.setval('"ExpenseReimbursementManagementSystem".request_image_imageid_seq', 1, false);


--
-- Name: request_requestid_seq; Type: SEQUENCE SET; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

SELECT pg_catalog.setval('"ExpenseReimbursementManagementSystem".request_requestid_seq', 4, true);


--
-- Name: request_status_statusid_seq; Type: SEQUENCE SET; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

SELECT pg_catalog.setval('"ExpenseReimbursementManagementSystem".request_status_statusid_seq', 3, true);


--
-- Name: roles_roleid_seq; Type: SEQUENCE SET; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

SELECT pg_catalog.setval('"ExpenseReimbursementManagementSystem".roles_roleid_seq', 2, true);


--
-- Name: supervisor_approval_approvalid_seq; Type: SEQUENCE SET; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

SELECT pg_catalog.setval('"ExpenseReimbursementManagementSystem".supervisor_approval_approvalid_seq', 6, true);


--
-- Name: supervisor_approval_status_statusid_seq; Type: SEQUENCE SET; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

SELECT pg_catalog.setval('"ExpenseReimbursementManagementSystem".supervisor_approval_status_statusid_seq', 3, true);


--
-- Name: users_userid_seq; Type: SEQUENCE SET; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

SELECT pg_catalog.setval('"ExpenseReimbursementManagementSystem".users_userid_seq', 5, true);


--
-- Name: approval_confirmation approval_confirmation_pkey; Type: CONSTRAINT; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

ALTER TABLE ONLY "ExpenseReimbursementManagementSystem".approval_confirmation
    ADD CONSTRAINT approval_confirmation_pkey PRIMARY KEY (confirmationid);


--
-- Name: city_state_postal city_state_postal_pkey; Type: CONSTRAINT; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

ALTER TABLE ONLY "ExpenseReimbursementManagementSystem".city_state_postal
    ADD CONSTRAINT city_state_postal_pkey PRIMARY KEY (postalcode);


--
-- Name: employee_supervisor_jt employee_supervisor_jt_hierarchyid_key; Type: CONSTRAINT; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

ALTER TABLE ONLY "ExpenseReimbursementManagementSystem".employee_supervisor_jt
    ADD CONSTRAINT employee_supervisor_jt_hierarchyid_key UNIQUE (hierarchyid);


--
-- Name: employee_supervisor_jt employee_supervisor_jt_pkey; Type: CONSTRAINT; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

ALTER TABLE ONLY "ExpenseReimbursementManagementSystem".employee_supervisor_jt
    ADD CONSTRAINT employee_supervisor_jt_pkey PRIMARY KEY (useridsupervisor, useridemployee);


--
-- Name: event_location event_location_locationid_key; Type: CONSTRAINT; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

ALTER TABLE ONLY "ExpenseReimbursementManagementSystem".event_location
    ADD CONSTRAINT event_location_locationid_key UNIQUE (locationid);


--
-- Name: event_location event_location_pkey; Type: CONSTRAINT; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

ALTER TABLE ONLY "ExpenseReimbursementManagementSystem".event_location
    ADD CONSTRAINT event_location_pkey PRIMARY KEY (street_number, street_name, postalcode);


--
-- Name: reimbursement_confirmation reimbursement_confirmation_pkey; Type: CONSTRAINT; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

ALTER TABLE ONLY "ExpenseReimbursementManagementSystem".reimbursement_confirmation
    ADD CONSTRAINT reimbursement_confirmation_pkey PRIMARY KEY (confirmationid);


--
-- Name: reimbursement reimbursement_pkey; Type: CONSTRAINT; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

ALTER TABLE ONLY "ExpenseReimbursementManagementSystem".reimbursement
    ADD CONSTRAINT reimbursement_pkey PRIMARY KEY (reimbursementid);


--
-- Name: reimbursement_status reimbursement_status_pkey; Type: CONSTRAINT; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

ALTER TABLE ONLY "ExpenseReimbursementManagementSystem".reimbursement_status
    ADD CONSTRAINT reimbursement_status_pkey PRIMARY KEY (statusid);


--
-- Name: reimbursement_status reimbursement_status_status_key; Type: CONSTRAINT; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

ALTER TABLE ONLY "ExpenseReimbursementManagementSystem".reimbursement_status
    ADD CONSTRAINT reimbursement_status_status_key UNIQUE (status);


--
-- Name: request_confirmation request_confirmation_pkey; Type: CONSTRAINT; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

ALTER TABLE ONLY "ExpenseReimbursementManagementSystem".request_confirmation
    ADD CONSTRAINT request_confirmation_pkey PRIMARY KEY (confirmationid);


--
-- Name: request_image request_image_pkey; Type: CONSTRAINT; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

ALTER TABLE ONLY "ExpenseReimbursementManagementSystem".request_image
    ADD CONSTRAINT request_image_pkey PRIMARY KEY (imageid);


--
-- Name: request request_pkey; Type: CONSTRAINT; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

ALTER TABLE ONLY "ExpenseReimbursementManagementSystem".request
    ADD CONSTRAINT request_pkey PRIMARY KEY (requestid);


--
-- Name: request_status request_status_pkey; Type: CONSTRAINT; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

ALTER TABLE ONLY "ExpenseReimbursementManagementSystem".request_status
    ADD CONSTRAINT request_status_pkey PRIMARY KEY (statusid);


--
-- Name: request_status request_status_status_key; Type: CONSTRAINT; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

ALTER TABLE ONLY "ExpenseReimbursementManagementSystem".request_status
    ADD CONSTRAINT request_status_status_key UNIQUE (status);


--
-- Name: roles roles_pkey; Type: CONSTRAINT; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

ALTER TABLE ONLY "ExpenseReimbursementManagementSystem".roles
    ADD CONSTRAINT roles_pkey PRIMARY KEY (roleid);


--
-- Name: roles roles_roles_key; Type: CONSTRAINT; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

ALTER TABLE ONLY "ExpenseReimbursementManagementSystem".roles
    ADD CONSTRAINT roles_roles_key UNIQUE (roles);


--
-- Name: supervisor_approval supervisor_approval_pkey; Type: CONSTRAINT; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

ALTER TABLE ONLY "ExpenseReimbursementManagementSystem".supervisor_approval
    ADD CONSTRAINT supervisor_approval_pkey PRIMARY KEY (approvalid);


--
-- Name: supervisor_approval_status supervisor_approval_status_pkey; Type: CONSTRAINT; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

ALTER TABLE ONLY "ExpenseReimbursementManagementSystem".supervisor_approval_status
    ADD CONSTRAINT supervisor_approval_status_pkey PRIMARY KEY (statusid);


--
-- Name: supervisor_approval_status supervisor_approval_status_status_key; Type: CONSTRAINT; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

ALTER TABLE ONLY "ExpenseReimbursementManagementSystem".supervisor_approval_status
    ADD CONSTRAINT supervisor_approval_status_status_key UNIQUE (status);


--
-- Name: users users_email_key; Type: CONSTRAINT; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

ALTER TABLE ONLY "ExpenseReimbursementManagementSystem".users
    ADD CONSTRAINT users_email_key UNIQUE (email);


--
-- Name: users users_loginusername_key; Type: CONSTRAINT; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

ALTER TABLE ONLY "ExpenseReimbursementManagementSystem".users
    ADD CONSTRAINT users_loginusername_key UNIQUE (loginusername);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

ALTER TABLE ONLY "ExpenseReimbursementManagementSystem".users
    ADD CONSTRAINT users_pkey PRIMARY KEY (userid);


--
-- Name: approval_confirmation approval_confirmation_approvalid_fkey; Type: FK CONSTRAINT; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

ALTER TABLE ONLY "ExpenseReimbursementManagementSystem".approval_confirmation
    ADD CONSTRAINT approval_confirmation_approvalid_fkey FOREIGN KEY (approvalid) REFERENCES "ExpenseReimbursementManagementSystem".supervisor_approval(approvalid);


--
-- Name: employee_supervisor_jt employee_supervisor_jt_useridemployee_fkey; Type: FK CONSTRAINT; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

ALTER TABLE ONLY "ExpenseReimbursementManagementSystem".employee_supervisor_jt
    ADD CONSTRAINT employee_supervisor_jt_useridemployee_fkey FOREIGN KEY (useridemployee) REFERENCES "ExpenseReimbursementManagementSystem".users(userid);


--
-- Name: employee_supervisor_jt employee_supervisor_jt_useridsupervisor_fkey; Type: FK CONSTRAINT; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

ALTER TABLE ONLY "ExpenseReimbursementManagementSystem".employee_supervisor_jt
    ADD CONSTRAINT employee_supervisor_jt_useridsupervisor_fkey FOREIGN KEY (useridsupervisor) REFERENCES "ExpenseReimbursementManagementSystem".users(userid);


--
-- Name: event_location event_location_postalcode_fkey; Type: FK CONSTRAINT; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

ALTER TABLE ONLY "ExpenseReimbursementManagementSystem".event_location
    ADD CONSTRAINT event_location_postalcode_fkey FOREIGN KEY (postalcode) REFERENCES "ExpenseReimbursementManagementSystem".city_state_postal(postalcode);


--
-- Name: reimbursement_confirmation reimbursement_confirmation_reimbursementid_fkey; Type: FK CONSTRAINT; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

ALTER TABLE ONLY "ExpenseReimbursementManagementSystem".reimbursement_confirmation
    ADD CONSTRAINT reimbursement_confirmation_reimbursementid_fkey FOREIGN KEY (reimbursementid) REFERENCES "ExpenseReimbursementManagementSystem".reimbursement(reimbursementid);


--
-- Name: reimbursement reimbursement_finalapprovalid_fkey; Type: FK CONSTRAINT; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

ALTER TABLE ONLY "ExpenseReimbursementManagementSystem".reimbursement
    ADD CONSTRAINT reimbursement_finalapprovalid_fkey FOREIGN KEY (finalapprovalid) REFERENCES "ExpenseReimbursementManagementSystem".supervisor_approval(approvalid);


--
-- Name: reimbursement reimbursement_statusid_fkey; Type: FK CONSTRAINT; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

ALTER TABLE ONLY "ExpenseReimbursementManagementSystem".reimbursement
    ADD CONSTRAINT reimbursement_statusid_fkey FOREIGN KEY (statusid) REFERENCES "ExpenseReimbursementManagementSystem".reimbursement_status(statusid);


--
-- Name: request_confirmation request_confirmation_requestid_fkey; Type: FK CONSTRAINT; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

ALTER TABLE ONLY "ExpenseReimbursementManagementSystem".request_confirmation
    ADD CONSTRAINT request_confirmation_requestid_fkey FOREIGN KEY (requestid) REFERENCES "ExpenseReimbursementManagementSystem".request(requestid);


--
-- Name: request request_eventlocation_fkey; Type: FK CONSTRAINT; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

ALTER TABLE ONLY "ExpenseReimbursementManagementSystem".request
    ADD CONSTRAINT request_eventlocation_fkey FOREIGN KEY (eventlocation) REFERENCES "ExpenseReimbursementManagementSystem".event_location(locationid);


--
-- Name: request_image request_image_requestid_fkey; Type: FK CONSTRAINT; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

ALTER TABLE ONLY "ExpenseReimbursementManagementSystem".request_image
    ADD CONSTRAINT request_image_requestid_fkey FOREIGN KEY (requestid) REFERENCES "ExpenseReimbursementManagementSystem".request(requestid);


--
-- Name: request request_requesteruserid_fkey; Type: FK CONSTRAINT; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

ALTER TABLE ONLY "ExpenseReimbursementManagementSystem".request
    ADD CONSTRAINT request_requesteruserid_fkey FOREIGN KEY (requesteruserid) REFERENCES "ExpenseReimbursementManagementSystem".users(userid);


--
-- Name: request request_statusid_fkey; Type: FK CONSTRAINT; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

ALTER TABLE ONLY "ExpenseReimbursementManagementSystem".request
    ADD CONSTRAINT request_statusid_fkey FOREIGN KEY (statusid) REFERENCES "ExpenseReimbursementManagementSystem".request_status(statusid);


--
-- Name: supervisor_approval supervisor_approval_hierarchyid_fkey; Type: FK CONSTRAINT; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

ALTER TABLE ONLY "ExpenseReimbursementManagementSystem".supervisor_approval
    ADD CONSTRAINT supervisor_approval_hierarchyid_fkey FOREIGN KEY (hierarchyid) REFERENCES "ExpenseReimbursementManagementSystem".employee_supervisor_jt(hierarchyid);


--
-- Name: supervisor_approval supervisor_approval_requestid_fkey; Type: FK CONSTRAINT; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

ALTER TABLE ONLY "ExpenseReimbursementManagementSystem".supervisor_approval
    ADD CONSTRAINT supervisor_approval_requestid_fkey FOREIGN KEY (requestid) REFERENCES "ExpenseReimbursementManagementSystem".request(requestid);


--
-- Name: supervisor_approval supervisor_approval_statusid_fkey; Type: FK CONSTRAINT; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

ALTER TABLE ONLY "ExpenseReimbursementManagementSystem".supervisor_approval
    ADD CONSTRAINT supervisor_approval_statusid_fkey FOREIGN KEY (statusid) REFERENCES "ExpenseReimbursementManagementSystem".supervisor_approval_status(statusid);


--
-- Name: users users_roles_fkey; Type: FK CONSTRAINT; Schema: ExpenseReimbursementManagementSystem; Owner: -
--

ALTER TABLE ONLY "ExpenseReimbursementManagementSystem".users
    ADD CONSTRAINT users_roles_fkey FOREIGN KEY (roles) REFERENCES "ExpenseReimbursementManagementSystem".roles(roleid);


--
-- PostgreSQL database dump complete
--

\unrestrict vZLhJzk0wkAxCnjn5cZ5iMyVeesyhUg6Sp6wukbQC0bfQsQcNTSiejkpPLnGI0K

