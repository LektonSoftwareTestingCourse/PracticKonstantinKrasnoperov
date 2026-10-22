# Практика 3: тест-кейсы и пирамида тестирования

## Общие тестовые данные

Все суммы — в копейках. Базовая карта: ACTIVE, срок `1229`, баланс `100000`, дневной лимит `1000000`, месячный `3000000`. Использованные лимиты равны нулю, если в кейсе не указано другое.

Запрос `Q`: `mti=0100`, `stan=000123`, PAN базовой карты с BIN `400000`, `processingCode=000000`, `amount=10000`, `currencyCode=643`, `transmissionDateTime=2026-10-22T12:00:00Z`, `terminalId=TERM0001`, `terminalType=POS`, `merchantId=MERCH0000000005`, `mcc=5411`, `acquirerId=ACQ001`. Для прямого вызова Authorization задаётся `issuerId=ISS001`.

Асинхронную доставку ждём до 10 секунд с опросом каждые 100 мс. Для проверки TTL ждём больше 60 секунд после попадания сообщения в DLQ. Это время ожидания теста, а не требование к скорости системы.

| Требование | Что проверяем | Основание |
|---|---|---|
| R-P3-GW | Проверка входных запросов Gateway | [GW-VAL], [GW-FILTER] |
| R-P3-BIN | Выбор эмитента по BIN | [ROUTING], [ROUTE] |
| R-P3-LOG | Доставка в Logger и отсутствие дублей | [LOG-LISTENER], [LOG-SERVICE] |
| R-P3-CONFIRM | Откат при отказе брокера или отсутствии подтверждения за 2 с | [TZ-SW], [PUBLISH] |
| R-P3-OUTBOX | Отправка событий из outbox в Notification | [NOTIFIER], [POLLER], [OUTBOX], [NOTIFICATION] |
| R-P3-DLQ | Три попытки обработки, затем DLQ; срок хранения 60 с | [ARCH-DOC], [MQ-SW] |
| R-P3-UI | Появление операции в Dashboard | [NGINX], [WS] |

## (б) Новые тест-кейсы — 24

