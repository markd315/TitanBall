CREATE DATABASE IF NOT EXISTS titanball;
USE titanball;

-- Users table (players + admins)
CREATE TABLE users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(255),
    email VARCHAR(255),
    password VARCHAR(70),
    role VARCHAR(10),
    rating DOUBLE DEFAULT 1000.0,
    wins INT DEFAULT 0,
    losses INT DEFAULT 0,
    ties INT DEFAULT 0,
    created TIMESTAMP,
    goals INT DEFAULT 0,
    points DOUBLE DEFAULT 0.0,
    sidegoals INT DEFAULT 0,
    blocks INT DEFAULT 0,
    steals INT DEFAULT 0,
    passes INT DEFAULT 0,
    kills INT DEFAULT 0,
    deaths INT DEFAULT 0,
    turnovers INT DEFAULT 0,
    killassists INT DEFAULT 0,
    goalassists INT DEFAULT 0,
    rebounds INT DEFAULT 0,
    rating_1v1 DOUBLE DEFAULT 1000.0,
    wins_1v1 INT DEFAULT 0,
    losses_1v1 INT DEFAULT 0,
    ties_1v1 INT DEFAULT 0,
    goals_1v1 INT DEFAULT 0,
    points_1v1 DOUBLE DEFAULT 0.0,
    sidegoals_1v1 INT DEFAULT 0,
    blocks_1v1 INT DEFAULT 0,
    steals_1v1 INT DEFAULT 0,
    passes_1v1 INT DEFAULT 0,
    kills_1v1 INT DEFAULT 0,
    deaths_1v1 INT DEFAULT 0,
    turnovers_1v1 INT DEFAULT 0,
    killassists_1v1 INT DEFAULT 0,
    goalassists_1v1 INT DEFAULT 0,
    rebounds_1v1 INT DEFAULT 0,
    activation VARCHAR(10),
    subexpiration TIMESTAMP,
    enabled BOOLEAN DEFAULT 1
);

-- Per-class aggregate stats
CREATE TABLE classstat (
    id INT AUTO_INCREMENT PRIMARY KEY,
    role VARCHAR(32) UNIQUE,
    wins INT DEFAULT 0,
    losses INT DEFAULT 0,
    ties INT DEFAULT 0,
    goals INT DEFAULT 0,
    points DOUBLE DEFAULT 0.0,
    sidegoals INT DEFAULT 0,
    blocks INT DEFAULT 0,
    steals INT DEFAULT 0,
    passes INT DEFAULT 0,
    kills INT DEFAULT 0,
    deaths INT DEFAULT 0,
    turnovers INT DEFAULT 0,
    killassists INT DEFAULT 0,
    goalassists INT DEFAULT 0,
    rebounds INT DEFAULT 0,
    saves INT DEFAULT 0,
    lasthits INT DEFAULT 0,
    miniondamage DOUBLE DEFAULT 0.0,
    upgradesgold INT DEFAULT 0,
    consumablesgold INT DEFAULT 0,
    sidegoalsaves INT DEFAULT 0,
    centergoalsaves INT DEFAULT 0,
    sidegoalsconceded INT DEFAULT 0,
    goalsconceded INT DEFAULT 0,
    manaspent INT DEFAULT 0
);

-- Premade team stats
CREATE TABLE premadestats (
    id INT AUTO_INCREMENT PRIMARY KEY,
    teamname VARCHAR(190) UNIQUE,
    topemail VARCHAR(255),
    midemail VARCHAR(255),
    botemail VARCHAR(255),
    topconfirmed TINYINT(1) DEFAULT 0,
    midconfirmed TINYINT(1) DEFAULT 0,
    botconfirmed TINYINT(1) DEFAULT 0,
    topqueued TINYINT(1) DEFAULT 0,
    midqueued TINYINT(1) DEFAULT 0,
    botqueued TINYINT(1) DEFAULT 0,
    rating DOUBLE DEFAULT 1000.0,
    points DOUBLE DEFAULT 0.0,
    wins INT DEFAULT 0,
    losses INT DEFAULT 0,
    ties INT DEFAULT 0,
    sidegoals INT DEFAULT 0,
    steals INT DEFAULT 0,
    blocks INT DEFAULT 0,
    passes INT DEFAULT 0,
    kills INT DEFAULT 0,
    deaths INT DEFAULT 0,
    turnovers INT DEFAULT 0,
    killassists INT DEFAULT 0,
    goalassists INT DEFAULT 0,
    rebounds INT DEFAULT 0,
    goals INT DEFAULT 0
);

-- ============================================================
-- Seed data (from existing Dockerfie)
-- ============================================================

