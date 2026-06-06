ALTER TABLE projects
    ADD COLUMN total_tokens_sold NUMERIC(20,8) NOT NULL DEFAULT 0;

UPDATE projects
SET total_tokens_sold = ROUND((raised_amount / early_bird_price)::numeric, 8)
WHERE raised_amount > 0
  AND early_bird_price > 0
  AND total_tokens_sold = 0;