| ID | Связанное требование | Уровень пирамиды | Вид | Предусловие | Шаги | Ожидаемый результат | Источник |
|---|---|---|---|---|---|---|---|
| TC-P3-01 | R-P3-GW | unit | негативный | TransactionRequestValidator, запрос Q | Проверить MCC 541, 54A1, 54111; затем 5411 | Первые три значения отклонены с TransactionValidationException; 5411 принят | Класс эквивалентности и границы длины MCC; [GW-VAL] |
| TC-P3-02 | R-P3-BIN | unit | позитивный | Таблица BIN: 400000→ISS001, 400001→ISS002 | Вызвать getIssuerIdByPan для двух PAN с BIN 400000 и разными последними цифрами; затем с BIN 400001 | Первые два дают ISS001, третий — ISS002 | Класс эквивалентности: первые 6 цифр PAN; [ROUTING] |
| TC-P3-03 | R-P3-BIN | unit | негативный | RouteService с настоящим RoutingService; остальные зависимости заменены заглушками; Logger возвращает true | Вызвать route(Q) с неизвестным BIN 499999 | DECLINED/14; вызова Authorization и расчёта комиссии нет; отказ передан в Logger | Класс эквивалентности: неизвестный BIN; [ROUTE] |
| TC-P3-04 | R-C4 | unit | негативный | Объект Card, без БД | Вызвать withData с daily=200, monthly=199; затем daily=-1, monthly=200 | IllegalArgumentException; исходная карта не меняется | Граничное значение: daily больше monthly на 1 и отрицательный лимит; [CARD] |
| TC-P3-05 | R-C8 | unit | позитивный | ACTIVE-карта, баланс 100, RRN из 12 цифр | Вызвать startReservation(100, rrn), затем withReservation | Резерв RESERVED на 100; баланс новой карты равен 0 | Граничное значение: сумма равна балансу; [CARD], [RESERVATION] |
| TC-P3-06 | R-A2, R-C8 | unit | негативный | Карта INACTIVE, BLOCKED, EXPIRED или DELETED; баланс 100 | Для каждого статуса вызвать startReservation(1, rrn) | IllegalStateException; резерв не создан | Класс эквивалентности: неактивные карты; [CARD] |
| TC-P3-07 | R-A4, R-A6 | unit | негативный | AuthServiceImpl; заглушки CMS и BIN; карта ACTIVE, balance=0, dailyLimit=0; репозиторий подменён | Вызвать authorize с amount=1 | DECLINED/51; обновление лимитов и резервирование не вызываются | Сценарий: одновременно не хватает денег и лимита; [AUTH] проверяет баланс первым |
| TC-P3-08 | R-C8 | unit | негативный | Резерв уже ROLLED_BACK | Повторно вызвать startRollback | RollbackAlreadySatisfiedException; второго возврата нет | Сценарий: повторный откат; [RESERVATION] |
| TC-P3-09 | R-P3-OUTBOX | unit | негативный | Событие PENDING, retryCount=0, максимум 3 ошибки; заглушка RabbitTemplate всегда вызывает AmqpException | Трижды вызвать processSingleEvent, каждый раз передавая сохранённое состояние | Состояния PENDING/1, PENDING/2, FAILED/3; записана ошибка | Граничное значение: третья ошибка; [OUTBOX] |
| TC-P3-10 | R-P3-CONFIRM | unit | негативный | RouteService; заглушка авторизации даёт APPROVED и RRN; отправка в Logger возвращает false; откат успешен | Вызвать route(Q) | Откат вызван один раз с исходными PAN, суммой и RRN; DECLINED/96; повторной отправки нет | Сценарий: ошибка отправки после одобрения; [ROUTE] |
| TC-P3-11 | R-P3-GW | api | негативный | HTTP Gateway; Switch заменён заглушкой | POST /api/transactions с Q и mti=0400 | HTTP 400, error=VALIDATION_ERROR; Switch не вызван | Контракт MTI; [GW-VAL], [GW-FILTER] |
| TC-P3-12 | R-P3-OUTBOX | api | позитивный | NotificationController; заглушка репозитория возвращает страницу из двух уведомлений | GET /api/notifications?page=1&size=2 | HTTP 200; номер страницы 1, размер 2; есть eventType, routingKey, payload; сортировка по receivedAt убывающая | Контракт страницы уведомлений; [NOTIFICATION-API] |
| TC-P3-13 | R-A2, R-A6 | api | негативный | AuthControllerImpl; AuthService заменён заглушкой | POST /api/internal/authorize с Q; отдельно задать ответы CARD_BLOCKED/05 и INSUFFICIENT_FUNDS/51 | Для BLOCKED — HTTP 403, для нехватки денег — 422; тело содержит DECLINED и нужные причину и код | Контракт HTTP-ответа; [AUTH-API], [DECLINES] |
| TC-P3-14 | R-C1, R-C4, R-C5 | api | позитивный | CardController с настоящим преобразованием данных; CardService заменён заглушкой с готовой картой | Отправить POST /api/cards, PATCH /api/cards/{pan}, DELETE /api/cards/{pan} | Статусы 201, 200, 204; POST/PATCH возвращают карту, DELETE — пустое тело; поля переданы сервису | Контракт создания, изменения и удаления; [CARD-API] |
| TC-P3-15 | R-P3-GW | api | негативный | HTTP Gateway; Switch заменён заглушкой | POST /api/transactions с Content-Type application/json и оборванным телом `{"mti":` | HTTP 400, error=VALIDATION_ERROR, сообщение об ошибке JSON; Switch не вызван | Класс эквивалентности: неверный JSON; [GW-FILTER] |
| TC-P3-16 | R-A4, R-A5 | integration | позитивный | PostgreSQL и LimitUsageRepository; на 21.10 использовано daily=900/monthly=900; лимиты 1000/1000 | Выполнить upsert на 22.10 с amount=100; затем с amount=1 | Первая запись: daily=100, monthly=1000; вторая операция не обновляет строку | Состояние БД и граница месячного лимита при смене дня; [LIMITS] |
| TC-P3-17 | R-C8 | integration | позитивный | CMS и PostgreSQL; ACTIVE-карта, баланс 100; два разных RRN | Одновременно отправить два POST reserve по 80; проверить баланс и резервы | Один ответ 200, другой 402; баланс 20; один резерв | Сценарий: одновременное резервирование; [CARD-JPA], [CARD-SERVICE] |
| TC-P3-18 | R-P3-LOG | integration | позитивный | RabbitMQ, Logger и PostgreSQL; обработчик очереди остановлен; сообщение с новым UUID | Отправить сообщение в smp.transactions/transaction.log; проверить отсутствие записи; включить обработчик; дождаться записи; отправить то же сообщение повторно и дождаться обработки | В БД одна запись с этим UUID; повтор не создаёт дубль и повторное WebSocket-сообщение | Сценарий повторной доставки и состояние БД; [LOG-LISTENER], [LOG-SERVICE] |
| TC-P3-19 | R-P3-OUTBOX, R-C4 | integration | негативный | CMS и PostgreSQL; карта ACTIVE; отправка outbox выключена; запись нового события в outbox_event принудительно завершается ошибкой | PATCH status=BLOCKED; проверить карту и outbox | Запрос неуспешен; карта остаётся ACTIVE, новое событие не сохранено | Состояние БД: ошибка сохранения события при PATCH; [CARD-SERVICE], [TX], [NOTIFIER] |
| TC-P3-20 | R-P3-OUTBOX | integration | позитивный | CMS, Notification, PostgreSQL и RabbitMQ; новая карта; обработчик outbox запускается вручную | PATCH status=BLOCKED; проверить PENDING; запустить обработчик; дождаться уведомления через :8097/api/notifications | Outbox PROCESSED; новое уведомление содержит ключ card.CardServicePatchEvent и PAN карты | Сценарий outbox → Notification и состояние БД; [POLLER], [OUTBOX], [NOTIFICATION] |
| TC-P3-21 | R-P3-CONFIRM | integration | негативный | Switch, Authorization, CMS, PostgreSQL и RabbitMQ; посредник между Switch и брокером задерживает ACK, но пропускает отправку сообщения | Отправить Q; задержать ACK больше 2 с; проверить ответ и резерв. Повторить с NACK | По ТЗ: при NACK или после 2 с без ACK начинается откат; DECLINED/96, резерв ROLLED_BACK, баланс восстановлен | Контракт подтверждения и сценарий отказа; [TZ-SW], [PUBLISH], [ROUTE] |
| TC-P3-22 | R-P3-DLQ | integration | негативный | RabbitMQ 3.13 с настройками проекта; обработчик Logger при получении сообщения выдаёт ошибку и возвращает его в очередь; без дополнительных настроек брокера | Отправить сообщение; посчитать попытки; проверить DLQ transaction-log-dlq; после попадания туда подождать больше 60 с. После 10 повторов без DLQ прекратить тест с ошибкой | По ТЗ: после трёх попыток сообщение в DLQ, затем удаляется по TTL 60000 мс | Сценарий ошибки обработки и граница TTL; [MQ-SW], [MQ-LOG], [ARCH-DOC] |
| TC-P3-23 | R-P3-OUTBOX | integration | позитивный | CMS, Notification, PostgreSQL и RabbitMQ; автоматическая отправка выключена; первая отправка недоступна, максимум 3 ошибки | PATCH карты; вручную запустить отправку при недоступном брокере; восстановить брокер до второй попытки; повторить отправку | После ошибки PENDING/1; после повтора PROCESSED; приходит уведомление с PAN карты | Сценарий восстановления доставки; [POLLER], [OUTBOX], [NOTIFICATION] |
| TC-P3-24 | R-A7, R-P3-LOG, R-P3-UI | e2e | позитивный | Полный стенд; базовая карта; Dashboard открыт, WebSocket подключён; исходные баланс и лимиты записаны | Через Gateway отправить Q с уникальным STAN; получить RRN; ждать запись в /api/transactions/search?pan={pan}, сообщение WebSocket и строку Dashboard с тем же RRN | HTTP 200 APPROVED/00; баланс уменьшен на 10000, использованные лимиты выросли на 10000; операция появилась в журнале и Dashboard за время ожидания теста | Сквозной сценарий и доставка с задержкой; [ROUTE], [LOG-SERVICE], [NGINX], [WS] |

