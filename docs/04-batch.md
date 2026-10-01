# 04. 배치 잡 설계

매입 파일 적재 → 대사 → 정산 → 지급 파일 생성. 테이블은 [01-erd.md](01-erd.md), 파일 레이아웃은 [02-message-spec.md](02-message-spec.md) 5~7장.

## 한눈에

```
dailySettlementJob (businessDate = B)          매일 새벽, 매입 파일 도착 후
  ① validateAcquisitionFile   tasklet   파일 전체 검증 → acquisition_file LOADING / REJECTED
  ② loadAcquisitionRecords    chunk     파일 → acquisition_record
  ③ completeAcquisitionFile   tasklet   DB 건수·합계·중복 검증 → LOADED / REJECTED
  ④ reconcile                 tasklet   승인 vs 매입 → recon_result
  ⑤ buildSettlements          tasklet   MATCHED만 묶어서 정산 → settlement
  ⑥ allocateFees              chunk     정산별 거래 수수료 배분 → settlement_item

payoutFileJob (payoutDate = P)                  지급일 새벽
  ① writePayoutFile           tasklet   settlement → PAY_{P}.dat
```

기호: **B** = 매입일(파일 기준일), **D** = B − 1일(승인일), **P** = 지급일.

## 1. 잡을 두 개로 나눈 이유

- ①~⑥은 같은 매입일에 대해 순서가 고정이고, 앞 단계 없이는 뒤 단계가 의미 없다. 한 잡으로 묶으면 **실패한 step부터 재시작**하는 걸 Spring Batch가 해 준다.
- 지급은 기준 날짜가 다르다. 주말·휴일 때문에 여러 매입일의 정산이 같은 지급일에 모인다. 예를 들어 목·금·토 매입분이 모두 다음 주 화요일에 지급될 수 있다.
- 대안은 step마다 잡을 따로 두고 외부 스케줄러가 순서를 관리하는 방식이다. 금융권 실무는 보통 이렇게 외부 스케줄러로 잡을 엮는다. v1은 명령행으로 실행한다.

## 2. 공통 규칙

| 규칙 | 이유 |
|---|---|
| 잡 파라미터는 날짜 하나 (`LocalDate`, identifying) | 같은 날짜 = 같은 JobInstance. 실패하면 같은 파라미터로 다시 실행해서 실패한 step부터 재시작한다. |
| **skip과 retry를 쓰지 않는다** | 레코드를 건너뛰면 그만큼 돈이 빠진다. 멈추고, 고치고, 재시작한다. |
| tasklet은 "그 날짜 결과를 지우고 다시 만들기"를 한 트랜잭션으로 | 재시작해도 결과가 같다 (멱등). |
| chunk step은 읽은 위치를 `ExecutionContext`에 저장 | 재시작할 때 이미 커밋된 chunk를 다시 처리하지 않는다. |
| 날짜 조건은 범위로: `approved_at >= :d AND approved_at < :d + 1` | `TRUNC(approved_at) = :d`는 컬럼에 함수를 씌워 인덱스를 못 쓴다. |
| `approved_at`, `cancelled_at`은 **카드사 응답 전문의 승인일시** | 시뮬레이터의 매입 기준과 같은 시계를 써야 자정 근처 거래가 어긋나지 않는다. |

- COMPLETED된 날짜는 같은 파라미터로 다시 실행할 수 없다. 개발 중에는 DB를 초기화하고 다시 돌린다. 운영 재처리 절차는 완성선 이후.
- Spring Batch 메타데이터 테이블(`BATCH_*`)은 Flyway로 관리한다 ([01-erd.md](01-erd.md)).

## 3. 매입 적재 (①~③)

### ① validateAcquisitionFile

파일을 처음부터 끝까지 한 번 읽으며 검사한다. 건수와 합계만 누적하므로 메모리는 파일 크기와 상관없이 일정하다.

| 검사 | 실패 시 |
|---|---|
| 파일이 있고 이름이 `ACQ_{카드사코드}_{B}.dat` | 잡 실패 (파일 도착 대기는 스케줄러 몫) |
| 같은 파일명이 이미 `LOADED` | 잡 실패 (`file_name` UK로 중복 적재 방지) |
| 레코드 길이 150바이트 + CRLF, 순서 H → D* → T | `REJECTED` |
| 헤더의 매입일 = B, 카드사코드 = 파일명 | `REJECTED` |
| `N` 필드가 숫자, 매입구분이 `01`/`02` | `REJECTED` |
| 트레일러 건수·합계 = 데이터 레코드 실제 값 (I4) | `REJECTED` |

