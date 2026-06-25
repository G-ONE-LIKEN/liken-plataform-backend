-- Se ejecuta una sola vez al crear el contenedor.
-- "user_db" ya la crea Postgres con POSTGRES_DB; las demas se crean aca.

CREATE DATABASE project_db;
CREATE DATABASE wallet_db;
CREATE DATABASE notification_db;
CREATE DATABASE blockchain_db;
CREATE DATABASE invest_db;
CREATE DATABASE marketplace_db;
CREATE DATABASE oracle_db;