-- Admin user
INSERT INTO users (id, username, email, password, role, created, subexpiration, enabled)
VALUES (24, 'markd315', 'markd315@gmail.com',
        '$2a$12$OPJoXUBmnuUHH/5lsXLDLep56M8gsQ4dzqWTkIJnSDun2HGV39Jo.',
        'ADMIN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1);

-- Test/dev users (all share the same bcrypt password hash)
INSERT INTO users (id, username, email, password, role, created, subexpiration, enabled) VALUES
(1,  'u1',         'e1@gmail.com',         '$2a$12$OPJoXUBmnuUHH/5lsXLDLep56M8gsQ4dzqWTkIJnSDun2HGV39Jo.', 'USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1),
(2,  'u2',         'e2@gmail.com',         '$2a$12$OPJoXUBmnuUHH/5lsXLDLep56M8gsQ4dzqWTkIJnSDun2HGV39Jo.', 'USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1),
(3,  'u3',         'e3@gmail.com',         '$2a$12$OPJoXUBmnuUHH/5lsXLDLep56M8gsQ4dzqWTkIJnSDun2HGV39Jo.', 'USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1),
(4,  'u4',         'e4@gmail.com',         '$2a$12$OPJoXUBmnuUHH/5lsXLDLep56M8gsQ4dzqWTkIJnSDun2HGV39Jo.', 'USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1),
(5,  'u5',         'e5@gmail.com',         '$2a$12$OPJoXUBmnuUHH/5lsXLDLep56M8gsQ4dzqWTkIJnSDun2HGV39Jo.', 'USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1),
(6,  'u6',         'e6@gmail.com',         '$2a$12$OPJoXUBmnuUHH/5lsXLDLep56M8gsQ4dzqWTkIJnSDun2HGV39Jo.', 'USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1),
(7,  'u7',         'e7@gmail.com',         '$2a$12$OPJoXUBmnuUHH/5lsXLDLep56M8gsQ4dzqWTkIJnSDun2HGV39Jo.', 'USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1),
(8,  'u8',         'e8@gmail.com',         '$2a$12$OPJoXUBmnuUHH/5lsXLDLep56M8gsQ4dzqWTkIJnSDun2HGV39Jo.', 'USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1),
(9,  'u0',         'e0@gmail.com',         '$2a$12$OPJoXUBmnuUHH/5lsXLDLep56M8gsQ4dzqWTkIJnSDun2HGV39Jo.', 'USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1),
(11, 'mattbuster', 'mattbuster@gmail.com', '$2a$12$OPJoXUBmnuUHH/5lsXLDLep56M8gsQ4dzqWTkIJnSDun2HGV39Jo.', 'USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1),
(12,  'matt',       'matt@gmail.com',         '$2a$12$OPJoXUBmnuUHH/5lsXLDLep56M8gsQ4dzqWTkIJnSDun2HGV39Jo.', 'USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1),
(13,  'rick',         'rick@gmail.com',         '$2a$12$OPJoXUBmnuUHH/5lsXLDLep56M8gsQ4dzqWTkIJnSDun2HGV39Jo.', 'USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1),
(14,  'mark',         'mark@gmail.com',         '$2a$12$OPJoXUBmnuUHH/5lsXLDLep56M8gsQ4dzqWTkIJnSDun2HGV39Jo.', 'USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1),
(15,  'heather',         'heather@gmail.com',         '$2a$12$OPJoXUBmnuUHH/5lsXLDLep56M8gsQ4dzqWTkIJnSDun2HGV39Jo.', 'USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1),
(16,  'kerri',         'kerri@gmail.com',         '$2a$12$OPJoXUBmnuUHH/5lsXLDLep56M8gsQ4dzqWTkIJnSDun2HGV39Jo.', 'USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1),
(17,  'john',         'john@gmail.com',         '$2a$12$OPJoXUBmnuUHH/5lsXLDLep56M8gsQ4dzqWTkIJnSDun2HGV39Jo.', 'USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1),
(18,  'krystal',         'krystal@gmail.com',         '$2a$12$OPJoXUBmnuUHH/5lsXLDLep56M8gsQ4dzqWTkIJnSDun2HGV39Jo.', 'USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1),
(19,  'ricky',         'ricky@gmail.com',         '$2a$12$OPJoXUBmnuUHH/5lsXLDLep56M8gsQ4dzqWTkIJnSDun2HGV39Jo.', 'USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1),
(20,  'pam',         'pam@gmail.com',         '$2a$12$OPJoXUBmnuUHH/5lsXLDLep56M8gsQ4dzqWTkIJnSDun2HGV39Jo.', 'USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1),
(21,  'tim',         'tim@gmail.com',         '$2a$12$OPJoXUBmnuUHH/5lsXLDLep56M8gsQ4dzqWTkIJnSDun2HGV39Jo.', 'USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1),
(22,  'brianna',         'brianna@gmail.com',         '$2a$12$OPJoXUBmnuUHH/5lsXLDLep56M8gsQ4dzqWTkIJnSDun2HGV39Jo.', 'USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1),
(23,  'xheni',         'xheni@gmail.com',         '$2a$12$OPJoXUBmnuUHH/5lsXLDLep56M8gsQ4dzqWTkIJnSDun2HGV39Jo.', 'USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1),
(25,  'christian',         'christian@gmail.com',         '$2a$12$OPJoXUBmnuUHH/5lsXLDLep56M8gsQ4dzqWTkIJnSDun2HGV39Jo.', 'USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1);




-- Class stat rows (one per playable class)
INSERT INTO classstat (role) VALUES
('GOALIE'), ('WARRIOR'), ('RANGER'), ('DASHER'), ('MARKSMAN'),
('STEALTH'), ('SUPPORT'), ('ARTISAN'), ('GOLEM'), ('MAGE'),
('BUILDER'), ('GRENADIER'), ('HOUNDMASTER'), ('CAPTAIN'), ('SPIDER');
