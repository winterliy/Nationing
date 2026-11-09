# 국가플러그인 대안 분석

## 어떤 플러그인을 핵심 참고 구현으로 삼을 것인가

### Takeaway
국가(state) 시스템의 가장 좋은 기준 구현은 Towny의 계층형 모델(주민→마을→국가), 인접 청크 확장, 세금·은행·동맹·적대 관계다. 보호 엔진은 WorldGuard의 영역/우선순위/플래그 API를 우선 참고하고, 플레이어 친화적 청구 UX는 GriefPrevention에서 가져오는 조합이 적합하다. Residence는 세밀한 개인/소유지 권한 모델 참고용이지만, 새 국가 시스템의 주된 공간 모델로 채택하기에는 오래된 계보와 별도 의미 체계가 부담이다.

### Cited Findings
- Towny는 마을의 집합으로 국가를 만들며, 수도·국가 지도자·국가 은행·세금·동맹·적대 관계·국가 스폰을 제공한다 — [Towny: How Towny Works](https://github-wiki-see.page/m/TownyAdvanced/Towny/wiki/How-Towny-Works)
- Towny의 기본 영토 단위는 townblock이며, 일반 확장은 기존 점유 영역과 직접 인접해야 하고 outpost는 예외다 — [Towny: How Towny Works](https://github-wiki-see.page/m/TownyAdvanced/Towny/wiki/How-Towny-Works)
- Towny 저장소는 Bukkit/Paper/Folia용 공개 소스이며 API 사용 지침과 Maven 빌드 방법을 제공한다 — [Towny GitHub repository](https://github.com/TownyAdvanced/Towny)
- WorldGuard는 영역, 영역 플래그, 우선순위와 상속, 공간 질의, 플래그 계산, 보호 질의, 커스텀 플래그/세션 핸들러 API를 문서화한다 — [WorldGuard documentation](https://worldguard.enginehub.org/en/latest/)
- WorldGuard에서 전역 영역은 가장 낮은 우선순위이며 일반 영역이 이를 덮어쓸 수 있다. 이 우선순위 규칙은 수도/국경/비무장지대 같은 중첩 정책의 참고 모델이 된다 — [WorldGuard global region](https://worldguard.enginehub.org/en/latest/regions/global-region/)
- GriefPrevention은 플레이어가 골든 셔블로 클레임을 만들고, 신뢰 단계(access/container/build trust)를 관리하며, 클레임 블록을 플레이 시간에 따라 누적·제한한다 — [GriefPrevention documentation](https://docs.griefprevention.com/); [GriefPrevention source](https://github.com/GriefPrevention/GriefPrevention)
- GriefPrevention 소스에는 클레임 수, 최소 크기, 만료, 확장, 보호 대상 엔티티·컨테이너·문·스위치·폭발·피스톤 등을 설정하는 경계가 명시돼 있다 — [GriefPrevention source configuration](https://github.com/GriefPrevention/GriefPrevention/blob/master/src/main/java/me/ryanhamshire/GriefPrevention/GriefPrevention.java)
- Residence는 “self serve area protection”과 cuboid residence를 제공하는 Bukkit 플러그인으로, 공개 저장소의 README가 Wiki·소스 빌드·라이선스를 안내한다 — [Residence repository](https://github.com/bekvon/Residence); [Residence plugin descriptor](https://github.com/Zrips/Residence/blob/master/src/main/resources/plugin.yml)

### Inferences
- 국가 시스템의 권위 데이터는 `Nation -> Town/Settlement -> Chunk/Cell -> Plot/Policy`처럼 별도 도메인 모델로 두고, WorldGuard/GriefPrevention은 보호 판정 어댑터로 연결하는 편이 확장 경계가 명확하다. Towny 전체를 의존하면 국가·전쟁·경제가 이미 결합된 설계를 수용해야 한다.
- 참고 우선순위는 (1) Towny의 조직·영토·경제, (2) WorldGuard의 정책 판정/API, (3) GriefPrevention의 UX·신뢰 권한, (4) Residence의 cuboid/세부 권한 순서가 적절하다.

### Gaps
- 각 프로젝트의 최신 내부 자료구조와 실제 성능 특성은 문서만으로 확정할 수 없다. 도입 전에는 목표 Minecraft/Paper 버전에 맞춰 소스와 실제 서버 부하를 별도 벤치마크해야 한다.

## 핵심 알고리즘과 확장 경계는 무엇인가

### Takeaway
토지 알고리즘은 서로 목적이 다르다. GriefPrevention은 플레이어별 면적 예산과 임의 직사각형 클레임, WorldGuard는 중첩 가능한 cuboid 영역과 플래그 계산, Towny는 고정 격자·인접 확장·인구/경제 기반 상한을 쓴다. 전쟁은 일반 보호 플래그를 끄는 방식보다 SiegeWar처럼 비파괴 점령 상태를 별도 상태 머신으로 두는 것이 국가 운영에 안전하다.

### Cited Findings
- GriefPrevention은 클레임 소유자별 초기 블록, 시간당 누적 블록, 최대 누적량, 최대 클레임 수, 최소 폭/면적, 만료 일수와 관리자 클레임을 설정한다 — [GriefPrevention admin guide](https://github.com/GriefPrevention/GriefPrevention-Docs/blob/master/administrators_guide.md); [GriefPrevention source](https://github.com/GriefPrevention/GriefPrevention/blob/master/src/main/java/me/ryanhamshire/GriefPrevention/GriefPrevention.java)
- WorldGuard의 클레임은 WorldEdit 선택 영역에서 생성되고, 영역별 소유자/멤버와 권한 노드·플래그·우선순위로 판정된다 — [WorldGuard quick start](https://github.com/EngineHub/WorldGuardDocs/blob/master/source/regions/quick-start.rst); [WorldGuard permissions](https://github.com/EngineHub/WorldGuardDocs/blob/master/source/permissions.rst)
- WorldGuard는 내부 영역 저장 드라이버와 SQL 저장을 제공하지만 SQL 지원은 deprecated로 표시돼 있다 — [WorldGuard configuration](https://github.com/EngineHub/WorldGuardDocs/blob/master/source/config.rst)
- Towny는 townblock을 격자로 취급하고 인접하지 않은 점유를 기본적으로 제한하며, 도시 인구·보너스·경제 설정으로 점유 한도를 조절한다 — [Towny wiki](https://github-wiki-see.page/m/TownyAdvanced/Towny/wiki/How-Towny-Works); [Towny GitHub language/config references](https://github.com/TownyAdvanced/Towny)
- SiegeWar는 Towny용 별도 애드온이며, 비파괴 전쟁·수일 지속되는 siege·도시 전체 점령·평화주의/전쟁 면역을 핵심으로 한다 — [Towny SiegeWar guide](https://townyadvanced.github.io/siegewar.html)
- Towny FlagWar는 국가·도시 영역을 빠르게 깃발로 점령하는 전략 게임식 전쟁 시스템이다 — [TownyAdvanced FlagWar](https://github.com/TownyAdvanced/FlagWar)
- Towny EventWar는 riot, town-vs-town, civil war, independence war, nation-vs-town, nation-vs-nation, alliance-vs-alliance, world war 등 전쟁 범위를 구분한다 — [Towny EventWar](https://github.com/TownyAdvanced/townyadvanced.github.io/blob/master/eventwar.html)

### Inferences
- 새 구현의 토지 인덱스는 월드별 `(chunkX, chunkZ)` 키와 국가/도시 ID를 기본으로 하고, 다각형·cuboid는 표시/특수 구역용 보조 레이어로 두는 것이 확장에 유리하다. 인접성은 4방향 그래프 탐색으로 검증하고, outpost/해외 영토는 명시적 예외 플래그로 처리한다.
- 영토 판정과 정책 판정을 분리해야 한다. 영토 서비스는 “누구의 땅인가”를, 정책 엔진은 “이 이벤트를 허용하는가”를 반환하게 하고, WorldGuard식 우선순위·상속을 국가 법률/전시법 구현에 활용할 수 있다.
- 전쟁은 `peace -> declared -> preparation -> active siege -> occupation -> peace/annexation` 상태와 점수·시간·참여자·휴전 조건을 별도 저장해야 한다. SiegeWar의 비파괴 경계는 건축물 롤백/파괴 동기화 없이도 전쟁을 운영하게 해준다.
- WorldGuard/GP/Residence를 동시에 “소유권 원장”으로 쓰면 충돌한다. 하나를 권위 원장으로 선택하고 나머지는 보호 후크 또는 마이그레이션 대상으로 제한해야 한다.

### Gaps
- 공식 문서만으로 WorldGuard의 모든 중첩 플래그 계산 우선순위와 Towny의 최신 전쟁 점수 공식 전체를 재구성할 수 없다. 구현 전 각 프로젝트의 API 테스트와 특정 버전 소스 검토가 필요하다.

## 지도·월드 경계·대안 조합은 무엇인가

### Takeaway
BlueMap은 3D 지도와 공개 Marker API가 있어 국가 경계·수도·전쟁 전선을 별도 마커셋으로 표현하기 가장 좋은 참고 대상이다. Dynmap은 성숙한 2D 웹 지도와 다양한 연동 사례가 강점이다. WorldBorder는 국가 국경이 아니라 월드 생성·청크 사전 생성·삭제(trim)와 이동 제한 도구이므로, 국가 영토 모델과 분리해 운영해야 한다.

### Cited Findings
- BlueMap은 Minecraft 월드를 3D 모델로 렌더링하고 웹 뷰어에 표시하는 공개 프로젝트이며, BlueMapAPI는 플러그인/모드가 지도에 접근하는 API다 — [BlueMap GitHub organization](https://github.com/BlueMap-Minecraft); [BlueMapAPI repository](https://github.com/BlueMap-Minecraft/BlueMapAPI)
- BlueMap은 marker-set과 point/line/shape 계열 마커를 제공하고, 제3자 플러그인이 API로 마커를 관리할 수 있다 — [BlueMap marker documentation](https://github.com/BlueMap-Minecraft/BlueMapWiki/blob/master/wiki/customization/Markers.md)
- Dynmap은 전체 지도를 보이려면 플레이/비행 또는 별도 청크 사전 생성 플러그인이 필요하고, Dynmap 자체는 월드 데이터를 수정하거나 청크 생성을 담당하지 않는다 — [Dynmap FAQ](https://dynmap.wiki.gg/wiki/FAQ)
- WorldBorder의 `fill`은 경계까지 누락 청크를 생성하고 `trim`은 경계 밖 청크를 제거하며, 중단 후 진행을 재개하는 절차가 문서화돼 있다 — [WorldBorder commands and permissions](https://dev.bukkit.org/projects/worldborder/pages/commands-and-permissions)
- WorldBorder trim 후 Dynmap을 다시 fullrender해야 지도 표시를 갱신할 수 있다 — [WorldBorder commands and permissions](https://dev.bukkit.org/projects/worldborder/pages/commands-and-permissions)
- Towny 생태계에는 Dynmap-Towny 애드온이 언급돼 있으며, Towny 영토를 지도 색상으로 표시하는 명령이 제공된다 — [Towny repository README](https://github.com/TownyAdvanced/Towny); [Towny wiki](https://github-wiki-see.page/m/TownyAdvanced/Towny/wiki)

### Inferences
- 국가 지도 연동은 렌더러에 직접 국경 알고리즘을 넣지 말고 `TerritoryChanged` 이벤트를 받아 BlueMap/Dynmap 어댑터가 마커를 갱신하도록 설계하는 것이 좋다. 국가별 색상·국경선·수도·전쟁 전선은 marker-set을 분리하면 클라이언트가 선택적으로 켤 수 있다.
- BlueMap은 3D 지형/외교 시각화가 핵심인 신규 국가 서버에 우선 추천한다. 2D 지도 호환성과 기존 연동 생태계가 더 중요하면 Dynmap을 대안으로 둔다.
- WorldBorder는 서버 전체 플레이 가능 구역/성능 관리용으로만 사용하고, 국가가 획득한 영토에는 사용하지 않는 편이 안전하다. 국경 축소나 trim은 데이터 삭제 성격이므로 국가 양도·전쟁 결과와 직접 결합하면 복구·감사 문제가 생긴다.
- 국가 시스템 대안 스택은 `Towny 참고 모델 + 자체 국가 서비스 + WorldGuard 보호 어댑터 + BlueMap 표시 어댑터 + WorldBorder 운영 경계`다. 상용/폐쇄형 플러그인보다 공개 소스와 명시적 API를 우선하면 마이그레이션과 테스트가 쉽다.

### Gaps
- BlueMap과 Dynmap의 현재 렌더 성능, 메모리 사용량, 대규모 마커 갱신 비용은 설치 환경·월드 크기·렌더 설정에 크게 좌우되며 공식 문서에서 일반적인 수치로 제시되지 않는다. 목표 서버 규모에서 부하 시험이 필요하다.
