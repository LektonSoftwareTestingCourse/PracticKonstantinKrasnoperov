# Test-design ядра СМП: Authorization + Card Management

## Тестовые сценарии (тест-кейсы)

### Авторизация: статус карты

**TC-01. Успешная транзакция по активной карте** — проверяет статусы карты и успешную авторизацию (R-A2, R-A7)

Предусловие:
1. Карта заведена в системе
2. На карте положительный баланс 1000,00 руб
3. Карта активна
4. По этой карте операций ещё не было

Параметры: статус карты — ACTIVE; сумма транзакции — 900,03 руб; тип терминала — POS; MCC — grocery

Шаги теста:
1. Проводим транзакцию

Ожидаемый результат:
1. Операция успешна, responseCode «00»
2. Средства зарезервированы на карте
3. Доступный баланс уменьшился на сумму авторизации (1000,00 − 900,03 = 99,97 руб)
4. Использованный дневной лимит увеличился на сумму авторизации
5. Использованный месячный лимит увеличился на сумму авторизации
6. RRN — 12 цифр, уникален; authCode — 6 символов A–Z0–9

**TC-02. Транзакция по неактивной карте** — проверяет статусы карты (R-A2)

Предусловие:
1. Карта заведена в системе
2. Статус карты — INACTIVE
3. Баланс и лимиты достаточны

Параметры: статус — INACTIVE; сумма — 100,00 руб; терминал — POS; MCC — grocery

Шаги теста:
1. Проводим транзакцию

Ожидаемый результат:
1. Операция отклонена, declineReason CARD_INACTIVE
2. Баланс и лимиты не изменились

**TC-03. Транзакция по заблокированной карте** — проверяет статусы карты (R-A2)

Предусловие:
1. Статус карты — BLOCKED
2. Баланс и лимиты достаточны

Параметры: статус — BLOCKED; сумма — 100,00 руб; терминал — ATM; MCC — grocery

Шаги теста:
1. Проводим транзакцию

Ожидаемый результат:
1. Операция отклонена, declineReason CARD_BLOCKED
2. Баланс и лимиты не изменились

**TC-04. Транзакция по карте с истёкшим статусом** — проверяет статусы карты (R-A2)

Предусловие:
1. Статус карты — EXPIRED

Параметры: статус — EXPIRED; сумма — 100,00 руб; терминал — POS; MCC — restaurant

Шаги теста:
1. Проводим транзакцию

Ожидаемый результат:
1. Операция отклонена, responseCode «54»
2. Баланс и лимиты не изменились

**TC-05. Карта не найдена в системе** — проверяет поиск карты по PAN (R-A1)

Предусловие:
1. PAN не существует в CMS (валидный по Луну, не заведён)

Параметры: PAN — несуществующий; сумма — 100,00 руб

Шаги теста:
1. Проводим транзакцию

Ожидаемый результат:
1. Операция отклонена, responseCode «14»

### Авторизация: срок действия

**TC-06. Истёкший срок действия карты** — проверяет срок действия (R-A3)

Предусловие:
1. Карта активна (ACTIVE)
2. expiryDate — прошлый месяц (MMYY)

Параметры: сумма — 100,00 руб; терминал — POS

Шаги теста:
1. Проводим транзакцию

Ожидаемый результат:
1. Операция отклонена, responseCode «54»
2. Баланс и лимиты не изменились

### Авторизация: лимиты и баланс

**TC-07. Сумма ровно в пределах дневного лимита** — проверяет дневной лимит (R-A4)

Предусловие:
1. Карта активна, баланс достаточен
2. Использовано за день 9 900,00 руб; дневной лимит 10 000,00 руб

Параметры: сумма — 100,00 руб (использовано 9 900 + 100 = ровно 10 000, упираемся в лимит точно)

Шаги теста:
1. Проводим транзакцию

Ожидаемый результат:
1. Операция успешна, responseCode «00»
2. Использованный дневной лимит стал равен дневному лимиту

**TC-08. Превышение дневного лимита на 1 копейку** — проверяет дневной лимит (R-A4)

Предусловие:
1. Использовано за день 9 900,00 руб; дневной лимит 10 000,00 руб

Параметры: сумма — 100,01 руб (на 1 копейку больше, чем осталось до лимита)

Шаги теста:
1. Проводим транзакцию

