# Vault와 EssentialsX 핵심 로직 및 국가 플러그인 통합

## 1. Vault의 추상화와 서비스 등록은 국가 플러그인에 무엇을 제공하는가

### Takeaway
Vault는 경제·권한·채팅을 하나의 안정적인 호출 표면으로 추상화하는 Bukkit API/브리지다. 국가 플러그인은 특정 경제·권한 플러그인에 직접 결합하지 않고 `ServicesManager`에서 `Economy`, `Permission`, `Chat` provider를 조회해 사용할 수 있지만, Vault 자체가 국가/영토/외교/세금 도메인 모델을 제공하지는 않는다.

### Cited Findings
- Vault의 공식 프로젝트 설명은 Vault를 Permissions, Chat, Economy API로 정의하며, 각 개별 provider에 직접 hook/depend하지 않고 지원 시스템에 연결하는 목적이라고 설명한다 — [Vault 프로젝트 설명](https://dev.bukkit.org/projects/vault)
- VaultAPI README의 예제는 `RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class)`로 provider를 얻고, `rsp == null` 및 `econ != null`을 검사한 뒤 사용한다. 권한과 채팅도 각각 `Permission.class`, `Chat.class`로 같은 패턴을 쓴다 — [VaultAPI README와 구현 예제](https://github.com/MilkBowl/VaultAPI)
- Bukkit `ServicesManager`는 service interface와 provider 구현을 관리하고, 여러 provider가 등록되면 우선순위가 가장 높은 provider가 선택된다. 핵심 API는 `register`, `getRegistration`, `getRegistrations`, `load`, `unregisterAll`이다 — [Spigot ServicesManager Javadoc](https://hub.spigotmc.org/javadocs/spigot/org/bukkit/plugin/ServicesManager.html)
- `load(Class<T>)`는 provider가 없으면 `null`을 반환하며 최고 우선순위 provider를 돌려준다. 따라서 국가 플러그인은 enable 시점에 provider 부재를 처리하고, 런타임에 provider 교체/재등록 가능성을 고려해 캐시를 무기한 신뢰하지 않는 편이 안전하다 — [Spigot ServicesManager Javadoc](https://hub.spigotmc.org/javadocs/spigot/org/bukkit/plugin/ServicesManager.html)
- Vault의 `Economy` 인터페이스에는 계정 존재/생성, 잔액 조회, 입금·출금, 잔액 보유 여부, 통화명·포맷 관련 API가 있고, 거래 결과는 `EconomyResponse`로 성공 여부·처리 금액·잔액·오류를 전달한다 — [VaultAPI Economy.java](https://github.com/MilkBowl/VaultAPI/blob/master/src/main/java/net/milkbowl/vault/economy/Economy.java)
- Vault `Permission`과 `Chat`은 각각 권한 질의/그룹·세계별 권한 및 prefix/suffix/group 같은 채팅 메타데이터 접근을 추상화한다 — [VaultAPI Permission.java](https://github.com/MilkBowl/VaultAPI/blob/master/src/main/java/net/milkbowl/vault/permission/Permission.java), [VaultAPI Chat.java](https://github.com/MilkBowl/VaultAPI/blob/master/src/main/java/net/milkbowl/vault/chat/Chat.java)
- EssentialsX 저장소는 Vault가 설치되어야 지원되는 권한 플러그인의 chat prefix/suffix와 group support를 사용할 수 있다고 명시한다 — [EssentialsX 저장소 README](https://github.com/EssentialsX/Essentials)

### Inferences
- 국가 플러그인의 의존성은 `VaultAPI`를 compile-only/provided로 두고, 실제 경제 provider는 EssentialsX Economy 또는 다른 provider로 교체 가능하게 설계하는 것이 적절하다. 단, 국가 재정은 개인 잔액 API의 단순 합계가 아니라 별도 도메인 계정/원장을 가져야 한다.
- Vault service가 우선순위 기반으로 선택되므로 “현재 provider가 무엇인지”를 `/nation debug` 또는 로그로 노출하고, 서버 운영자가 provider 중복 등록을 감지할 수 있게 해야 한다.

### Gaps
- Vault API는 국가 treasury, 복수 통화의 표준 모델, 원자적 다중 계정 거래, 거래 사유/감사 원장의 표준 계약을 해결하지 않는다. VaultAPI 저장소의 공개 이슈에도 BigDecimal 전환과 multicurrency 지원 요청이 별도로 남아 있다 — [VaultAPI issues](https://github.com/MilkBowl/VaultAPI/issues)

## 2. EssentialsX의 경제·권한·채팅 및 이벤트/오프라인 플레이어 패턴

### Takeaway
EssentialsX는 개인 플레이어 중심의 기본 경제와 명령, EssentialsXChat의 포맷/반경 채팅을 제공한다. 국가 플러그인은 Vault 경제 호출의 성공 여부를 반드시 확인하고 UUID/`OfflinePlayer` 기반으로 오프라인 계정을 다루며, Bukkit 이벤트의 우선순위·취소 상태·메인 스레드 규칙을 지켜야 한다.

### Cited Findings
- EssentialsX 설정은 `starting-balance`, `max-money`, `min-money`, 최소 `/pay` 금액, 거래 로그 옵션을 제공하며 음수 잔액은 `essentials.eco.loan` 권한과 연결된다 — [EssentialsX config.yml](https://github.com/EssentialsX/Essentials/blob/2.x/Essentials/src/main/resources/config.yml)
- EssentialsX 문서는 `/eco`, `/balance`, `/baltop`, `/pay`를 기본 경제 명령으로 설명하고, 내장 경제 API가 다른 감지된 경제 시스템으로 redirect할 수 있다고 설명한다 — [EssentialsX Economy 문서](https://github.com/EssentialsX/Essentials/wiki/Essentials-Economy)
- EssentialsX의 Vault economy provider는 `OfflinePlayer` 오버로드에서 UUID를 사용해 `Economy.add/getBalance/subtract`를 호출하고, 음수 금액·없는 사용자·대출 불허·최대 금액 초과를 `EconomyResponse.FAILURE`로 반환한다 — [EssentialsX VaultEconomyProvider.java](https://github.com/essentialsx/essentials/blob/2.x/Essentials/src/main/java/com/earth2me/essentials/economy/vault/VaultEconomyProvider.java)
- EssentialsX의 Vault compatibility layer는 `hasAccount`, `createPlayerAccount`, `getBalance(OfflinePlayer)`, `deposit`, `withdraw`를 Vault adapter로 위임하며 `Bukkit.getServicesManager().load(Economy.class)`로 provider를 로드한다 — [EssentialsX VaultLayer.java](https://github.com/EssentialsX/Essentials/blob/2.x/Essentials/src/main/java/com/earth2me/essentials/economy/layers/VaultLayer.java)
- EssentialsX 설정의 채팅 섹션은 반경 채팅, shout/question 우회, 포맷, `{PREFIX}`, `{SUFFIX}`, `{GROUP}` placeholder를 정의하며 prefix/suffix/group 값은 permissions plugin에서 공급된다 — [EssentialsX config.yml](https://github.com/EssentialsX/Essentials/blob/2.x/Essentials/src/main/resources/config.yml)
- EssentialsChat의 `plugin.yml`은 `Essentials`에 의존하고 `essentials.chat.color`, `essentials.chat.format`, `essentials.chat.local`, `essentials.chat.shout` 및 수신 권한을 선언한다 — [EssentialsChat plugin.yml](https://github.com/EssentialsX/Essentials/blob/2.x/EssentialsChat/src/main/resources/plugin.yml)
- `OfflinePlayer.getName()`은 단일 게임 세션을 넘어 고유하지 않을 수 있어 영속 저장에는 `getUniqueId()` 사용이 권장된다. `Bukkit.getOfflinePlayer(UUID)`는 온라인 여부와 무관하게 객체를 얻지만, `getOfflinePlayer(String)`은 deprecated이고 이름/UUID 조회에 네트워크 요청이 발생할 수 있다 — [Spigot OfflinePlayer Javadoc](https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/OfflinePlayer.html), [Spigot Bukkit Javadoc](https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/Bukkit.html)
- Bukkit의 `EventHandler`는 `priority` 순서(LOWEST→LOW→NORMAL→HIGH→HIGHEST→MONITOR)와 `ignoreCancelled`를 제공한다. `PlayerJoinEvent`는 입장 시 발생하고, 플레이어 이벤트 패키지 문서는 join/quit 같은 이벤트를 제공한다 — [Spigot EventHandler Javadoc](https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/event/EventHandler.html), [PlayerJoinEvent Javadoc](https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/event/player/PlayerJoinEvent.html), [player event package](https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/event/player/package-summary.html)
- EssentialsX 소스의 과거 호환성 설명은 초기 Vault/economy 통합이 username 문자열 기반이었고 UUID 전환 문제가 있었다고 기록한다 — [EssentialsX EssentialsUpgrade.java](https://github.com/EssentialsX/Essentials/blob/2.x/Essentials/src/main/java/com/earth2me/essentials/EssentialsUpgrade.java)

### Inferences
- 돈을 입금/출금한 뒤 `EconomyResponse.transactionSuccess()`가 false면 국가 가입비·세금·전쟁 비용의 국가 상태를 커밋하지 말아야 한다. 반대로 국가 DB 커밋 후 경제 출금이 실패하면 보상/복구 작업이 필요하므로, 한 번의 Bukkit/Vault 호출로 다중 시스템 원자성을 얻을 수 없다는 전제를 두어야 한다.
- `OfflinePlayer` 대상의 국가 세금·배당은 UUID를 canonical key로 삼고, 플레이어 객체가 필요할 때만 `offline.getPlayer()`가 null일 수 있음을 처리한다. 온라인 전환 시 `PlayerJoinEvent`에서 미수금/알림/캐시를 동기화하는 패턴이 적절하다.
- `MONITOR`는 관찰용 우선순위로 사용하고 상태 변경은 낮은/정상 우선순위에서 처리하는 것이 안전하다. 취소된 이벤트를 정책상 무시할지 `ignoreCancelled=true`로 명시해야 한다.

### Gaps
- EssentialsX의 경제는 개인 계정, 명령 비용, 아이템 판매/거래 보조에 초점이 있으며 국가별 예산·세율·공동 금고의 동시성·감사 원장·롤백은 제공하지 않는다 — [EssentialsX Economy 문서](https://github.com/EssentialsX/Essentials/wiki/Essentials-Economy), [EssentialsX config.yml](https://github.com/EssentialsX/Essentials/blob/2.x/Essentials/src/main/resources/config.yml)
- EssentialsXChat의 prefix/group은 권한 provider의 메타데이터를 표시하는 계층이지 국가 직위·시민권·외교 상태를 관리하는 계층이 아니다 — [EssentialsX config.yml](https://github.com/EssentialsX/Essentials/blob/2.x/Essentials/src/main/resources/config.yml), [EssentialsX README](https://github.com/EssentialsX/Essentials)

## 3. 국가 플러그인 통합 설계와 Vault/EssentialsX가 해결하지 않는 문제

### Takeaway
국가 플러그인은 Vault를 “외부 개인경제·권한·표시 메타데이터 어댑터”로 사용하고, 국가의 정본 데이터는 자체 저장소와 트랜잭션 원장으로 소유해야 한다. 국가 기능의 핵심은 서비스 연결이 아니라 영토/회원/역할/정책/재정/외교 상태를 일관되게 묶는 도메인 로직이다.

### Cited Findings
- Bukkit 서비스는 provider를 교체 가능한 인터페이스로 제공할 뿐이며, provider 우선순위와 null 가능성만 정의한다. 국가 객체, 영토 claim, 전쟁 상태, 조약, 세금 정책은 ServicesManager 계약에 없다 — [Spigot ServicesManager Javadoc](https://hub.spigotmc.org/javadocs/spigot/org/bukkit/plugin/ServicesManager.html)
- Vault의 표준 경제 호출은 플레이어별 `OfflinePlayer` 계정과 입금/출금 결과를 다루지만, 국가 treasury ID나 두 계정 간 원자적 transfer 계약은 제공하지 않는다 — [VaultAPI Economy.java](https://github.com/MilkBowl/VaultAPI/blob/master/src/main/java/net/milkbowl/vault/economy/Economy.java)
- EssentialsX 설정은 개인 시작 잔액, 잔액 상·하한, 플레이어 간 `/pay`, 명령 비용과 채팅 반경/포맷을 정의하지만 국가 지도·국경·세율·관세·의회·외교·전쟁 규칙을 정의하지 않는다 — [EssentialsX config.yml](https://github.com/EssentialsX/Essentials/blob/2.x/Essentials/src/main/resources/config.yml), [EssentialsX Wiki](https://github.com/EssentialsX/Essentials/wiki/Home)
- EssentialsX README는 EssentialsX를 명령·홈·워프·텔레포트·메시지·moderation·Vault Economy를 제공하는 서버 기본 기능 모음으로 설명한다 — [EssentialsX 저장소 README](https://github.com/EssentialsX/Essentials)

### Inferences
- 권장 경계는 다음과 같다: (1) 국가 DB가 `nation`, `nation_member`, `nation_role`, `territory`, `treasury_ledger`, `diplomacy`의 정본을 소유, (2) Vault Economy는 개인 계정에서 국가 금고로/금고에서 개인으로 이동하는 외부 side effect, (3) Vault Permission은 `nation.member.<id>`, `nation.role.<role>` 같은 서버 권한 노드의 보조 수단, (4) Vault Chat/EssentialsXChat은 `{PREFIX}`·`{SUFFIX}`에 국가 직위 표시를 투영하는 presentation layer로 사용한다.
- 잔액 일관성을 위해 국가 계정별 ledger entry에 `transaction_id`, `actor_uuid`, `source`, `amount`, `before/after` 또는 불변 delta, `status`, `created_at`을 기록하고, Vault 호출 성공 응답을 받은 경우에만 완료 처리한다. 실패·재시도·서버 중단을 위한 idempotency key와 reconciliation 명령이 필요하다. 이는 Vault/EssentialsX API가 제공하지 않는 애플리케이션 책임이다.
- 오프라인 세금은 Bukkit 이름이 아니라 UUID로 예약하고, 로그인 시 미수금 정책을 적용한다. 이름 문자열 API는 deprecated/비고유이므로 국가 membership과 금고 소유권의 키로 사용하면 안 된다.
- 국가 채팅은 EssentialsXChat의 전역/반경 채팅을 그대로 국가 채널로 오해하지 말고 별도 채널 라우팅과 수신자 집합을 구현해야 한다. 국가 직위 표시는 권한 메타데이터로 projection할 수 있지만, 국가 구성원 목록과 권한 plugin 그룹을 동일한 정본으로 취급하면 탈퇴/쿠데타/외교 역할 변경의 일관성이 깨질 수 있다.

### Gaps
- 조사한 공식 Vault/EssentialsX/Bukkit 문서와 공개 소스에는 국가 플러그인 전용 표준(국가 ID namespace, treasury API, 영토 충돌 해결, 전쟁/외교 이벤트, multi-server 분산 원장)이 없다. 따라서 이 부분은 별도 도메인 설계와 테스트가 필요하다.
- Vault API가 `double` 금액을 노출하는 오래된 계약을 포함하고 EssentialsX 내부 일부 계층은 `BigDecimal`을 사용하므로, 정확한 회계 정본을 Vault `double`에만 저장할 수는 없다 — [VaultAPI Economy.java](https://github.com/MilkBowl/VaultAPI/blob/master/src/main/java/net/milkbowl/vault/economy/Economy.java), [EssentialsX VaultLayer.java](https://github.com/EssentialsX/Essentials/blob/2.x/Essentials/src/main/java/com/earth2me/essentials/economy/layers/VaultLayer.java)
- 국가 재정과 개인 경제를 서버 간 공유할 때의 분산 락/중복 지급/재연결 순서는 Vault와 EssentialsX의 범위를 벗어난다. 멀티서버라면 중앙 DB 원장, unique idempotency constraint, outbox/reconciliation 전략을 별도로 검증해야 한다.
