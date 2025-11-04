-- 1. Flyway 히스토리 테이블 확인
SELECT * FROM flyway_schema_history ORDER BY installed_rank;

-- 2. 현재 존재하는 테이블 목록
SELECT table_name FROM information_schema.tables WHERE table_schema = 'public';

-- 3. subscribers 테이블 존재 여부
SELECT EXISTS (
  SELECT FROM information_schema.tables 
  WHERE table_schema = 'public' 
  AND table_name = 'subscribers'
) AS subscribers_exists;