Ожидаемый результат:
1. Операция отклонена, responseCode «61»
2. Использованный дневной лимит не изменился

**TC-09. Превышение месячного лимита** — проверяет месячный лимит (R-A5)

Предусловие:
1. Использовано за месяц 49 900,00 руб; месячный лимит 50 000,00 руб
2. Дневной лимит не превышен

Параметры: сумма — 100,01 руб

Шаги теста:
1. Проводим транзакцию

Ожидаемый результат:
1. Операция отклонена, responseCode «61»

**TC-10. Сумма равна доступному балансу** — проверяет баланс (R-A6)

Предусловие:
1. Карта активна, лимиты не превышены
2. Доступный баланс 500,00 руб

Параметры: сумма — 500,00 руб (ровно весь баланс)

Шаги теста:
1. Проводим транзакцию

Ожидаемый результат:
1. Операция успешна, responseCode «00»
2. Доступный баланс стал 0,00 руб

**TC-11. Сумма превышает баланс на 1 копейку** — проверяет баланс (R-A6)

Предусловие:
1. Доступный баланс 500,00 руб, лимиты не превышены

Параметры: сумма — 500,01 руб

Шаги теста:
1. Проводим транзакцию

Ожидаемый результат:
1. Операция отклонена, responseCode «51»
2. Баланс не изменился

### Авторизация: доступность зависимостей

**TC-12. Card Management недоступен** — проверяет поведение при недоступности CMS (R-A8)

Предусловие:
1. Карта активна, баланс и лимиты достаточны
2. Контейнер card-management остановлен

Шаги теста:
1. Останавливаем card-management
2. Проводим транзакцию
3. Запускаем card-management обратно

Ожидаемый результат:
1. Операция отклонена, responseCode «05», причина ISSUER_TIMEOUT
2. После восстановления CMS авторизация по той же карте проходит успешно

### Управление картами: CRUD

**TC-13. Создание карты** — проверяет создание, получение по PAN и корректность номера (R-C1, R-C2, R-C7)

Предусловие:
1. Система запущена, БД доступна

Параметры: bin — 400000; владелец — IVAN IVANOV; валюта — 643; дневной лимит — 1 500,00 руб; месячный лимит — 3 000,00 руб; стартовый баланс — 1 000,00 руб

Шаги теста:
1. POST /api/cards с параметрами
2. GET /api/cards/{pan}

Ожидаемый результат:
1. Карта создана: PAN 16 цифр, проходит проверку Луна; expiryDate = текущая дата + 3 года (MMYY); статус ACTIVE
2. GET возвращает созданную карту с тем же PAN

**TC-14. Блокировка карты и отклонение транзакции** — проверяет обновление карты (R-C4)

Предусловие:
1. Карта активна, баланс и лимиты достаточны

Шаги теста:
1. PATCH /api/cards/{pan} со значением status=BLOCKED
2. GET /api/cards/{pan}
3. Проводим транзакцию

Ожидаемый результат:
1. PATCH отвечает 200
2. Статус карты — BLOCKED
3. Транзакция отклонена, declineReason CARD_BLOCKED

**TC-15. Мягкое удаление карты** — проверяет удаление карты (R-C5)

Предусловие:
1. Карта существует и активна

Шаги теста:
1. DELETE /api/cards/{pan}
2. GET /api/cards/{pan}
3. Проводим транзакцию по этой карте

Ожидаемый результат:
1. DELETE отвечает 200
2. GET возвращает 404
3. Транзакция отклонена

### Управление картами: генерация и резервирование

**TC-16. Генерация тестовых карт** — проверяет генератор и список карт (R-C3, R-C6)

Параметры: count — 20; bins — ["400000","400001"]

Шаги теста:
1. POST /api/cards/generate
2. GET /api/cards?limit=50

Ожидаемый результат:
1. Создано ровно 20 карт; распределение по двум BIN примерно равное
2. Все PAN проходят Луна; месячный лимит = дневной × 30
3. Список возвращает созданные карты

**TC-17. Резервирование на весь баланс** — проверяет резервирование средств (R-C8)

Предусловие:
1. Карта активна, доступный баланс 1 000,00 руб, RRN — 12 цифр

Параметры: amount — 1 000,00 руб (весь баланс)