## (в) Распределение кейсов практики 2

### Основные и дополнительные кейсы

| ID (пр. 2) | Название | Что проверяет тест | Уровень | Обоснование |
|---|---|---|---|---|
| TC-01 | Успешная транзакция по активной карте | Одобрение, реальное уменьшение баланса, рост использованных лимитов и генерация RRN/authCode | integration | Проверяются настоящий резерв и изменения лимитов в PostgreSQL. |
| TC-02 | Транзакция по неактивной карте | Ветка INACTIVE: CARD_INACTIVE, запрет резерва и изменения лимитов | unit | Достаточно заданной карты и проверки решения AuthServiceImpl. |
| TC-03 | Транзакция по заблокированной карте | Ветка BLOCKED: CARD_BLOCKED, запрет резерва и изменения лимитов | unit | Логику отказа можно проверить без HTTP и БД. |
| TC-04 | Транзакция по карте с истёкшим статусом | Статус EXPIRED даёт код 54 без резервирования | unit | Для проверки статуса EXPIRED не нужна БД. |
| TC-05 | Карта не найдена в системе | CardNotFoundException от CMS преобразуется в DECLINED/14 | unit | Клиент CMS возвращает заданную ошибку; проверяется её обработка. |
| TC-06 | Истёкший срок действия карты | expiryDate раньше даты операции даёт 54 без изменений | unit | Сравнение дат и отсутствие вызовов изменения данных проверяются в памяти. |
| TC-07 | Сумма ровно в пределах дневного лимита | 990000 + 10000 = 1000000 допускается и сохраняется | integration | Граница лимита проверяется SQL-запросом в PostgreSQL. |
| TC-08 | Превышение дневного лимита на 1 копейку | 990000 + 10001 отвергается, использованный лимит не изменён | integration | Нужно проверить SQL-условие и неизменность данных БД. |
| TC-09 | Превышение месячного лимита | 4990000 + 10001 > 5000000 даёт 61 при доступном дневном лимите | integration | Проверяется накопленная месячная сумма в БД. |
| TC-10 | Сумма равна доступному балансу | Одобрение и сохранение нулевого остатка после reserve | integration | Нужно проверить сохранение нулевого баланса после резерва. |
| TC-11 | Сумма превышает баланс на 1 копейку | Проверка amount > availableBalance даёт 51, reserve не вызывается | unit | Проверяется сравнение суммы с балансом и отсутствие вызова reserve. |
| TC-12 | Card Management недоступен | HTTP-клиент и обработка недоступного CMS | integration | Нужен настоящий HTTP-клиент и недоступный адрес CMS. |
| TC-13 | Создание карты | POST создаёт карту, GET читает сохранённый PAN, PAN проходит Луна, срок корректен | integration | Созданную карту нужно прочитать из настоящей БД. |
| TC-14 | Блокировка карты и отклонение транзакции | PATCH BLOCKED → GET → отказ транзакции через Gateway | e2e | Проверяется путь от блокировки до отказа транзакции через Gateway. |
| TC-15 | Мягкое удаление карты | DELETE → GET 404 → невозможность провести транзакцию | e2e | Проверяется удаление и последующий отказ через несколько сервисов. |
| TC-16 | Генерация тестовых карт | 20 карт по двум BIN, валидные PAN, monthly=daily×30, чтение списка | integration | Нужно проверить создание карт и чтение списка из БД. |
| TC-17 | Резервирование на весь баланс | резервирование через HTTP сохраняет нулевой остаток | integration | Баланс и резерв должны сохраниться в PostgreSQL. |
| TC-18 | Нулевая сумма авторизации | amount=0 отклоняется на входном HTTP-слое | api | Проверяется входной запрос Gateway; БД не нужна. |
| TC-19 | Отрицательная сумма авторизации | amount=-1 отклоняется валидатором запроса | api | Проверяется допустимость суммы в HTTP-запросе. |
| TC-20 | Нечисловая сумма авторизации | amount="abc" даёт ошибку чтения JSON/400 | api | Нужно проверить чтение JSON и ответ HTTP 400. |
| TC-21 | BIN из пяти цифр | Создание карты с bin=40000 даёт 400 | api | Проверяется длина BIN до вызова сервиса. |
| TC-22 | BIN с буквами | Создание карты с bin=ABCDEF даёт 400 | api | Проверяется формат BIN в запросе. |
| TC-23 | Пустое имя держателя | cardholderName="" даёт 400 | api | Пустое имя проверяется при приёме запроса. |
| TC-24 | Буквенный код валюты | currencyCode=RUB даёт 400 | api | Проверяется числовой формат кода валюты. |
| TC-25 | Отрицательный дневной лимит | dailyLimit=-1 даёт 400 | api | Проверяется допустимость лимита в запросе. |
| TC-26 | Offset за концом списка | offset=total+1 возвращает пустой cards при прежнем total | integration | Нужны реальные данные списка и подсчёт строк в БД. |
| TC-27 | Фильтрация списка по BLOCKED | Среди разных карточных статусов возвращаются только BLOCKED | integration | Проверяется отбор карт запросом к БД. |
| TC-28 | Неизвестный статус фильтра | status=HACKED даёт 400 | api | Проверяется обработка неизвестного значения статуса. |
| TC-29 | PATCH несуществующей карты | Отсутствующий PAN преобразуется в HTTP 404 | api | Заглушка сообщает, что карта не найдена; проверяется HTTP 404. |
| TC-30 | PATCH с неизвестным статусом | status=FROZEN даёт 400 | api | Неизвестный статус отклоняется при чтении JSON. |
| TC-31 | Повторное удаление карты | После первого DELETE карта скрыта, второй DELETE даёт 404 | integration | Проверяется поиск карты после сохранения DELETED в БД. |
| TC-32 | Генерация нуля карт | count=0 даёт 400 | api | Нулевое количество отклоняется при приёме запроса. |
| TC-33 | Генерация с пустым списком BIN | bins=[] даёт 400 | api | Проверяется пустой список BIN в запросе. |
| TC-34 | Резерв сверх баланса | amount=B+1 даёт 402, баланс БД не меняется | integration | Нужно проверить ошибку и неизменность баланса в БД. |
| TC-35 | Короткий RRN резерва | rrn=123 даёт 400 | api | Проверяется формат RRN до резервирования. |
| TC-36 | Срок созданной карты | Созданный expiryDate в формате MMYY и на 3 года позже | integration | Нужно проверить срок, который назначен при создании карты. |
| TC-37 | PAN длиной 15 и 17 цифр | GET отклоняет обе невалидные длины с 400 | api | Проверяется допустимая длина PAN в HTTP-запросе. |