- `REJECTED`는 **별도 트랜잭션으로 커밋**한 뒤 잡을 실패시킨다. 잡이 실패해도 거부 기록은 남아야 한다.
- **적재 전에 따로 한 번 읽는 이유**: I4는 파일 전체를 거부하라는 규칙이다. 적재하면서 검증하면, 중간에 불일치를 발견했을 때 이미 커밋된 chunk를 지워야 한다. 150MB를 한 번 더 순차로 읽는 비용은 1초 안팎이다.

### ② loadAcquisitionRecords (chunk)

- **`FlatFileItemReader`를 그대로 쓰지 않는다.** 줄을 문자열로 디코딩한 뒤 자르면 문자 기준이 되어 한글 필드 뒤의 오프셋이 밀린다. `codec` 모듈로 바이트를 잘라 읽는 `ItemStreamReader`를 직접 만든다.
  - 대안: ISO-8859-1로 읽으면 1바이트가 1문자라서 `substring`이 바이트 기준이 된다. 자른 필드를 다시 바이트로 바꿔 MS949로 디코딩하면 동작한다. 다만 의도가 코드에 드러나지 않는다.
- **재시작 위치는 계산으로 찾는다.** 고정길이라 커밋된 데이터 레코드 수 k만 `ExecutionContext`에 저장하면, 재시작 오프셋은 152 × (1 + k)바이트다 (레코드 150 + CRLF 2, 앞에 헤더 1개). 처음부터 다시 읽을 필요가 없다. 마지막 레코드 뒤에는 CRLF가 없을 수 있다.
- writer는 MyBatis `BATCH` executor로 JDBC 배치 INSERT를 한다.
- `record_seq`(파일 안 순번)를 함께 넣는다. 재시작 로직에 버그가 있어도 `UK(file_id, record_seq)`가 중복을 막는다.
- chunk 크기는 1,000으로 시작하고 Phase 4에서 측정한다.

### ③ completeAcquisitionFile

- DB에 들어간 건수·합계가 트레일러와 같은지 SQL로 확인한다. 적재 버그(누락, 중복)를 잡는다.
- 같은 파일 안에 같은 `(txn_id, acq_type)`이 두 번 있으면 `REJECTED`. 중복 매입이 대사에서 둘 다 `MATCHED`가 되면 이중 정산된다.
- 통과하면 `LOADED`. **대사는 `LOADED` 파일만 본다.** 적재 중(`LOADING`)인 데이터는 다음 단계에 보이지 않는다.

## 4. 대사 (④)

### 기대 집합: D일 24:00 시점

지금 상태가 아니라 **D일 24:00 시점의 상태**로 "무엇이 와야 하는지"를 정한다 ([00-domain.md](00-domain.md) 5장). 시뮬레이터의 매입 기준([02-message-spec.md](02-message-spec.md) 4장)과 같은 규칙이다.

| 집합 | 조건 |
|---|---|
| **E01** 매입이 와야 하는 결제 | `approved_at` ∈ [D, B) 이고, `cancelled_at < B`인 `CANCELLED` 취소가 없다 |
| **E02** 매입취소가 와야 하는 취소 | `CANCELLED` 취소이고 `cancelled_at` ∈ [D, B), 원결제의 `approved_at < D` |

D일에 승인되고 B일 새벽에 취소된 결제는 E01에 들어간다. 지금은 `CANCELLED`지만 D일 24:00에는 승인 상태였기 때문이다. 이 취소는 B+1 파일에 매입취소로 온다.

### 분류: INSERT … SELECT 4개

| # | 출발 | 짝 찾기 | 결과 |
|---|---|---|---|
| A | 파일의 `01` 레코드 | `txn_id`로 `payment` | 결제 없음 → `CAPTURE_ONLY` · E01이고 금액 같음 → `MATCHED` · E01이고 금액 다름 → `AMOUNT_MISMATCH` · 그 외 → `STATUS_MISMATCH` |
| B | E01 | 같은 `txn_id`의 `01` 레코드 | 없음 → `UNCAPTURED` |
| C | 파일의 `02` 레코드 | `txn_id`로 `payment` → `CANCELLED` 취소 | 결제 없음 → `CAPTURE_ONLY` · E02이고 금액 같음 → `MATCHED` · E02이고 금액 다름 → `AMOUNT_MISMATCH` · 그 외 → `STATUS_MISMATCH` |
| D | E02 | 같은 `txn_id`의 `02` 레코드 | 없음 → `UNCAPTURED` |