Шаги теста:
1. POST /api/cards/{pan}/reserve

Ожидаемый результат:
1. Ответ 200
2. Доступный баланс стал 0,00 руб


## Требования и их покрытие

Что обязана делать система (по ТЗ) и какими кейсами это закрыто.

R-A — требования к Authorization

R-C — к Card Management.

| Бирка | Что проверяем | Где в ТЗ | Кейсы |
|---|---|---|---|
| R-A1 | Карта находится по PAN; ненайденная — отказ «14» | ТЗ Authorization, алгоритм, шаг 1 | TC-05 |
| R-A2 | Статусы карты: решение только для ACTIVE | ТЗ Authorization, алгоритм, шаг 2 | TC-01, TC-02, TC-03, TC-04 |
| R-A3 | Срок действия: прошлый месяц — отказ «54» | ТЗ Authorization, алгоритм, шаг 3 | TC-06 |
| R-A4 | Дневной лимит: сверх — отказ «61» | ТЗ Authorization, алгоритм, шаг 4; учёт лимитов | TC-07, TC-08 |
| R-A5 | Месячный лимит: сверх — отказ «61» | ТЗ Authorization, алгоритм, шаг 5; учёт лимитов | TC-09 |
| R-A6 | Баланс: сумма не больше доступного, иначе «51» | ТЗ Authorization, алгоритм, шаг 6 | TC-10, TC-11 |
| R-A7 | Успех: резервирование, RRN 12 цифр, authCode 6 символов | ТЗ Authorization, алгоритм, шаг 7; генерация идентификаторов | TC-01, TC-10 |
| R-A8 | CMS недоступна — отказ «05», ISSUER_TIMEOUT | ТЗ Authorization, обработка ошибок | TC-12 |
| R-C1 | Создание карты: PAN по Луну, срок +3 года, статус ACTIVE | ТЗ Card Management, CRUD карт | TC-13 |
| R-C2 | Получение карты по PAN: 200 / 404 | ТЗ Card Management, CRUD карт | TC-13, TC-15 |
| R-C3 | Список карт с пагинацией и фильтрами | ТЗ Card Management, список карт | TC-16 |
| R-C4 | Частичное обновление карты (PATCH) | ТЗ Card Management, CRUD карт | TC-14 |
| R-C5 | Мягкое удаление: карта исчезает из GET и транзакций | ТЗ Card Management, CRUD карт | TC-15 |
| R-C6 | Генератор карт: распределение по BIN, статусы 95/3/2 | ТЗ Card Management, генератор тестовых карт | TC-16 |
| R-C7 | Сгенерированный PAN проходит алгоритм Луна | ТЗ Card Management, алгоритм Луна | TC-13, TC-16 |
| R-C8 | Резервирование: баланс уменьшается на сумму | ТЗ Card Management, резервирование средств | TC-17 |
| — | Попарное покрытие всех пар параметров авторизации | модель и маппинг в приложении Г | 27 строк + критичные тройки |

## Классы эквивалентности

### Authorization

