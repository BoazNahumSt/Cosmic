CREATE TABLE ip_ban
(
    ip         varchar(15)             NOT NULL,
    account_id integer,
    created_at timestamp DEFAULT now() NOT NULL,
    PRIMARY KEY (ip)
);
GRANT SELECT, INSERT ON TABLE ip_ban TO ${server-username};

CREATE TABLE hwid_ban
(
    hwid       varchar(30)             NOT NULL,
    account_id integer,
    created_at timestamp DEFAULT now() NOT NULL,
    PRIMARY KEY (hwid)
);
GRANT SELECT ON TABLE hwid_ban TO ${server-username};

CREATE TABLE mac_ban
(
    mac varchar(30) NOT NULL,
account_id integer,
    created_at timestamp DEFAULT now() NOT NULL,
    PRIMARY KEY (mac)
);
GRANT SELECT, INSERT ON TABLE mac_ban TO ${server-username};