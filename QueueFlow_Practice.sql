-- =========================================================
-- QueueFlow_Practice.sql
-- Purpose: JOINs, filtering, grouping, functions and constraint checks.
-- Run after QueueFlow_Setup.sql.
-- Safe SELECT queries can be run repeatedly.
-- UPDATE changes data; DELETE removes data. Read warnings first.
-- =========================================================

USE QueueFlowDB;


-- 1. JOIN PRACTICE

-- 1.1 Customer details with their requests
SELECT
    C.CustomerID,
    C.FirstName,
    C.LastName,
    R.RequestID,
    R.Priority,
    R.Status
FROM Customer C
INNER JOIN Request R
    ON C.CustomerID = R.CustomerID;

-- 1.2 Requests with the required skill name
SELECT
    R.RequestID,
    R.Priority,
    S.SkillName
FROM Request R
INNER JOIN Skill S
    ON R.RequiredSkillID = S.SkillID;

-- 1.3 Agent names with their skills and proficiency
SELECT
    A.FirstName,
    S.SkillName,
    ASk.ProficiencyLevel
FROM Agent A
INNER JOIN AgentSkill ASk
    ON A.AgentID = ASk.AgentID
INNER JOIN Skill S
    ON ASk.SkillID = S.SkillID;

-- 1.4 Routing decisions with request and agent details
SELECT
    R.RequestID,
    R.Priority,
    A.FirstName AS AgentFirstName,
    A.LastName AS AgentLastName,
    RD.RoutedAt,
    RD.WaitSeconds
FROM RoutingDecision RD
INNER JOIN Request R
    ON RD.RequestID = R.RequestID
INNER JOIN Agent A
    ON RD.AgentID = A.AgentID;

-- 1.5 LEFT JOIN: show all customers, including customers without requests
SELECT
    C.CustomerID,
    C.FirstName,
    R.RequestID
FROM Customer C
LEFT JOIN Request R
    ON C.CustomerID = R.CustomerID;

-- 1.6 Find customers who do not have requests
SELECT
    C.CustomerID,
    C.FirstName,
    C.LastName
FROM Customer C
LEFT JOIN Request R
    ON C.CustomerID = R.CustomerID
WHERE R.RequestID IS NULL;

-- 1.7 Find agents who do not have any skill mapping
SELECT
    A.AgentID,
    A.FirstName,
    A.LastName
FROM Agent A
LEFT JOIN AgentSkill ASk
    ON A.AgentID = ASk.AgentID
WHERE ASk.AgentID IS NULL;


-- 2. FILTERING AND GROUPING

-- 2.1 Find High-priority requests
SELECT *
FROM Request
WHERE Priority = 'High';

-- 2.2 Count requests in each priority
SELECT
    Priority,
    COUNT(*) AS RequestCount
FROM Request
GROUP BY Priority;

-- 2.3 Find skills mapped to more than one agent
SELECT
    S.SkillName,
    COUNT(ASk.AgentID) AS AgentCount
FROM Skill S
INNER JOIN AgentSkill ASk
    ON S.SkillID = ASk.SkillID
GROUP BY S.SkillID, S.SkillName
HAVING COUNT(ASk.AgentID) > 1;

-- 2.4 Sort requests by priority: High, Medium, Low
SELECT *
FROM Request
ORDER BY
    CASE Priority
        WHEN 'High' THEN 1
        WHEN 'Medium' THEN 2
        WHEN 'Low' THEN 3
        ELSE 4
    END,
    ReceivedAt;

-- 2.5 Show the different priority values
SELECT DISTINCT Priority
FROM Request;

-- 2.6 Display the first five customers
SELECT *
FROM Customer
LIMIT 5;


-- 3. UPDATE AND DELETE PRACTICE

-- UPDATE was already executed by you for RequestID 1.
-- Check its current status:
SELECT RequestID, Status
FROM Request
WHERE RequestID = 1;

-- Example UPDATE (do not rerun unless you intentionally want this change):
-- UPDATE Request
-- SET Status = 'Assigned'
-- WHERE RequestID = 1;

-- IMPORTANT: Do not execute this DELETE as written.
-- RoutingDecision contains a foreign-key reference to RequestID 10,
-- so MySQL will normally reject deleting that parent request.
-- DELETE FROM Request
-- WHERE RequestID = 10;


-- 4. AGGREGATE AND OTHER SQL FUNCTIONS

-- 4.1 Average waiting time
SELECT AVG(WaitSeconds) AS AverageWaitSeconds
FROM RoutingDecision;

-- 4.2 Extract the date from ReceivedAt
SELECT
    RequestID,
    DATE(ReceivedAt) AS RequestDate
FROM Request;

-- 4.3 Convert customer names to uppercase
SELECT
    UPPER(CONCAT(FirstName, ' ', LastName)) AS CustomerName
FROM Customer;

-- 4.4 Find agent-skill mappings with above-average proficiency
SELECT *
FROM AgentSkill
WHERE ProficiencyLevel > (
    SELECT AVG(ProficiencyLevel)
    FROM AgentSkill
);


-- 5. CONSTRAINT VERIFICATION AND TESTING

-- 5.1 List foreign-key relationships in this database
SELECT
    TABLE_NAME,
    COLUMN_NAME,
    CONSTRAINT_NAME,
    REFERENCED_TABLE_NAME,
    REFERENCED_COLUMN_NAME
FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = 'QueueFlowDB'
  AND REFERENCED_TABLE_NAME IS NOT NULL;

-- 5.2 Check default values and nullability for selected columns
SELECT
    TABLE_NAME,
    COLUMN_NAME,
    IS_NULLABLE,
    COLUMN_DEFAULT
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = 'QueueFlowDB'
  AND (
      (TABLE_NAME = 'Agent' AND COLUMN_NAME = 'Status')
      OR (TABLE_NAME = 'Customer' AND COLUMN_NAME = 'Tier')
      OR (TABLE_NAME = 'Request' AND COLUMN_NAME = 'Status')
  );

-- 5.3 Check whether SkillName has a UNIQUE constraint
SELECT
    CONSTRAINT_NAME,
    COLUMN_NAME
FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = 'QueueFlowDB'
  AND TABLE_NAME = 'Skill'
  AND CONSTRAINT_NAME <> 'PRIMARY';

-- 5.4 Check CHECK constraints
SELECT
    CONSTRAINT_NAME,
    CHECK_CLAUSE
FROM INFORMATION_SCHEMA.CHECK_CONSTRAINTS
WHERE CONSTRAINT_SCHEMA = 'QueueFlowDB';

-- 5.5 Check total Request count
SELECT COUNT(*) AS RequestCount
FROM Request;

-- 5.6 Check that each request has a valid Customer and Skill reference
SELECT COUNT(*) AS ValidRequestCount
FROM Request R
INNER JOIN Customer C ON R.CustomerID = C.CustomerID
INNER JOIN Skill S ON R.RequiredSkillID = S.SkillID;