### Попарные проверки Authorization

| ID (пр. 2) | Название | Что проверяет тест | Уровень | Обоснование |
|---|---|---|---|---|
| TC-PW-001 | EXPIRED; amount=1; POS; MCC 5732 | Статус EXPIRED даёт отказ 54. | integration | Проверяется ответ Authorization по данным карты из БД. |
| TC-PW-002 | BLOCKED; amount=1; ECOM; MCC 5812 | Заблокированная карта даёт отказ CARD_BLOCKED. | integration | HTTP-авторизация с настоящей картой из БД. |
| TC-PW-003 | INACTIVE; amount=1; ATM; MCC 5812 | Неактивная карта даёт отказ CARD_INACTIVE. | integration | HTTP-авторизация с настоящей картой из БД. |
| TC-PW-004 | BLOCKED; amount=1; ATM; MCC 5411 | Заблокированная карта даёт отказ CARD_BLOCKED. | integration | HTTP-авторизация с настоящей картой из БД. |
| TC-PW-005 | ACTIVE; amount=15000000; ECOM; MCC 5732 | Недостаточный баланс даёт отказ 51. | integration | Проверяется ответ по балансу настоящей карты. |
| TC-PW-006 | ACTIVE; amount=15000001; POS; MCC 5411 | Превышение лимита даёт отказ 61. | integration | Порог лимита рассчитывается SQL-запросом. |
| TC-PW-007 | ACTIVE; amount=15000001; ATM; MCC 5812 | Недостаточный баланс даёт отказ 51. | integration | Проверяется ответ по балансу настоящей карты. |
| TC-PW-008 | EXPIRED; amount=1; ATM; MCC 5411 | Статус EXPIRED даёт отказ 54. | integration | Проверяется ответ Authorization по данным карты из БД. |
| TC-PW-009 | ACTIVE; amount=15000001; ECOM; MCC 5732 | Превышение лимита даёт отказ 61. | integration | Порог лимита рассчитывается SQL-запросом. |
| TC-PW-010 | ACTIVE; amount=1; ATM; MCC 5732 | При допустимых сумме, балансе и лимитах операция APPROVED/00. | integration | Нужны SQL-проверка лимитов и резервирование в CMS. |
| TC-PW-011 | INACTIVE; amount=1; POS; MCC 5812 | Неактивная карта даёт отказ CARD_INACTIVE. | integration | HTTP-авторизация с настоящей картой из БД. |
| TC-PW-012 | INACTIVE; amount=1; ECOM; MCC 5411 | Неактивная карта даёт отказ CARD_INACTIVE. | integration | HTTP-авторизация с настоящей картой из БД. |
| TC-PW-013 | ACTIVE; amount=15000000; POS; MCC 5812 | Недостаточный баланс даёт отказ 51. | integration | Проверяется ответ по балансу настоящей карты. |
| TC-PW-014 | BLOCKED; amount=1; POS; MCC 5732 | Заблокированная карта даёт отказ CARD_BLOCKED. | integration | HTTP-авторизация с настоящей картой из БД. |
| TC-PW-015 | ACTIVE; amount=15000000; ECOM; MCC 5812 | При допустимых сумме, балансе и лимитах операция APPROVED/00. | integration | Нужны SQL-проверка лимитов и резервирование в CMS. |
| TC-PW-016 | ACTIVE; amount=15000000; ATM; MCC 5411 | При допустимых сумме, балансе и лимитах операция APPROVED/00. | integration | Нужны SQL-проверка лимитов и резервирование в CMS. |
| TC-PW-017 | INACTIVE; amount=1; POS; MCC 5732 | Неактивная карта даёт отказ CARD_INACTIVE. | integration | HTTP-авторизация с настоящей картой из БД. |
| TC-PW-018 | EXPIRED; amount=1; ECOM; MCC 5812 | Статус EXPIRED даёт отказ 54. | integration | Проверяется ответ Authorization по данным карты из БД. |
| TC-PW-019 | ACTIVE; amount=1; POS; MCC 5411 | Истёкший срок при ACTIVE даёт отказ 54. | integration | Проверяется ответ Authorization по данным карты из БД. |
| TC-PW-020 | ACTIVE; amount=1; POS; MCC 5411 | Недостаточный баланс даёт отказ 51. | integration | Проверяется ответ по балансу настоящей карты. |
| TC-PW-021 | ACTIVE; amount=15000001; POS; MCC 5812 | Недостаточный баланс даёт отказ 51. | integration | Проверяется ответ по балансу настоящей карты. |