| № | Параметр | Группа значений | Валидная? | Что берём в тест | Ожидаемый результат |
|---|---|---|---|---|---|
| AUTH-01 | PAN | Существует в CMS | да | PAN активной карты из пула | проверка продолжается |
| AUTH-02 | PAN | Не существует в CMS | нет | «499999» + случайные 10 цифр, Лун-корректный | DECLINED, "14" |
| AUTH-03 | cardStatus | ACTIVE | да | статус ACTIVE | проверка продолжается |
| AUTH-04 | cardStatus | INACTIVE | нет | PATCH статуса на INACTIVE | DECLINED, "CARD_INACTIVE" |
| AUTH-05 | cardStatus | BLOCKED | нет | PATCH статуса на BLOCKED | DECLINED, "CARD_BLOCKED" |
| AUTH-06 | cardStatus | EXPIRED | нет | карта со статусом EXPIRED | DECLINED, "54" |
| AUTH-07 | expiryDate | ≥ текущего месяца | да | MMYY = текущий/будущий месяц | проверка продолжается |
| AUTH-08 | expiryDate | Прошлый месяц | нет | MMYY = прошлый месяц | DECLINED, "54" |
| AUTH-09 | amount vs dailyLimit | usage_today + amount ≤ dailyLimit | да | amount = dailyLimit − usage_today | лимит пройден |
| AUTH-10 | amount vs dailyLimit | usage_today + amount > dailyLimit | нет | amount = dailyLimit − usage_today + 1 | DECLINED, "61" |
| AUTH-11 | amount vs monthlyLimit | usage_month + amount ≤ monthlyLimit | да | amount = monthlyLimit − usage_month | лимит пройден |
| AUTH-12 | amount vs monthlyLimit | usage_month + amount > monthlyLimit | нет | amount = monthlyLimit − usage_month + 1 | DECLINED, "61" |
| AUTH-13 | amount vs balance | amount ≤ availableBalance | да | amount = availableBalance | баланс пройден |
| AUTH-14 | amount vs balance | amount > availableBalance | нет | amount = availableBalance + 1 | DECLINED, "51" |
| AUTH-15 | amount (формат) | Положительное целое (копейки) | да | amount = 100 (= 1,00 ₽) | валидно |
| AUTH-16 | amount (формат) | 0 | нет | amount = 0 | отклонение валидацией |
| AUTH-17 | amount (формат) | Отрицательное | нет | amount = −100 | отклонение валидацией |
| AUTH-18 | amount (формат) | Нецелое / не число | нет | amount = 10.5, "abc" | отклонение валидацией |
| AUTH-19 | CMS доступность | Card Management отвечает | да | контейнер запущен | проверка продолжается |
| AUTH-20 | CMS доступность | Card Management недоступен | нет | docker compose stop card-management | DECLINED, "05", "ISSUER_TIMEOUT" |

### Card Management

| ID класса | Поле/метод | Класс | Тип | Представитель | Ожидаемый результат |
|---|---|---|---|---|---|
| CM-01 | bin | 6 цифр | да | "400000" | карта создана |
| CM-02 | bin | Не 6 цифр (5/7) | нет | "40000", "4000000" | 400 |
| CM-03 | bin | Нецифровые символы | нет | "40000a" | 400 |
| CM-04 | cardholderName | Непустая строка | да | "IVAN IVANOV" | карта создана |
| CM-05 | cardholderName | Пустое/отсутствует | нет | "" | 400 |
| CM-06 | currencyCode | 3 цифры | да | "643" | карта создана |
| CM-07 | currencyCode | Иной формат | нет | "64", "RUB" | 400 |
| CM-08 | dailyLimit / monthlyLimit / initialBalance | Положительные целые | да | 15000000 / 300000000 / 100000000 | карта создана |
| CM-09 | dailyLimit | 0 или отрицательный | нет | 0, −1 | 400 |
| CM-10 | monthlyLimit | < dailyLimit | нет | daily 500000, monthly 100000 | 400 — открытый вопрос, см. раздел 7 |
| CM-11 | GET /api/cards/{pan} | Существующий PAN | да | карта из пула | 200, данные карты |
| CM-12 | GET /api/cards/{pan} | Несуществующий PAN | нет | валидный по Луну несуществующий | 404 |
| CM-13 | GET /api/cards (пагинация) | limit/offset в допустимом диапазоне | да | limit=10, offset=0 | 200, total ≤ 10 записей |
| CM-14 | GET /api/cards (пагинация) | offset за пределами total | нет | offset = total + 1 | 200, пустой список cards |
| CM-15 | GET /api/cards (фильтр status) | Допустимое значение | да | ACTIVE/INACTIVE/BLOCKED/EXPIRED | 200, только карты статуса |
| CM-16 | GET /api/cards (фильтр status) | Недопустимое значение | нет | status=HACKED | 400 или пустой результат — открытый вопрос, см. раздел 7 |
| CM-17 | PATCH | Корректные изменяемые поля | да | {"status":"BLOCKED"} | 200, поле изменено |
| CM-18 | PATCH | Несуществующий PAN | нет | случайный валидный PAN | 404 |
| CM-19 | PATCH | Недопустимое значение поля | нет | {"status":"FROZEN"} | 400 |
| CM-20 | DELETE | Существующая карта | да | активная карта | 200; GET → 404; транзакции не проходят |
| CM-21 | DELETE | Уже удалённая (DELETED) | нет | повторный DELETE того же PAN | 404 |
| CM-22 | generate.count | Целое ≥ 1 | да | 20 | 200, создано ровно count |
| CM-23 | generate.count | 0 или отрицательное | нет | 0 | 400 |
| CM-24 | generate.bins | Непустой список валидных BIN | да | ["400000","400001"] | 200, распределение по BIN равномерно |
| CM-25 | generate.bins | Пустой список | нет | [] | 400 |
| CM-26 | reserve.amount | ≤ availableBalance | да | amount = баланс | 200, баланс уменьшился |
| CM-27 | reserve.amount | > availableBalance | нет | amount = баланс + 1 | отказ (по коду сервиса 402 InsufficientFunds/PaymentRequired) |
| CM-28 | reserve.rrn | 12 цифр | да | "012345678901" | 200 |
| CM-29 | reserve.rrn | Не 12 цифр | нет | "123" | 400 |

