ALTER TABLE proj_contract_price ADD COLUMN IF NOT EXISTS min_quantity numeric(12,4);
ALTER TABLE proj_workload      ADD COLUMN IF NOT EXISTS min_quantity_source varchar(20);

UPDATE proj_workload SET min_quantity_source='dict'
WHERE del_flag='0' AND min_quantity IS NOT NULL AND min_quantity_source IS NULL;