### Ручные сочетания параметров

| ID (пр. 2) | Название | Что проверяет тест | Уровень | Обоснование |
|---|---|---|---|---|
| PW-MAN-01 | Все денежные границы равны | amount=dailyLimit=monthlyLimit=balance=15000000 → APPROVED/00 | integration | Нужно проверить лимиты БД и резервирование CMS. |
| PW-MAN-02 | Нулевой баланс при доступных лимитах | amount=1, balance=0 → DECLINED/51 | integration | Сохраняется исходная проверка с настоящей картой из БД. |
| PW-MAN-03 | Неактивная карта, превышены лимит и баланс | Статус INACTIVE имеет приоритет; CARD_INACTIVE, резерв и изменение лимитов не вызываются | unit | Порядок проверок AuthServiceImpl можно проверить с заданной картой. |
| PW-MAN-04 | PAN валиден по формату, карта отсутствует | Отсутствующая карта даёт DECLINED/14 | api | CMS заменён заглушкой с ответом 404; проверяется HTTP-ответ Authorization. |

### Попарные проверки Card Management

| ID (пр. 2) | Название | Что проверяет тест | Уровень | Обоснование |
|---|---|---|---|---|
| CM-PW-01 | Создание: BIN 400004, 643, IVAN IVANOV | Создание: HTTP 201, ISS005, PAN проходит Луна, ACTIVE, срок +3 года. | integration | Проверяются справочник BIN и сохранение карты в PostgreSQL. |
| CM-PW-02 | Создание: BIN 400000, 978, ANNA SMIRNOVA | Создание: HTTP 201, ISS001, PAN проходит Луна, ACTIVE, срок +3 года. | integration | Проверяются справочник BIN и сохранение карты в PostgreSQL. |
| CM-PW-03 | Создание: BIN 400004, 840, IVAN IVANOV | Создание: HTTP 201, ISS005, PAN проходит Луна, ACTIVE, срок +3 года. | integration | Проверяются справочник BIN и сохранение карты в PostgreSQL. |
| CM-PW-04 | Создание: BIN 400001, 840, ANNA SMIRNOVA | Создание: HTTP 201, ISS002, PAN проходит Луна, ACTIVE, срок +3 года. | integration | Проверяются справочник BIN и сохранение карты в PostgreSQL. |
| CM-PW-05 | Создание: BIN 400001, 643, IVAN IVANOV | Создание: HTTP 201, ISS002, PAN проходит Луна, ACTIVE, срок +3 года. | integration | Проверяются справочник BIN и сохранение карты в PostgreSQL. |
| CM-PW-06 | Создание: BIN 400001, 978, ANNA SMIRNOVA | Создание: HTTP 201, ISS002, PAN проходит Луна, ACTIVE, срок +3 года. | integration | Проверяются справочник BIN и сохранение карты в PostgreSQL. |
| CM-PW-07 | Создание: BIN 400004, 840, ANNA SMIRNOVA | Создание: HTTP 201, ISS005, PAN проходит Луна, ACTIVE, срок +3 года. | integration | Проверяются справочник BIN и сохранение карты в PostgreSQL. |
| CM-PW-08 | Создание: BIN 400001, 978, IVAN IVANOV | Создание: HTTP 201, ISS002, PAN проходит Луна, ACTIVE, срок +3 года. | integration | Проверяются справочник BIN и сохранение карты в PostgreSQL. |
| CM-PW-09 | Создание: BIN 400000, 643, IVAN IVANOV | Создание: HTTP 201, ISS001, PAN проходит Луна, ACTIVE, срок +3 года. | integration | Проверяются справочник BIN и сохранение карты в PostgreSQL. |
| CM-PW-10 | Создание: BIN 400000, 840, IVAN IVANOV | Создание: HTTP 201, ISS001, PAN проходит Луна, ACTIVE, срок +3 года. | integration | Проверяются справочник BIN и сохранение карты в PostgreSQL. |
| CM-PW-11 | Создание: BIN 400004, 643, ANNA SMIRNOVA | Создание: HTTP 201, ISS005, PAN проходит Луна, ACTIVE, срок +3 года. | integration | Проверяются справочник BIN и сохранение карты в PostgreSQL. |
| CM-PW-12 | Создание: BIN 400004, 978, ANNA SMIRNOVA | Создание: HTTP 201, ISS005, PAN проходит Луна, ACTIVE, срок +3 года. | integration | Проверяются справочник BIN и сохранение карты в PostgreSQL. |