- A의 "그 외"에 들어가는 결제: `DECLINED`, `NET_CANCELLED`, `UNKNOWN`, 서버 중단으로 남은 `REQUESTED`, D일 안에 취소된 결제, D일이 아닌 날 승인된 결제(늦은 매입. v1 시뮬레이터에서는 생기지 않는다).
- `UNKNOWN`·`REQUESTED`인데 매입이 오지 않으면 결과 행을 만들지 않는다. 결제 상태는 그대로 두고, 상태를 바꾸는 건 운영 판단이다 (완성선 이후).
- `recon_result.acq_type`으로 매입(`01`) 대사인지 매입취소(`02`) 대사인지 구분한다. B·D의 `UNCAPTURED`는 매입 레코드가 없어서 이 컬럼이 필요하다.

**한 트랜잭션으로 처리한다.** `DELETE recon_result WHERE business_date = B` → INSERT 4개 → 커밋. 기준 시점이 고정이라, 재실행 사이에 새 취소가 들어와도 결과가 바뀌지 않는다.

**chunk가 아니라 SQL 집합 연산으로 한다.** 100만 건 × 100만 건 짝짓기는 DB 안의 해시 조인이 가장 빠르다. Java로 가져와 비교하면 200만 행을 네트워크로 옮겨야 한다. Phase 3에서 `DBMS_XPLAN`으로 실행 계획을 확인한다. 기대하는 계획은 A·C가 `HASH JOIN`, B·D(`NOT EXISTS`)가 `HASH JOIN ANTI`다.

step이 끝나면 결과 유형별 건수를 로그로 남긴다.

## 5. 정산 (⑤ ⑥)

### ⑤ buildSettlements (tasklet, 한 트랜잭션)

1. B의 정산 중 이미 지급 파일에 들어간 게 있으면 실패한다. 지급된 정산은 다시 만들지 않는다.
2. **수수료율 선분이력 검증.** 가맹점별 기간 겹침(I9)이나 요율이 없는 거래가 있으면 실패한다. 겹치면 한 거래가 두 요율에 잡혀 이중 정산된다. Oracle에는 배제 제약이 없어서 여기서 쿼리로 막는다 ([ADR-0001](adr/0001-java-mybatis-oracle.md)).
3. B의 `settlement_item`, `settlement`를 지운다.
4. **`MATCHED` 매입 레코드만** (가맹점, 수수료율)로 묶는다. 수수료율은 원거래 승인일자로 찾는다 (`valid_from ≤ 승인일자 ≤ valid_to`). 매입취소도 원거래 승인일자를 쓰므로 원래 받은 수수료율로 되돌려진다.
5. 묶음마다 Java에서 계산해 INSERT한다 ([ADR-0004](adr/0004-money-calculation.md)).
   - 수수료 = (매입 합계 − 매입취소 합계) × 요율, 원 미만 `DOWN`
   - 부가세 = 수수료 × 0.1, 원 미만 `DOWN`
   - 지급일 = B + 정산주기 영업일 (주말과 `holiday` 건너뜀)

- **불일치 거래는 정산하지 않는다.** 해결될 때까지 보류된다. 대사가 정산의 문지기 역할을 한다.
- 계산을 SQL이 아니라 Java에서 하는 이유: 절사 방향과 부가세 규칙을 단위 테스트로 고정하기 쉽다. 묶음은 하루 수천 개라 성능 차이가 없다.

### ⑥ allocateFees (chunk, item = 정산 1건)

- reader: B의 `settlement`를 `settlement_id` 순으로 읽는다 (MyBatis 페이징 reader는 재시작할 때 읽던 페이지부터 다시 읽는다). **정렬이 결정적이어야** 재시작 위치가 맞다.
- processor: 그 정산의 매입 레코드를 읽어 거래별 수수료를 배분한다.
- writer: `settlement_item` 배치 INSERT.
- chunk 크기는 작게 잡는다 (예: 정산 10건). 정산 1건에 거래가 수만 건일 수 있다.

**배분 규칙: 바닥은 `FLOOR`로 잡는다**

1. 거래마다 이상값 = 금액 × 요율 (매입취소는 금액이 음수)
2. 기본값 = `FLOOR(이상값)`. **`DOWN`이 아니다.**
3. 남은 금액 R = 정산 수수료 − Σ기본값. 항상 0 ≤ R ≤ 거래 수다.
4. 소수부(이상값 − 기본값)가 큰 순서로 R개 거래에 1원씩 더한다. 동점이면 `acquisition_record_id` 오름차순.

왜 `FLOOR`인가: `FLOOR`의 소수부는 음수에서도 항상 0 이상 1 미만이라 R이 음수가 되지 않는다. `DOWN`은 음수를 0 쪽으로 올리므로 기본값 합이 정산 수수료보다 커질 수 있다.

