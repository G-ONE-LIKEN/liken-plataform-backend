ALTER TABLE processed_events
    ALTER COLUMN event_id TYPE VARCHAR(128);

UPDATE projects
SET total_tokens_sold = ROUND((raised_amount / early_bird_price)::numeric, 8)
WHERE raised_amount > 0
  AND early_bird_price > 0
  AND total_tokens_sold < ROUND((raised_amount / early_bird_price)::numeric, 8);
