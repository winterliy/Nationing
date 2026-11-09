# Factions와 SaberFactions 핵심 로직 분석

## 1. 데이터 모델: 국가·구성원·계급·영토

### Takeaway
두 계열의 기본 모델은 `Faction(팀/국가)`, `FPlayer(플레이어의 소속·개인 상태)`, `Board/FLocation(청크 좌표→Faction 매핑)`, `Relation(외교 상태)`로 분리하는 방식이다. 국가 플러그인으로 확장할 때는 이름(tag)과 변경 불가능한 내부 ID/UUID를 분리하고, 영토·구성원·관계를 각각 독립 저장하는 것이 핵심 설계 교훈이다.

### Cited Findings
- FactionsUUID는 faction을 플레이어 팀으로, claim을 16×16 청크 단위 영토로 정의한다. 영토는 `/f claim`으로 획득하며 일반적으로 faction power와 연결된다. — [FactionsUUID 소개](https://factions.support/introduction/); [정의](https://factions.support/definitions/)
- FactionsUUID의 역할 계층은 admin, coleader, moderator, normal, recruit이며 권한 컨텍스트도 역할의 “이상/이하” 비교를 지원한다. — [Permission Contexts](https://factions.support/contexts/)
- SaberFactions API 예시는 `FPlayers.getInstance().getByPlayer(...)`로 FPlayer를 얻고, `fPlayer.getRole()`로 역할을 읽으며, `FPlayers`가 Wilderness를 소속 없음으로 반환하는 구조를 보여준다. — [SaberFactions API Examples](https://github-wiki-see.page/m/SaberLLC/Saber-Factions/wiki/API-Examples)
- SaberFactions API 예시는 `FLocation`으로 위치를 청크/영토 키로 변환하고, `Board.getFactionAt`, `Board.setFactionAt`, `Board.removeAt`, `Board.unclaimAll`로 영토 인덱스를 읽고 변경한다. — [SaberFactions API Examples](https://github-wiki-see.page/m/SaberLLC/Saber-Factions/wiki/API-Examples)
- 관계의 기본 값은 neutral이고 가능한 관계는 ally, truce, enemy, neutral이다. — [FactionsUUID 정의](https://factions.support/definitions/)
- FactionsUUID 권한 문서는 `role-at-least/at-most`, `relation-single`, `relation-atleast/atmost` 셀렉터를 제공하며, 셀렉터는 이름보다 변경 불가능한 faction ID와 플레이어 UUID를 저장하도록 설명한다. — [Faction Permissions](https://factions.support/permissions/)
- SaberFactions 저장소는 `factions-shared`, `factions-plugin`, `factions-modern`, `factions-legacy`, `factions-folia`로 모듈을 나눈 공개 Maven 프로젝트다. — [SaberFactions GitHub 저장소](https://github.com/SaberLLC/Saber-Factions)
- SaberFactions 릴리스 기록은 최근 버전에서 “per faction” 데이터 시스템 개편과 FPerms 개편을 명시한다. — [SaberFactions 4.1.9 릴리스](https://github.com/SaberLLC/Saber-Factions/releases)

### Inferences
- 국가 모델은 Faction을 국가 본체, FPlayer를 시민/개인 상태, Role을 관직·계급, Board를 행정구역 색인으로 매핑하면 기존 API 사고방식을 재사용하기 쉽다.
- 관계·권한·영토를 서로 다른 도메인 객체로 두면 전쟁/동맹 정책을 블록 보호 코드와 분리할 수 있다.

### Gaps
- 현재 공식 FactionsUUID 문서만으로는 최신 구현의 정확한 파일 포맷(JSON/YAML/DB), flush 주기, crash-safe 원자성까지 확인하지 못했다.
- SaberFactions 최신 브랜치의 모든 클래스 경로와 저장 어댑터는 GitHub 웹 색인에서 완전하게 열리지 않아, 공개 API/릴리스/설정 자료 수준으로만 확인했다.

## 2. 핵심 게임 로직: claims·power/DTR·관계·전쟁·보호

### Takeaway
고전 Factions의 “청크 수 ≤ power” 규칙은 사망/활동에 따른 점유 가능 면적을 조정하고, DTR 변형은 “사망 허용량”과 raidability를 중심으로 운영한다. 보호는 단순한 건축 금지가 아니라 관계·역할·권한·raid 상태를 이벤트별로 평가하는 정책 엔진이어야 한다.

### Cited Findings
- FactionsUUID 소개는 faction이 보유한 power가 현재 claims보다 클 때 claim할 수 있다고 설명하고, DTR 모드에서는 구성원 수에 따른 별도 claim 계산을 언급한다. — [FactionsUUID 소개](https://factions.support/introduction/)
- FactionsUUID 정의는 power를 영토 점유에 쓰는 영향력으로 설명하고, 플레이어 사망 시 power를 잃어 영토가 over-claim될 수 있다고 설명한다. — [FactionsUUID 정의](https://factions.support/definitions/)
- FactionsUUID 명령에는 플레이어/faction power 조정, 영구 faction power, DTR 조정, claim/unclaim, safezone/warzone 관리가 포함된다. — [FactionsUUID 명령](https://factions.support/commands/); [권한 노드](https://factions.support/permissionnodes/)
- FactionsUUID 권한의 `build`, `destroy`, `container`, `door`, `button`, `lever`, `item`, `piston` 관련 액션은 “영토 관계와 faction permission”을 기준으로 보호 행위를 나누는 모델을 보여준다. — [Faction Permissions](https://factions.support/permissions/)
- FactionsUUID 관계 컨텍스트의 가능한 값은 member, ally, truce, neutral, enemy이며, 영토에 서 있는 플레이어의 현재 관계도 permission context로 노출된다. — [Permission Contexts](https://factions.support/contexts/)
- FactionsUUID는 faction의 `peaceful`, `permanent`, `raidability`, `shield` 상태를 노출하고, `/f set explosions`로 peaceful faction 영토의 폭발 허용 여부를 설정한다. — [FactionsUUID 명령](https://factions.support/commands/); [Placeholders](https://factions.support/placeholders/)
- SaberFactions 설정은 기본 관계, 포털 생성 최소 관계, 적 근접 시 flight 비활성화, 피스톤 보호, chorus teleport 제한, grace period(폭발 거부)를 설정 가능하게 한다. — [SaberFactions config.yml](https://github.com/JorisPV/SaberFactions/blob/master/config.yml)
- SaberFactions 공식 저장소는 grace period, alt system, faction audit, wall/buffer check, shield addon, upgrades, TNT bank를 기능으로 열거한다. — [SaberFactions README](https://github.com/SaberLLC/Saber-Factions)
- SaberFactions 릴리스는 `canDamagerHurtDamagee(Entity, Entity, boolean)`를 PvP 판정의 정식 경로로 추가하고, combust·potion splash 등의 이벤트 핸들러를 entity 기반 판정으로 통일했다고 기록한다. — [SaberFactions 4.1.9 릴리스](https://github.com/SaberLLC/Saber-Factions/releases)
- FactionsUUID의 `/f relation`은 ally/enemy/neutral/truce 관계를 설정하고, faction chat도 관계별 채널을 지원한다. — [FactionsUUID 명령](https://factions.support/commands/)

### Inferences
- 보호 이벤트는 `Location → claim → defender faction → actor FPlayer → relation/role → faction permission → 현재 raid/peaceful/grace 상태` 순으로 판정하는 파이프라인이 적합하다.
- 블록 이벤트만 막으면 TNT, 피스톤, 유체, 발사체, 엔티티 상호작용으로 우회될 수 있으므로 이벤트 종류별 정책과 공통 `canInteract/canDamage` 서비스를 둬야 한다.
- “전쟁”은 전용 전투 시뮬레이션이라기보다 enemy 관계, PvP 판정, raidable/overclaim 상태, 폭발·침입 허용의 결합으로 구현된다고 보는 편이 문서와 소스에 부합한다. 독립적인 전쟁 세션/전선/항복 시스템은 이 조사에서 확인되지 않았다.

### Gaps
- FactionsUUID 공식 문서에서 실제 전투 승패·전쟁 선포의 단일 규칙은 확인되지 않았다. 구현 시 “enemy 관계=전쟁”으로 할지, 별도 전쟁 객체를 둘지는 서버 기획 결정이다.
- 모든 보호 이벤트 목록과 이벤트 우선순위는 버전·포크에 따라 달라질 수 있다.

## 3. 경제·업킵·명령·영속성 및 국가 플러그인 설계 trade-off

### Takeaway
Factions 계열은 faction bank와 개인 잔액을 분리하고 claim/warp/TNT/업그레이드 비용을 경제·권한 시스템과 연결한다. 대신 기능이 늘수록 보호 판정과 저장 마이그레이션 복잡도가 커지므로, 국가 플러그인은 핵심 도메인과 선택형 모듈을 분리하는 편이 안전하다.

### Cited Findings
- FactionsUUID는 개인 balance와 faction balance를 구분하고, money send/withdraw와 faction bank 권한을 제공한다. — [FactionsUUID 정의](https://factions.support/definitions/); [명령](https://factions.support/commands/); [권한 노드](https://factions.support/permissionnodes/)
- FactionsUUID 최신 릴리스는 faction 관리자가 구성원에게 매일 fee를 부과해 개인 잔액에서 faction bank로 이체할 수 있는 dues 기능을 추가했다고 명시한다. — [FactionsUUID 4.7.0 릴리스](https://factions.support/news/4.7.0/)
- FactionsUUID는 TNT bank를 제공하며 deposit/withdraw/fill/siphon 명령과 권한을 문서화한다. — [FactionsUUID 명령](https://factions.support/commands/); [권한 노드](https://factions.support/permissionnodes/)
- FactionsUUID의 업그레이드 문서는 claim limit boost, power boost, faction shield, TNT bank, warp 등의 기능을 업그레이드로 제공할 수 있다고 설명한다. — [FactionsUUID Upgrades](https://factions.support/upgrades/)
- SaberFactions 설정은 warp 설치/사용 비용과 최대 warp 수를 설정하며, scoreboard에 개인 잔액·faction balance·power·claims를 표시할 수 있다. — [SaberFactions config.yml](https://github.com/JorisPV/SaberFactions/blob/master/config.yml)
- SaberFactions는 faction points를 게임 내 통화로 사용하는 faction shop/missions를 공식 기능으로 열거한다. — [SaberFactions README](https://github.com/SaberLLC/Saber-Factions)
- 주요 명령 표면은 생성/초대/가입/탈퇴/추방, 역할 승격·강등·leader 이전, claim/unclaim/map, home/warp, relation, chat, money, power, protection 설정으로 구성된다. — [SaberFactions Commands and Permissions](https://saberdevelopment.gitbook.io/wiki/general-informations/commands-and-permissions); [FactionsUUID Commands](https://factions.support/commands/)
- FactionsUUID는 `/fa save-all`, 자동 저장 on/off, `/fa reload`를 제공한다. — [FactionsUUID Commands](https://factions.support/commands/)
- SaberFactions의 과거 공개 소스는 `MemoryFaction`이 `Faction`과 `EconomyParticipator`를 구현하고, `FPlayer`가 파일에서 Gson으로 데이터를 읽는 흔적을 보여준다. 이는 포크/구버전 구조의 직접 증거다. — [MemoryFaction 공개 미러](https://git.beatrice.wtf/SpigotMC/Saber-Factions/src/commit/ba5ee1ae485c6944762f7459ac476be98eabd549/src/main/java/com/massivecraft/factions/zcore/persist/MemoryFaction.java); [FPlayer 공개 미러](https://git.beatrice.wtf/SpigotMC/Saber-Factions/src/commit/8ff84019eea24af5aee85dbc143bf253b64d4926/src/org/mcteam/factions/FPlayer.java)
- SaberFactions 릴리스 기록은 파일 관리 시스템 개편, FactionData 관리 개편, long 타입으로의 금액/TNT/업그레이드 비용 확장을 명시한다. — [SaberFactions releases](https://github.com/SaberLLC/Saber-Factions/releases)

### Inferences
- 국가 플러그인 MVP는 `국가 금고`, `세금/업킵`, `영토 유지비`, `전쟁 비용`을 별도 ledger 거래로 기록하고, 잔액 변경은 명령 성공 후 원자적으로 커밋해야 한다.
- 명령은 도메인 서비스로 위임하고 `/nation`, `/nation admin`처럼 사용자 명령과 관리자 명령을 분리하면 권한 검증·감사 로그가 단순해진다.
- 경제를 Vault/외부 경제에 강하게 결합하면 서버 생태계 호환성은 높아지지만 외부 플러그인 실패가 국가 상태에 영향을 줄 수 있다. 내부 ledger를 기준 원장으로 두고 외부 경제는 adapter로 연결하는 설계가 더 복구 가능하다.
- Factions/SaberFactions의 폭넓은 기능(비행, TNT, GUI, 미션, audit, shield)은 즉시 풍부한 서버 경험을 주지만, 국가 플러그인에는 기능 플래그·모듈 경계·버전별 이벤트 adapter가 필수라는 trade-off를 시사한다.

### Gaps
- 최신 SaberFactions의 실제 저장 파일명, 직렬화 스키마, 저장 실패 시 복구 전략은 공식 README/문서만으로 확정할 수 없다.
- “정기 업킵”은 FactionsUUID 4.7.0의 dues처럼 일부 버전에 추가된 기능과, 서버별 설정을 구분해야 한다. 모든 SaberFactions 버전의 업킵 동작이 동일하다는 근거는 찾지 못했다.
- 공개 소스는 GPL/라이선스 및 포크별 변경 이력이 섞여 있으므로, 국가 플러그인에 코드를 복사할 때는 해당 저장소의 라이선스와 API 호환성을 별도 법무·기술 검토해야 한다. (이 문서는 라이선스 전문을 조사하지 않았다.)