| 거래 | 금액 | 이상값 (3.3%) | `DOWN` 기본값 | `FLOOR` 기본값 |
|---|---|---|---|---|
| 매입 | 40 | 1.32 | 1 | 1 |
| 매입취소 | −20 | −0.66 | 0 | −1 |
| 합계 | 20 | 0.66 → 정산 수수료 0 | 1 (R = −1 ✗) | 0 (R = 0 ✓) |

정산마다 Σ배분 수수료 = 정산 수수료인지 확인하고, 다르면 step을 실패시킨다 ([ADR-0004](adr/0004-money-calculation.md) 결과).

## 6. 지급 파일 (payoutFileJob)

대상은 `payout_date = P`, `net_amount > 0`, `payout_file_id IS NULL`인 정산이다. 가맹점별로 합쳐 데이터 레코드 1건을 만든다 ([02-message-spec.md](02-message-spec.md) 7장). 가맹점 1,000곳이면 1,000건이라 chunk가 필요 없다.

1. 대상을 조회해 임시 파일 `PAY_{P}.dat.tmp`에 쓴다. 계좌번호는 **이때만** 복호화한다.
2. 트레일러 합계 = 대상 정산의 `net_amount` 합계인지 확인한다 (I7).
3. 한 트랜잭션: `payout_file` INSERT, `UPDATE settlement SET payout_file_id = ? WHERE … AND payout_file_id IS NULL`. 바뀐 건수가 대상 건수와 같은지 확인한다.
4. 임시 파일을 `PAY_{P}.dat`로 이름을 바꾼다. 같은 디렉터리 안의 이름 변경은 원자적이다.

- **최종 파일명은 DB 커밋 뒤에만 생긴다.** 은행 전송 쪽은 최종 파일명만 보므로, DB에 기록되지 않은 지급 파일이 전송될 일이 없다.
- **재실행**: `payout_file`(P) 행이 이미 있으면, 그 행에 연결된 정산으로 파일을 다시 만든다. 3과 4 사이에 죽어도 DB 기준으로 같은 파일이 다시 나온다.
- 지급액이 0 이하인 정산은 제외하고 남겨 둔다. 상계는 완성선 이후.

## 7. 인덱스 (Flyway V1에 포함)

| 인덱스 | 쓰는 곳 |
|---|---|
| `payment(approved_at)` | 대사 E01 |
| `payment_cancel(cancelled_at)` | 대사 E02 |
| `payment_cancel(payment_id)` | FK, 취소 조회 |
| `acquisition_record(file_id, acq_type)` | 대사 A·C, 적재 검증 |
| `recon_result(business_date, result_type)` | 재실행 삭제, 정산 ⑤에서 `MATCHED` 찾기 |
| `settlement(business_date)`, `settlement(payout_date)` | 정산 재실행 삭제, 지급 |
| `settlement_item(settlement_id)` | FK, 배분 검증 |

- **Oracle은 FK에 인덱스를 자동으로 만들지 않는다.** 자식 테이블의 FK 컬럼에 인덱스가 없으면, 부모 행을 지우거나 키를 바꿀 때 자식 테이블 전체에 잠금이 걸린다. 자식 FK 컬럼에는 인덱스를 둔다.
- `acquisition_record(txn_id)`에는 인덱스를 두지 않고 시작한다. 대사는 파일 하나를 통째로 해시 조인하므로 인덱스를 쓰지 않을 가능성이 높다. Phase 3에서 실행 계획을 보고 정한다.

## 8. Phase 4 측정 계획

| 대상 | 바꾸는 것 | 기록할 값 |
|---|---|---|
| ② 적재 | chunk 100 / 500 / 1,000 / 5,000 / 10,000 | 총 시간, 초당 건수 |
| ② 적재 | MyBatis `SIMPLE` vs `BATCH` executor | 총 시간 |
| ④ 대사 | — | 실행 계획, 시간, temp 사용량 |

- 조건: 합성 데이터 100만 건, Oracle Free (CPU 2스레드, 메모리 2GB), MacBook M1 Max, Docker 자원 설정. 수치와 조건은 `docs/`에 남긴다.
- 목표: 새벽 배치 2시간 안 → 초당 139건 이상 ([00-domain.md](00-domain.md) 7장).

## 알려진 한계 (v1)

| 한계 | 결과 | 정리 방법 |
|---|---|---|
| 늦은 매입 (D일 이전 승인 건이 B 파일에 옴) | `STATUS_MISMATCH`로 잡힌다 | 대기 기간 처리는 완성선 이후 |
| 불일치 거래 | 정산이 보류된 채 남는다 | 처리 화면은 완성선 이후 |
| 완료된 날짜 재처리 | 같은 파라미터로 다시 실행할 수 없다 | 재처리 절차는 완성선 이후 |
| 카드사 1곳 | 매입 파일이 하루 1개 | 여러 카드사는 완성선 이후 |
