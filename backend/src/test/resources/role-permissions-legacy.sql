-- Deliberately reproduce the old MySQL ENUM schema before Hibernate runs update.
CREATE TABLE role_permissions (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    role_name ENUM('ROLE_SALES') NOT NULL,
    permission ENUM('ORDER_VIEW') NOT NULL,
    CONSTRAINT uk_role_permission UNIQUE (role_name, permission)
);
INSERT INTO role_permissions (role_name, permission) VALUES ('ROLE_SALES', 'ORDER_VIEW');
