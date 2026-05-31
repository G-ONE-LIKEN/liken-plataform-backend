-- V5: Drop tables que ya migraron a otros bounded contexts.
--
-- ✅ `projects` migró a project-service (project_db.projects).
-- ✅ `wallets`  migró a wallet-service  (wallet_db.wallets).
--
-- CASCADE: dropea automáticamente las FK constraints que apuntan a estas tablas
-- desde `investments` y `user_projects`. Esas tablas se mantienen (sin reemplazo
-- todavía), solo se eliminan los foreign keys huérfanos.
--
-- Cuando se implemente invest-dividend-service en su propia DB (invest_db), las
-- tablas `investments` y `user_projects` se podrán eliminar también en una V7.

DROP TABLE IF EXISTS wallets CASCADE;
DROP TABLE IF EXISTS projects CASCADE;

-- TODO (cuando invest-dividend-service esté implementado):
-- DROP TABLE IF EXISTS investments;
-- DROP TABLE IF EXISTS user_projects;
