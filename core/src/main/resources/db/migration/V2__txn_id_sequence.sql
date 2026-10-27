-- V2: 거래고유번호 일련번호 (docs/02-message-spec.md 2장: P + 날짜 8자리 + 일련번호 11자리)
-- 승인과 취소가 같은 시퀀스를 쓴다. 하루마다 초기화하지 않는다 (11자리면 하루 100만 건으로 270년 넘게 쓴다).
-- CACHE: 번호를 미리 메모리에 받아 둔다. DB가 재시작되면 캐시에 남은 번호는 건너뛴다 (번호에 빈칸이 생길 수 있다).
CREATE SEQUENCE txn_id_seq START WITH 1 INCREMENT BY 1 CACHE 100 NOCYCLE;
