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

-- Admin user: 6 wins, 5 losses -> 6*(5, 13, 9.5) + 5*(3, 6, 5.5) = 45 goals, 108 sidegoals, 84.5 pts
INSERT INTO users (id, username, email, password, role, created, subexpiration, enabled, rating, wins, losses,
                   goals, points, sidegoals, blocks, steals, passes, kills, deaths, turnovers, rebounds)
VALUES (24, 'markd315', 'markd315@gmail.com',
        '$2a$12$OPJoXUBmnuUHH/5lsXLDLep56M8gsQ4dzqWTkIJnSDun2HGV39Jo.',
        'ADMIN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1, 1020.0, 6, 5,
        45, 84.5, 108, 50, 28, 149, 17, 16, 17, 111)
ON DUPLICATE KEY UPDATE rating = VALUES(rating), wins = VALUES(wins), losses = VALUES(losses),
                        goals = VALUES(goals), points = VALUES(points), sidegoals = VALUES(sidegoals),
                        blocks = VALUES(blocks), steals = VALUES(steals), passes = VALUES(passes),
                        kills = VALUES(kills), deaths = VALUES(deaths), turnovers = VALUES(turnovers),
                        rebounds = VALUES(rebounds);

-- Test/dev users (all share the same bcrypt password hash)
-- Win:  5 goals, 13 sidegoals, 9.5 pts, 5 blocks, 3 steals, 14 passes, 2 kills, 1 death, 1 to, 11 rebounds
-- Loss: 3 goals,  6 sidegoals, 5.5 pts, 4 blocks, 2 steals, 13 passes, 1 kill,  2 deaths, 2 to,  9 rebounds
INSERT INTO users (id, username, email, password, role, created, subexpiration, enabled, rating, wins, losses,
                   goals, points, sidegoals, blocks, steals, passes, kills, deaths, turnovers, rebounds) VALUES
(1,  'u1',         'e1@gmail.com',         '$2a$12$OPJoXUBmnuUHH/5lsXLDLep56M8gsQ4dzqWTkIJnSDun2HGV39Jo.', 'USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1, 960.0, 4, 7,
 41, 76.5, 94, 48, 26, 147, 15, 18, 18, 107),
(2,  'u2',         'e2@gmail.com',         '$2a$12$OPJoXUBmnuUHH/5lsXLDLep56M8gsQ4dzqWTkIJnSDun2HGV39Jo.', 'USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1, 1150.0, 10, 1,
 53, 100.5, 136, 54, 32, 153, 21, 12, 11, 119),
(3,  'u3',         'e3@gmail.com',         '$2a$12$OPJoXUBmnuUHH/5lsXLDLep56M8gsQ4dzqWTkIJnSDun2HGV39Jo.', 'USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1, 850.0, 1, 10,
 35, 64.5, 73, 45, 23, 144, 12, 21, 21, 99),
(4,  'u4',         'e4@gmail.com',         '$2a$12$OPJoXUBmnuUHH/5lsXLDLep56M8gsQ4dzqWTkIJnSDun2HGV39Jo.', 'USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1, 1650.0, 42, 1,
 213, 404.5, 552, 214, 128, 601, 85, 44, 43, 471),
(5,  'u5',         'e5@gmail.com',         '$2a$12$OPJoXUBmnuUHH/5lsXLDLep56M8gsQ4dzqWTkIJnSDun2HGV39Jo.', 'USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1, 450.0, 1, 42,
 131, 240.5, 265, 173, 87, 560, 44, 85, 85, 389),
(6,  'u6',         'e6@gmail.com',         '$2a$12$OPJoXUBmnuUHH/5lsXLDLep56M8gsQ4dzqWTkIJnSDun2HGV39Jo.', 'USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1, 1015.0, 3, 2,
 21, 39.5, 51, 23, 13, 68, 8, 7, 7, 51),
(7,  'u7',         'e7@gmail.com',         '$2a$12$OPJoXUBmnuUHH/5lsXLDLep56M8gsQ4dzqWTkIJnSDun2HGV39Jo.', 'USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1, 1000.0, 0, 0,
 0, 0.0, 0, 0, 0, 0, 0, 0, 0, 0),
(8,  'u8',         'e8@gmail.com',         '$2a$12$OPJoXUBmnuUHH/5lsXLDLep56M8gsQ4dzqWTkIJnSDun2HGV39Jo.', 'USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1, 1000.0, 0, 0,
 0, 0.0, 0, 0, 0, 0, 0, 0, 0, 0),
(9,  'u0',         'e0@gmail.com',         '$2a$12$OPJoXUBmnuUHH/5lsXLDLep56M8gsQ4dzqWTkIJnSDun2HGV39Jo.', 'USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1, 1000.0, 0, 0,
 0, 0.0, 0, 0, 0, 0, 0, 0, 0, 0),
(11, 'mattbuster', 'mattbuster@gmail.com', '$2a$12$OPJoXUBmnuUHH/5lsXLDLep56M8gsQ4dzqWTkIJnSDun2HGV39Jo.', 'USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1, 1000.0, 0, 0,
 0, 0.0, 0, 0, 0, 0, 0, 0, 0, 0),
