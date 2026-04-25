CREATE SCHEMA IF NOT EXISTS ssdd;
USE ssdd;

CREATE TABLE IF NOT EXISTS users(
    id varchar(50) PRIMARY KEY,
    email varchar(50) UNIQUE NOT NULL,
    password_hash text NOT NULL,
    name text,
    token text
);

-- Para búsquedas con email
CREATE INDEX user_email_idx ON users (email);

CREATE TABLE IF NOT EXISTS conversations (
    dialogue_id varchar(100) PRIMARY KEY,
    user_id varchar(50),      
    status varchar(20) DEFAULT 'READY', 
    FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS dialogues (
    id int AUTO_INCREMENT PRIMARY KEY,       
    dialogue_id varchar(100),    
    prompt text,                 
    response text,           
    timestamp bigint,            
    FOREIGN KEY(dialogue_id) REFERENCES conversations(dialogue_id) ON DELETE CASCADE
);

-- ----------------------------
-- Usuario inicial 
-- email: admin@um.es
-- pass : 2004
-- ----------------------------
INSERT INTO users (id, email, password_hash, name, token)
VALUES (
  'e5074a56539948a6f6ead15f6cb177f5',
  'admin@um.es',
  'b8b4b727d6f5d1b61fff7be687f7970f',
  'Administrador',
  NULL
)
ON DUPLICATE KEY UPDATE email = email;