## Граничные значения

Для каждой границы — тройка: значение на границе, ниже, выше. Значения в копейках.


B — граница (boundary): AUTH-B1 = граница №1 для авторизации.

| № | Параметр (граница) | OFF− | ON | OFF+ | Ожидания |
|---|---|---|---|---|---|
| AUTH-B01 | dailyLimit (usage+amount) | L−1 → APPROVED | L → APPROVED | L+1 → DECLINED "61" | [R-A4] |
| AUTH-B02 | monthlyLimit (usage+amount) | M−1 → APPROVED | M → APPROVED | M+1 → DECLINED "61" | [R-A5] |
| AUTH-B03 | availableBalance (amount) | B−1 → APPROVED (остаток 1) | B → APPROVED (остаток 0) | B+1 → DECLINED "51" | [R-A6] |
| AUTH-B04 | expiryDate (текущий месяц) | прошлый месяц → DECLINED "54" | текущий месяц → APPROVED | следующий месяц → APPROVED | [R-A3] |
| AUTH-B05 | amount (минимум) | 0 → отказ валидации | 1 → APPROVED | 2 → APPROVED | [R-A6] |
| CM-B01 | PAN (длина) | 15 → не проходит Лун/404 | 16 → валиден | 17 → невалиден | [R-C7] |
| CM-B02 | limit (пагинация) | 1 → 1 запись | 50 (дефолт) | 51 — открытый вопрос о верхней границе, см. раздел 7 | [R-C3] |
| CM-B03 | offset (пагинация) | total−1 → 1 запись | total → пусто | total+1 → пусто | [R-C3] |
| CM-B04 | generate.count | 0 → 400 | 1 → 1 карта | 2 → 2 карты | [R-C6] |
| CM-B05 | initialBalance при reserve | amount = B+1 → отказ | amount = B → 200, остаток 0 | amount = B−1 → 200 | [R-C8] |

## Попарное тестирование

### Модель

Модель — `pict/model.txt`: 8 параметров (card_status, pan_in_cms, amount_vs_daily, amount_vs_monthly, amount_vs_balance, expiry, terminal_type, mcc), 3 ограничения, исключающие бессмысленные сочетания (при не-ACTIVE карте или ненайденном PAN лимиты/баланс не проверяются; истёкший expiry закрывает проверку баланса).

Полный перебор: 4 × 2 × 3 × 3 × 3 × 3 × 3 × 3 = **5832** комбинации. Попарный набор: **27 строк** в `pict/cases.txt`.

### Правило проецирования строки набора в тест-кейс

Ожидаемый результат определяется порядком проверок алгоритма авторизации из ТЗ (порядок жёсткий):

1. pan_in_cms = not_found → DECLINED "14"
2. card_status = INACTIVE → "CARD_INACTIVE"; BLOCKED → "CARD_BLOCKED"; EXPIRED → "54"
3. expiry = previous_month → DECLINED "54"
4. amount_vs_daily = above → DECLINED "61"
5. amount_vs_monthly = above → DECLINED "61"
6. amount_vs_balance = above → DECLINED "51"
7. иначе → APPROVED "00" (RRN 12 цифр, authCode 6 символов)


### Критичные сочетания, добавленные вручную (не покрываются парами)

