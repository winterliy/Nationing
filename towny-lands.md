# Towny Advanced와 Lands의 국가 플러그인 핵심 로직

## 1. 데이터 모델·영토·권한 계층

### Takeaway

Towny는 `Resident → Town → Nation`이라는 명시적 계층과 격자형 `TownBlock` 보호를 결합한다. Lands는 `Player → Land → Nation`에 가까운 모델이며, Land 내부를 다시 Area로 나누고 역할별 flag를 적용한다. 국가 플러그인 설계에서는 “영토 소유권(누가 소유하는가)”과 “행위 권한(무엇을 할 수 있는가)”을 별도 축으로 저장하는 것이 두 제품의 공통 핵심이다.

### Cited Findings

- Towny 공식 위키는 Nomad, Resident, Town, Nation을 단계적 계층으로 설명한다. Town은 Resident들의 집합이며 Mayor, assistants, bank, tax를 갖고, Nation은 Town들의 상위 정치 단위다. [Towny How Towny Works](https://github-wiki-see.page/m/TownyAdvanced/Towny/wiki/How-Towny-Works)
- Towny 공식 저장소의 설명과 `plugin.yml`은 Resident–Town–Nation 계층, grid-based protection, 확장 API를 핵심 기능으로 명시한다. [Towny plugin.yml](https://github.com/TownyAdvanced/Towny/blob/master/Towny/src/main/resources/plugin.yml), [Towny repository](https://github.com/TownyAdvanced/Towny)
- Towny의 보호 단위는 세계별 격자 plot/townblock이다. 일반 Town claim은 기존 TownBlock에 인접해야 하고, wilderness outpost는 별도 규칙으로 허용된다. [How Towny Works](https://github-wiki-see.page/m/TownyAdvanced/Towny/wiki/How-Towny-Works)
- Towny는 개인 소유 plot과 Town 소유(비소유) plot을 구분한다. 개인 소유 plot은 Resident 기본 권한을, Town 소유 plot은 Town 기본 권한을 상속하며 각 plot에 override를 둘 수 있다. [Towny plot permissions](https://github-wiki-see.page/m/TownyAdvanced/Towny/wiki/How-Towny-Works)
- Towny의 권한 그룹은 Friend/Resident, Town/Nation, Ally, Outsider의 4개이고, 권한 종류는 build, destroy, switch, itemuse 등으로 구성된다. Town plot에서는 Residents/Nation/Ally/Outsider로 표시되고, 개인 plot에서는 Friend/Town/Ally/Outsider로 표시된다. [Towny plot permissions](https://github-wiki-see.page/m/TownyAdvanced/Towny/wiki/How-Towny-Works)
- Towny는 Bukkit 권한 노드를 직접 부여하는 TownyPerms 체계를 사용하며, Nomad/Resident/Mayor/King 같은 자동 역할과 Assistant/VIP 등 Town·Nation 보조 역할을 조합한다. 보조 역할은 여러 개를 동시에 가질 수 있다. [Towny roles and Townyperms](https://github-wiki-see.page/m/TownyAdvanced/Towny/wiki/How-Towny-Works)
- Towny 소스 트리는 `object`, `event`, `permissions`, `db`, `listeners`, `tasks`, `regen` 패키지를 별도로 둔다. 이는 도메인 객체, 이벤트, 권한 판정, 저장, Bukkit 이벤트 훅, 주기 작업, 복구/재생성 로직이 분리된 구조임을 소스에서 관찰할 수 있게 한다. [Towny Java package tree](https://github.com/TownyAdvanced/Towny/tree/master/Towny/src/main/java/com/palmergames/bukkit/towny)
- Lands 공식 문서 목차는 Claims and Worlds, Economy and Progression, Roles and Role Flags, Natural Flags, Wars, Permissions, API, Events, Database를 별도 개념으로 다룬다. [Lands official documentation index](https://wiki.incredibleplugins.com/lands)
- Lands의 공개 플레이어 문서(서버 가이드)는 Chunk를 16×16 열(column)로, Land를 claimed chunks의 집합으로, Area를 Land의 하위 구역으로, Roles를 플레이어별 권한 묶음으로, Flags를 행위/환경 설정으로 정의한다. [Clovercraft Lands guide](https://wiki.clovercraft.gg/en/player-guide/land-claims)
- Lands의 역할 모델은 Owner/Admin/Member/Ally·Nation/Untrusted처럼 기본 역할을 제공하고, 역할마다 block break, chest, door, redstone, trust, set-role 등 행동 flag를 설정한다. [Wick SMP Lands guide](https://www.wicksmp.com/lands.html), [PureVanilla Lands guide](https://wiki.purevanilla.co/gamemodes/chill/lands/)
- Lands는 claim 단위를 기본적으로 Minecraft chunk로 취급하며, 공식 문서 색인상 세계별 claim rule, height, camps, sub-area, claim/unclaim, wilderness regeneration을 별도 설정 대상으로 둔다. [Lands official documentation index](https://wiki.incredibleplugins.com/lands)

### Inferences

- Towny는 “정치 계층이 권한의 기본값을 내려보내고 plot이 최종 override”하는 상속 모델이다. Lands는 “Land/Area × Role × Flag”의 행렬 모델에 더 가깝다. 국가 플러그인은 `territory.owner`, `territory.area`, `role`, `flag`를 분리하면 두 방식 모두 수용할 수 있다.
- Towny의 인접 claim 강제는 영토가 덩어리로 성장하게 하는 반면, Lands의 Area와 설정 가능한 claim 규칙은 도시 내부 용도 분할에 유리하다. 이는 문서 구조에서 도출한 설계 추론이며, 모든 Lands 서버의 실제 설정이 동일하다는 뜻은 아니다.

### Gaps

- Lands의 상용/비공개 구현 저장소는 확인하지 못했다. 따라서 내부 클래스, DB schema, 동시성 모델, 실제 판정 순서는 공식 문서·API 표면만으로는 확정할 수 없다.
- Towny의 최신 버전별 구체적 YAML schema와 저장 flush 타이밍은 문서만으로 전부 확정하지 않았다.

## 2. 외교·전쟁·경제·이벤트 흐름

### Takeaway

두 플러그인은 영토를 단순 보호막이 아니라 지속 비용과 정치적 관계를 가진 상태 머신으로 만든다. Towny는 Town/Nation bank, resident/town/nation tax·upkeep, alliance/enemy와 war 시 권한 예외를 제공하고, Lands는 Land/Nation 경제, taxes/upkeep, relation/war, capture/shield/reward와 이벤트 기반 자동화를 문서화한다.

### Cited Findings

- Towny Town은 bank와 tax를 가지며, 세금은 일일 interval 종료 시 징수될 수 있다. Nation도 별도 bank·town tax/upkeep 관련 명령과 설정을 가진다. [Towny How Towny Works](https://github-wiki-see.page/m/TownyAdvanced/Towny/wiki/How-Towny-Works), [Towny commands](https://github-wiki-see.page/m/TownyAdvanced/Towny/wiki/Towny-Commands)
- Towny는 economy를 끄면 taxes/upkeep, claim·town/nation creation 비용도 발생하지 않도록 설정할 수 있다. [Towny How Towny Works](https://github-wiki-see.page/m/TownyAdvanced/Towny/wiki/How-Towny-Works)
- Towny 공식 위키는 Nation이 war event에 참여하고 다른 Nation과 ally가 될 수 있다고 설명한다. [Towny How Towny Works](https://github-wiki-see.page/m/TownyAdvanced/Towny/wiki/How-Towny-Works)
- Towny 공식 홈은 전쟁 중 block permissions가 무시되고, Nation에 속한 Town이 다른 Nation 주민에게 완전히 비보호 상태가 된다고 설명한다. [Towny wiki home](https://github-wiki-see.page/m/townyadvanced/towny/wiki)
- Towny 공개 changelog/discussions에는 nation alliance invite의 상호 수락, neutral/peaceful 비용, town/nation upkeep, overclaimed upkeep penalty, reclaim/delete 기간, pre/post resident removal event, PreNewTownEvent가 기록돼 있다. [Towny 0.97.5.3 discussion](https://github.com/TownyAdvanced/Towny/discussions/5553), [Towny 0.98.3.13 discussion](https://github.com/TownyAdvanced/Towny/discussions/6229), [Towny 0.98.0 discussion](https://github.com/TownyAdvanced/Towny/discussions/5754)
- Towny 소스/문서에는 TownyAPI와 Cancellable event 계열이 존재하며, `ChunkNotificationEvent`처럼 다른 플러그인이 알림을 변경할 수 있는 API도 공개되어 있다. [TownyAPI wiki](https://github-wiki-see.page/m/TownyAdvanced/Towny/wiki/TownyAPI), [Towny source package tree](https://github.com/TownyAdvanced/Towny/tree/master/Towny/src/main/java/com/palmergames/bukkit/towny)
- Lands 공식 문서 색인은 economy 페이지에 banks, claim costs, taxes, upkeep, rent, nation economy를, wars 페이지에 war configuration, capture flags, shields, rewards를 명시한다. [Lands official documentation index](https://wiki.incredibleplugins.com/lands)
- Lands 공식 이벤트 문서는 플러그인 API 이벤트에 조건부 명령을 연결할 수 있고, `LandOwnerChangeEvent`가 Land 또는 Area에 대해 호출될 수 있으며, 이벤트를 cancel 조건으로 취소할 수 있다고 설명한다. [Lands Events](https://wiki.incredibleplugins.com/general/locale-and-config/events)
- Lands의 공식 문서 색인은 Database에 SQLite/MySQL/Redis synchronization과 migration/backups를 포함한다고 명시한다. 이는 저장소 선택과 다중 서버 동기화가 지원 범위라는 관찰이다. [Lands official documentation index](https://wiki.incredibleplugins.com/lands)
- 공개 서버 가이드들은 Lands의 Land bank가 upkeep을 지급하고, member tax가 Land bank로 들어가며, 미납 시 member 제거 또는 최근 claim unclaim 같은 결과가 설정될 수 있다고 설명한다. 이 수치와 정확한 실패 동작은 서버별 설정 예시이므로 제품 기본값으로 일반화하면 안 된다. [Wick SMP guide](https://www.wicksmp.com/lands.html), [Mine-Play settings](https://wiki.mine-play.cloud/Smp/Lands/Server%20Settings)

### Inferences

- 양쪽의 경제 루프는 `claim 증가 → recurring upkeep 증가 → bank/tax로 충당 → 미납 시 영토/멤버십 손실`이라는 안정화 피드백이다. 국가 플러그인에서 claim만 무제한으로 주면 영토 인플레이션과 휴면 국가가 발생하므로 이 루프가 필요하다.
- Lands 이벤트의 조건부 command binding은 “도메인 이벤트 → 정책 조건 → 부가 효과” 파이프라인이다. 국가 플러그인에서는 이를 감사 로그, 외교 통지, 전쟁 점수, 세금 고지에 재사용할 수 있다.
- 전쟁에서 Towny가 보호 권한을 광범위하게 무시하는 방식은 전쟁을 실제 상태 전환으로 만들지만, 전쟁 선언·동맹·중립·combat logging·스폰 공격 방지의 경계가 별도 설계되지 않으면 악용 가능성이 커진다.

### Gaps

- Lands 전쟁의 실제 점수 계산, capture tick, shield 조건, 승패 정산 순서는 공식 색인만으로는 확인하지 못했다.
- Towny의 현재 전쟁 동작은 FlagWar 등 별도 애드온/버전 설정과 결합될 수 있으므로, “war가 정확히 무엇을 점령하는가”는 설치 조합별 검증이 필요하다.

## 3. 영속성·이벤트 경계·악용 가능한 설계 약점

### Takeaway

Towny는 공개 코드에서 데이터·이벤트·권한·작업을 분리하고 세계별 파일을 사용하는 전통적 플러그인 구조를 보여준다. Lands는 문서상 SQLite/MySQL/Redis와 API/events.yml을 제공하지만 내부 저장 구현은 공개되지 않는다. 설계 약점은 주로 권한 상속의 복잡성, 경제 원자성, 전쟁 중 보호 예외, 청구·해제·소유권 변경의 경계 조건에서 발생한다.

### Cited Findings

- Towny 문서는 세계별 데이터 파일이 `plugins/towny/data/worlds/worldname.txt`에 있고 세계 목록은 `data/worlds.txt`에 있다고 설명한다. [Towny How Towny Works](https://github-wiki-see.page/m/TownyAdvanced/Towny/wiki/How-Towny-Works)
- Towny 소스에는 `db` 패키지와 `TownyDataSource` 계열이 존재하고, API/Universe가 데이터 접근의 표면으로 분리돼 있다. [Towny Java package tree](https://github.com/TownyAdvanced/Towny/tree/master/Towny/src/main/java/com/palmergames/bukkit/towny), [TownyAPI](https://github.com/TownyAdvanced/Towny/blob/master/Towny/src/main/java/com/palmergames/bukkit/towny/TownyAPI.java)
- Towny 위키는 Plot permissions를 Resident/Town 기본값과 plot별 custom line으로 나눠 설명한다. [Towny plot permissions](https://github-wiki-see.page/m/TownyAdvanced/Towny/wiki/How-Towny-Works)
- Towny 명령 문서에는 NPC resident가 taxes/upkeep에서 면제되는 예외가 있다. [Towny Commands](https://github-wiki-see.page/m/TownyAdvanced/Towny/wiki/Towny-Commands)
- Towny plugin.yml은 nation/town/resident/plot 명령을 세분화된 Bukkit permission node로 노출하고 wildcard 및 child permission을 정의한다. [Towny plugin.yml](https://github.com/TownyAdvanced/Towny/blob/master/Towny/src/main/resources/plugin.yml)
- Lands 공식 이벤트 문서는 이벤트 조건식에서 null 변수, cancellation 상태, player group별 command 실행을 별도로 다뤄야 함을 명시한다. [Lands Events](https://wiki.incredibleplugins.com/general/locale-and-config/events)
- Lands 공식 문서 색인은 expiration/cleanup, unclaim/wilderness regeneration, database migration/backups를 독립 운영 기능으로 분류한다. [Lands official documentation index](https://wiki.incredibleplugins.com/lands)

### Inferences

- 권한 악용 1: Towny처럼 Friend/Town/Nation/Ally/Outsider, plot override, Townyperms, Bukkit permission이 겹치면 “더 넓은 권한이 좁은 plot override를 덮는가?” 같은 우선순위 버그가 생길 수 있다. 구현 시 최종 판정 함수를 단일화하고, 모든 결과에 판정 근거(주체·role·territory·flag·war state)를 기록해야 한다.
- 권한 악용 2: Lands처럼 Admin 역할에 trust, set-role, claim/unclaim, withdraw를 동시에 주면 내부자 한 명이 영토·금고·권한을 모두 탈취할 수 있다. 운영 기본값에서는 재무 인출, 역할 부여, 영토 변경을 분리된 capability로 두는 편이 안전하다.
- 청구 경계 악용: claim과 unclaim, 소유권 이전, Area 분할, 국경 인접성 검사를 서로 다른 이벤트에서 처리하면 “검사 후 상태 변경” 사이 race/재진입 문제가 생길 수 있다. claim transaction을 좌표 lock 또는 단일 메인 스레드 command로 원자화해야 한다.
- 경제 악용: 세금 징수와 upkeep 차감이 각각 성공한 뒤 후속 해제/추방이 실패하면 돈과 영토 상태가 불일치할 수 있다. debit, 결과 상태, audit event를 idempotent transaction으로 묶고 재시도 키를 둬야 한다.
- 전쟁 악용: Towny의 전쟁 중 광범위한 보호 해제는 전쟁 참여자가 아닌 제3자, offline player, spawn/상자/레드스톤 등까지 영향을 줄 위험이 있다. 전쟁 예외는 “공격자·방어자·대상 flag·전쟁 구역·보호 유예·combat tag”를 명시적으로 계산해야 한다.
- 영속성 악용/장애: 파일 기반 Towny는 수동 편집·부분 저장·백업 복원 중 참조 무결성 문제가 생길 수 있고, Lands의 다중 저장소/Redis 동기화는 stale write와 split-brain을 고려해야 한다. 이는 각각 공개된 저장 방식에서 도출한 설계 위험 추론이며, 실제 취약점 확인이 아니다.
- 이벤트 악용: Lands의 events.yml처럼 이벤트에서 console/player 명령을 실행할 수 있으면 악성 또는 실수한 조건식이 권한 상승·무한 반복·대량 명령을 만들 수 있다. 명령 allowlist, recursion guard, 실행량 제한, cancellation 전후 검증이 필요하다.
- 국가 플러그인 권장 최소 상태 모델: `Player(uuid, memberships)`, `Land(id, owner, bank, level)`, `Territory(world, chunkX, chunkZ, landId)`, `Area(id, landId, bounds)`, `Role(id, scope, priority, flags)`, `Nation(id, capital, members)`, `Relation(type, source, target)`, `War(state, sides, phases, score, shield)`, `LedgerEntry`, `DomainEvent`로 분해한다. 이는 Towny/Lands의 관찰된 개념을 일반화한 설계 제안이다.

### Gaps

- Lands의 실제 공개 소스 저장소는 찾지 못했으므로 DB table, cache invalidation, lock, event dispatch 순서를 사실로 기술할 수 없다. 공식 문서/API와 서버별 설정 가이드 이상으로는 reverse engineering이 필요하다.
- Towny 저장 파일의 정확한 직렬화 포맷, flush 주기, crash recovery, 동시 접속 중 save 정책은 이 조사 범위에서 코드 라인 단위로 검증하지 않았다.
- 보고서에 “실제 취약점”이라고 단정할 만한 CVE/재현 가능한 exploit은 확인하지 못했다. 위 약점은 소스·문서 구조에서 도출한 threat model이며, 별도 테스트 서버에서 재현해야 한다.
