-- =========================================================
-- QueueFlow_Setup.sql
-- Purpose: Create QueueFlowDB, its six tables, and sample data.
-- Run this file ONCE on a fresh database.
-- Do not add practice UPDATE/DELETE statements here.
-- =========================================================

-- 1. DATABASE CREATION
CREATE DATABASE IF NOT EXISTS QueueFlowDB;
USE QueueFlowDB;


-- 2. TABLE CREATION

CREATE TABLE Agent (
    AgentID INT PRIMARY KEY AUTO_INCREMENT,
    FirstName VARCHAR(50),
    LastName VARCHAR(50),
    Status VARCHAR(20) DEFAULT 'Offline'
);

CREATE TABLE Skill (
    SkillID INT PRIMARY KEY AUTO_INCREMENT,
    SkillName VARCHAR(50)
);

CREATE TABLE AgentSkill (
    AgentSkillID INT PRIMARY KEY AUTO_INCREMENT,
    AgentID INT,
    SkillID INT,
    ProficiencyLevel INT,
    FOREIGN KEY (AgentID) REFERENCES Agent(AgentID),
    FOREIGN KEY (SkillID) REFERENCES Skill(SkillID)
);

CREATE TABLE Customer (
    CustomerID INT PRIMARY KEY AUTO_INCREMENT,
    FirstName VARCHAR(50),
    LastName VARCHAR(50),
    Tier VARCHAR(20) DEFAULT 'Standard'
);

CREATE TABLE Request (
    RequestID INT PRIMARY KEY AUTO_INCREMENT,
    CustomerID INT,
    RequiredSkillID INT,
    Priority VARCHAR(20),
    Channel VARCHAR(20),
    ReceivedAt DATETIME,
    Status VARCHAR(20) DEFAULT 'Queued',
    FOREIGN KEY (CustomerID) REFERENCES Customer(CustomerID),
    FOREIGN KEY (RequiredSkillID) REFERENCES Skill(SkillID)
);

CREATE TABLE RoutingDecision (
    DecisionID INT PRIMARY KEY AUTO_INCREMENT,
    RequestID INT,
    AgentID INT,
    RoutedAt DATETIME,
    WaitSeconds INT,
    FOREIGN KEY (RequestID) REFERENCES Request(RequestID),
    FOREIGN KEY (AgentID) REFERENCES Agent(AgentID)
);


-- 3. INITIAL SAMPLE DATA

INSERT INTO Agent (FirstName, LastName, Status)
VALUES
('Arun', 'Kumar', 'Available'),
('Priya', 'Sharma', 'Available'),
('Rahul', 'Raj', 'Busy'),
('Divya', 'Mohan', 'Available'),
('Karthik', 'Suresh', 'Offline'),
('Meena', 'Devi', 'Available'),
('Vijay', 'Kumar', 'Busy'),
('Anitha', 'Ravi', 'Available'),
('Sanjay', 'Kumar', 'Offline'),
('Sneha', 'Raj', 'Available');

INSERT INTO Skill (SkillName)
VALUES
('Technical Support'),
('Billing'),
('Sales'),
('Refund Processing'),
('Account Management'),
('Order Management'),
('Delivery Support'),
('Product Enquiry'),
('Complaint Handling'),
('Customer Retention');

INSERT INTO AgentSkill (AgentID, SkillID, ProficiencyLevel)
VALUES
(1, 1, 5),
(1, 2, 4),
(2, 2, 5),
(2, 3, 4),
(3, 1, 3),
(3, 4, 5),
(4, 3, 4),
(4, 5, 5),
(5, 6, 3),
(5, 7, 4),
(6, 2, 4),
(6, 8, 5);

INSERT INTO Customer (FirstName, LastName, Tier)
VALUES
('Simi', 'Jersalin', 'Premium'),
('Arun', 'Kumar', 'Standard'),
('Priya', 'Sharma', 'Gold'),
('Rahul', 'Raj', 'Standard'),
('Divya', 'Mohan', 'Premium'),
('Karthik', 'Suresh', 'Gold'),
('Meena', 'Devi', 'Standard'),
('Vijay', 'Kumar', 'Premium'),
('Anitha', 'Ravi', 'Gold'),
('Sneha', 'Raj', 'Standard');

INSERT INTO Request
(CustomerID, RequiredSkillID, Priority, Channel, ReceivedAt, Status)
VALUES
(1, 2, 'High', 'Chat', NOW(), DEFAULT),
(2, 1, 'Medium', 'Call', NOW(), DEFAULT),
(3, 3, 'Low', 'Email', NOW(), DEFAULT),
(4, 2, 'High', 'Chat', NOW(), DEFAULT),
(5, 4, 'Medium', 'Call', NOW(), DEFAULT),
(6, 6, 'Low', 'Email', NOW(), DEFAULT),
(7, 7, 'High', 'Chat', NOW(), DEFAULT),
(8, 1, 'Medium', 'Call', NOW(), DEFAULT),
(9, 5, 'Low', 'Email', NOW(), DEFAULT),
(10, 3, 'High', 'Chat', NOW(), DEFAULT);

INSERT INTO RoutingDecision
(RequestID, AgentID, RoutedAt, WaitSeconds)
VALUES
(1, 1, NOW(), 30),
(2, 3, NOW(), 45),
(3, 2, NOW(), 60),
(4, 1, NOW(), 25),
(5, 4, NOW(), 50),
(6, 5, NOW(), 35),
(7, 6, NOW(), 40),
(8, 3, NOW(), 55),
(9, 8, NOW(), 20),
(10, 2, NOW(), 65);


-- 4. ADDITIONAL 190 REQUESTS
-- Together with the 10 initial requests, Request will contain 200 rows.
-- This recursive CTE is supported by MySQL 8.0+.

INSERT INTO Request
(CustomerID, RequiredSkillID, Priority, Channel, ReceivedAt, Status)
WITH RECURSIVE numbers AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1
    FROM numbers
    WHERE n < 190
)
SELECT
    MOD(n - 1, 10) + 1,
    MOD(n - 1, 10) + 1,
    CASE MOD(n, 3)
        WHEN 0 THEN 'High'
        WHEN 1 THEN 'Medium'
        ELSE 'Low'
    END,
    CASE MOD(n, 3)
        WHEN 0 THEN 'Call'
        WHEN 1 THEN 'Chat'
        ELSE 'Email'
    END,
    NOW(),
    'Queued'
FROM numbers;


-- 5. FINAL VERIFICATION
SELECT COUNT(*) AS AgentCount FROM Agent;
SELECT COUNT(*) AS SkillCount FROM Skill;
SELECT COUNT(*) AS AgentSkillCount FROM AgentSkill;
SELECT COUNT(*) AS CustomerCount FROM Customer;
SELECT COUNT(*) AS RequestCount FROM Request; -- Expected: 200
SELECT COUNT(*) AS RoutingDecisionCount FROM RoutingDecision;

-- Verify every Request refers to an existing Customer and Skill.
SELECT COUNT(*) AS ValidRequestCount
FROM Request R
INNER JOIN Customer C ON R.CustomerID = C.CustomerID
INNER JOIN Skill S ON R.RequiredSkillID = S.SkillID;
-- Expected: 200