(12,  'matt',       'matt@gmail.com',         '$2a$12$OPJoXUBmnuUHH/5lsXLDLep56M8gsQ4dzqWTkIJnSDun2HGV39Jo.', 'USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1, 1000.0, 0, 0,
 0, 0.0, 0, 0, 0, 0, 0, 0, 0, 0),
(13,  'rick',         'rick@gmail.com',         '$2a$12$OPJoXUBmnuUHH/5lsXLDLep56M8gsQ4dzqWTkIJnSDun2HGV39Jo.', 'USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1, 1000.0, 0, 0,
 0, 0.0, 0, 0, 0, 0, 0, 0, 0, 0),
(14,  'mark',         'mark@gmail.com',         '$2a$12$OPJoXUBmnuUHH/5lsXLDLep56M8gsQ4dzqWTkIJnSDun2HGV39Jo.', 'USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1, 1000.0, 0, 0,
 0, 0.0, 0, 0, 0, 0, 0, 0, 0, 0),
(15,  'heather',         'heather@gmail.com',         '$2a$12$OPJoXUBmnuUHH/5lsXLDLep56M8gsQ4dzqWTkIJnSDun2HGV39Jo.', 'USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1, 1000.0, 0, 0,
 0, 0.0, 0, 0, 0, 0, 0, 0, 0, 0),
(16,  'kerri',         'kerri@gmail.com',         '$2a$12$OPJoXUBmnuUHH/5lsXLDLep56M8gsQ4dzqWTkIJnSDun2HGV39Jo.', 'USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1, 1000.0, 0, 0,
 0, 0.0, 0, 0, 0, 0, 0, 0, 0, 0),
(17,  'john',         'john@gmail.com',         '$2a$12$OPJoXUBmnuUHH/5lsXLDLep56M8gsQ4dzqWTkIJnSDun2HGV39Jo.', 'USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1, 1000.0, 0, 0,
 0, 0.0, 0, 0, 0, 0, 0, 0, 0, 0),
(18,  'krystal',         'krystal@gmail.com',         '$2a$12$OPJoXUBmnuUHH/5lsXLDLep56M8gsQ4dzqWTkIJnSDun2HGV39Jo.', 'USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1, 1000.0, 0, 0,
 0, 0.0, 0, 0, 0, 0, 0, 0, 0, 0),
(19,  'ricky',         'ricky@gmail.com',         '$2a$12$OPJoXUBmnuUHH/5lsXLDLep56M8gsQ4dzqWTkIJnSDun2HGV39Jo.', 'USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1, 1000.0, 0, 0,
 0, 0.0, 0, 0, 0, 0, 0, 0, 0, 0),
(20,  'pam',         'pam@gmail.com',         '$2a$12$OPJoXUBmnuUHH/5lsXLDLep56M8gsQ4dzqWTkIJnSDun2HGV39Jo.', 'USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1, 1000.0, 0, 0,
 0, 0.0, 0, 0, 0, 0, 0, 0, 0, 0),
(21,  'tim',         'tim@gmail.com',         '$2a$12$OPJoXUBmnuUHH/5lsXLDLep56M8gsQ4dzqWTkIJnSDun2HGV39Jo.', 'USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1, 1000.0, 0, 0,
 0, 0.0, 0, 0, 0, 0, 0, 0, 0, 0),
(22,  'brianna',         'brianna@gmail.com',         '$2a$12$OPJoXUBmnuUHH/5lsXLDLep56M8gsQ4dzqWTkIJnSDun2HGV39Jo.', 'USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1, 1000.0, 0, 0,
 0, 0.0, 0, 0, 0, 0, 0, 0, 0, 0),
(23,  'xheni',         'xheni@gmail.com',         '$2a$12$OPJoXUBmnuUHH/5lsXLDLep56M8gsQ4dzqWTkIJnSDun2HGV39Jo.', 'USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1, 1000.0, 0, 0,
 0, 0.0, 0, 0, 0, 0, 0, 0, 0, 0),
(25,  'christian',         'christian@gmail.com',         '$2a$12$OPJoXUBmnuUHH/5lsXLDLep56M8gsQ4dzqWTkIJnSDun2HGV39Jo.', 'USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1, 1000.0, 0, 0,
 0, 0.0, 0, 0, 0, 0, 0, 0, 0, 0)
ON DUPLICATE KEY UPDATE rating = VALUES(rating), wins = VALUES(wins), losses = VALUES(losses),
                        goals = VALUES(goals), points = VALUES(points), sidegoals = VALUES(sidegoals),
                        blocks = VALUES(blocks), steals = VALUES(steals), passes = VALUES(passes),
                        kills = VALUES(kills), deaths = VALUES(deaths), turnovers = VALUES(turnovers),
                        rebounds = VALUES(rebounds);




-- Class stat rows (one per playable class)
INSERT INTO classstat (role) VALUES
('GOALIE'), ('WARRIOR'), ('RANGER'), ('DASHER'), ('MARKSMAN'),
('STEALTH'), ('SUPPORT'), ('ARTISAN'), ('GOLEM'), ('MAGE'),
('BUILDER'), ('GRENADIER'), ('HOUNDMASTER'), ('CAPTAIN'), ('SPIDER');
