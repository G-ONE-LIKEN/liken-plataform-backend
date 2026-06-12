-- V5: Drop tables que ya migraron a otros bounded contexts.
--
-- ✅ `projects` migro a project-service (project_db.projects).
-- ✅ `wallets`  migro a wallet-service  (wallet_db.wallets).
--
-- CASCADE: dropea automaticamente las FK constraints que apuntan a estas tablas
-- desde `investments` y `user_projects`. Esas tablas se mantienen (sin reemplazo
-- todavia), solo se eliminan los foreign keys huérfanos.
--
-- Cuando se implemente invest-dividend-service en su propia DB (invest_db), las
-- tablas `investments` y `user_projects` se podran eliminar también en una V7.

DROP TABLE IF EXISTS wallets CASCADE;
DROP TABLE IF EXISTS projects CASCADE;

-- TODO (cuando invest-dividend-service esté implementado):
-- DROP TABLE IF EXISTS investments;
-- DROP TABLE IF EXISTS user_projects;