## (г) Пропорции старых и новых кейсов

| Уровень | Старые | Новые | Итого | Доля |
|---|---:|---:|---:|---:|
| unit | 7 | 10 | 17 | 17.35% |
| api / integration | 65 | 13 | 78 | 79.59% |
| e2e | 2 | 1 | 3 | 3.06% |
| **Итого** | **74** | **24** | **98** | **100%** |

API и integration преобладают, потому что система работает с PostgreSQL и RabbitMQ: нужно проверять сохранение данных и доставку сообщений. На доли также влияет количество попарных кейсов. Перекоса в E2E (ice-cream cone) нет — таких кейсов только три. Проверки HTTP-контрактов представлены 21 API-кейсом, а unit-кейсы проверяют отдельные правила и решения методов.


[GW-VAL]: ../../services/gateway/src/main/java/com/processing/gateway/validation/TransactionRequestValidator.java
[GW-FILTER]: ../../services/gateway/src/main/java/com/processing/gateway/validation/TransactionValidationFilter.java
[ROUTING]: ../../services/switch/src/main/java/com/processing/service/RoutingService.java
[ROUTE]: ../../services/switch/src/main/java/com/processing/service/RouteService.java
[PUBLISH]: ../../services/switch/src/main/java/com/processing/service/LoggerClient.java
[CARD]: ../../services/card-management/src/main/java/com/processing/cardmanagement/models/Card.java
[RESERVATION]: ../../services/card-management/src/main/java/com/processing/cardmanagement/models/Reservation.java
[AUTH]: ../../services/authorization/src/main/java/com/processing/authorization/services/AuthServiceImpl.java
[AUTH-API]: ../../services/authorization/src/main/java/com/processing/authorization/controller/AuthControllerImpl.java
[DECLINES]: ../../services/authorization/src/main/java/com/processing/authorization/constants/DeclineOutcome.java
[OUTBOX]: ../../services/card-management/src/main/java/com/processing/cardmanagement/services/OutboxEventProcessorImpl.java
[POLLER]: ../../services/card-management/src/main/java/com/processing/cardmanagement/services/OutboxProcessor.java
[NOTIFIER]: ../../services/card-management/src/main/java/com/processing/cardmanagement/events/CardEventNotifierImpl.java
[NOTIFICATION]: ../../services/notification-service/src/main/java/com/processing/notification/listener/CardEventListener.java
[NOTIFICATION-API]: ../../services/notification-service/src/main/java/com/processing/notification/controller/NotificationController.java
[CARD-API]: ../../services/card-management/src/main/java/com/processing/cardmanagement/controllers/CardController.java
[CARD-SERVICE]: ../../services/card-management/src/main/java/com/processing/cardmanagement/services/CardServiceImpl.java
[CARD-JPA]: ../../services/card-management/src/main/java/com/processing/cardmanagement/repositories/CardJpaRepository.java
[TX]: ../../services/card-management/src/main/java/com/processing/cardmanagement/services/TransactionRunnerImpl.java
[LIMITS]: ../../services/authorization/src/main/java/com/processing/authorization/repositories/LimitUsageRepository.java
[LOG-LISTENER]: ../../services/transaction-logger/src/main/java/com/processing/transactionlogger/listener/TransactionLogListener.java
[LOG-SERVICE]: ../../services/transaction-logger/src/main/java/com/processing/transactionlogger/service/TransactionService.java
[MQ-SW]: ../../services/switch/src/main/java/com/processing/config/RabbitMQConfig.java
[MQ-LOG]: ../../services/transaction-logger/src/main/java/com/processing/transactionlogger/config/RabbitMQConfig.java
[TZ-SW]: ../../tz/03-switch.md
[ARCH-DOC]: ../../docs/architecture.md
[NGINX]: ../../services/dashboard/nginx.conf
[WS]: ../../services/dashboard/src/hooks/useWebSocket.ts