| ID | Сочетание 3+ параметров | Ожидание |
|---|---|---|
| PW-MAN-01 | ACTIVE + equal daily + equal monthly + equal balance (всё ровно на границах) | APPROVED "00" |
| PW-MAN-02 | EXPIRED-статус + previous_month + amount above balance | DECLINED "54" (статус проверяется раньше срока) |
| PW-MAN-03 | INACTIVE + above daily + above balance | DECLINED "CARD_INACTIVE" |
| PW-MAN-04 | not_found + above daily (невозможное сочетание исключено ограничением; вручную подтверждаем приоритет причины) | DECLINED "14" |

### Маппинг сгенерированного набора в тест-кейсы

Набор построен попарным алгоритмом по модели. Из 5832 полных комбинаций ограничениям модели удовлетворяют 675; попарный набор покрывает все 222 пары значений 27 строками. Каждая строка — отдельный тест-кейс; ожидаемый результат определяется по правилу проецирования. terminal_type и mcc задают контекст терминала и на ожидание не влияют.

| TC | card_status | pan_in_cms | vs_daily | vs_monthly | vs_balance | expiry | terminal | mcc | Ожидание |
|---|---|---|---|---|---|---|---|---|---|
| TC-PW-001 | ACTIVE | found | below | below | below | next_month | pos | grocery | APPROVED "00" |
| TC-PW-002 | ACTIVE | found | equal | equal | equal | current_month | atm | restaurant | APPROVED "00" |
| TC-PW-003 | ACTIVE | found | above | above | below | previous_month | ecom | electronics | DECLINED "54" |
| TC-PW-004 | ACTIVE | not_found | below | below | below | current_month | atm | electronics | DECLINED "14" |
| TC-PW-005 | INACTIVE | found | below | below | below | previous_month | ecom | restaurant | DECLINED "CARD_INACTIVE" |
| TC-PW-006 | ACTIVE | found | equal | equal | above | next_month | pos | electronics | DECLINED "51" |
| TC-PW-007 | ACTIVE | found | above | above | above | current_month | atm | grocery | DECLINED "61" |
| TC-PW-008 | ACTIVE | found | above | above | equal | next_month | pos | restaurant | DECLINED "61" |
| TC-PW-009 | ACTIVE | found | equal | equal | below | previous_month | ecom | grocery | DECLINED "54" |
| TC-PW-010 | BLOCKED | found | below | below | below | next_month | atm | grocery | DECLINED "CARD_BLOCKED" |
| TC-PW-011 | EXPIRED | found | below | below | below | next_month | ecom | grocery | DECLINED "54" |
| TC-PW-012 | ACTIVE | found | below | below | equal | current_month | ecom | grocery | APPROVED "00" |
| TC-PW-013 | ACTIVE | found | below | below | above | next_month | ecom | restaurant | DECLINED "51" |
| TC-PW-014 | ACTIVE | not_found | below | below | below | previous_month | pos | grocery | DECLINED "14" |
| TC-PW-015 | INACTIVE | found | below | below | below | current_month | pos | grocery | DECLINED "CARD_INACTIVE" |
| TC-PW-016 | EXPIRED | found | below | below | below | previous_month | atm | restaurant | DECLINED "54" |
| TC-PW-017 | ACTIVE | not_found | below | below | below | next_month | ecom | restaurant | DECLINED "14" |
| TC-PW-018 | INACTIVE | found | below | below | below | next_month | atm | electronics | DECLINED "CARD_INACTIVE" |
| TC-PW-019 | BLOCKED | found | below | below | below | current_month | pos | restaurant | DECLINED "CARD_BLOCKED" |
| TC-PW-020 | BLOCKED | found | below | below | below | previous_month | ecom | electronics | DECLINED "CARD_BLOCKED" |
| TC-PW-021 | EXPIRED | found | below | below | below | current_month | pos | electronics | DECLINED "54" |
| TC-PW-022 | ACTIVE | found | below | equal | equal | next_month | pos | electronics | APPROVED "00" |
| TC-PW-023 | ACTIVE | found | below | above | below | next_month | pos | grocery | DECLINED "61" |
| TC-PW-024 | ACTIVE | found | equal | below | below | next_month | pos | grocery | APPROVED "00" |
| TC-PW-025 | ACTIVE | found | equal | above | below | next_month | pos | grocery | DECLINED "61" |
| TC-PW-026 | ACTIVE | found | above | below | below | next_month | pos | grocery | DECLINED "61" |
| TC-PW-027 | ACTIVE | found | above | equal | below | next_month | pos | grocery | DECLINED "61" |
