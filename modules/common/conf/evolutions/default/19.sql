# --- Insert admin user

# --- !Ups

INSERT IGNORE INTO Worker (workerType, id, mtWorkerId, comment)
    VALUES ('Jatos', 1, NULL, NULL);
INSERT IGNORE INTO User (username, name, passwordHash, worker_id, authMethod, lastLogin, active, email, lastSeen)
    VALUES ('admin', 'Admin',  '21232f297a57a5a743894a0e4a801fc3', 1, 'DB', NULL, 1, NULL, NULL);
INSERT IGNORE INTO User_roleList (User_username, roleList)
    VALUES ('admin', 'USER'), ('admin', 'ADMIN');

# --- !Downs
# --- not supported
