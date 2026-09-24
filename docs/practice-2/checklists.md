# Чек-листы СМП

## 1. Дымовое тестирование

Запускаем после `docker compose up -d`. Цель — быстро убедиться, что система жива, до всякой глубокой проверки.

**Инфраструктура**

- [ ] PostgreSQL и RabbitMQ — контейнеры healthy
- [ ] RabbitMQ Management UI открывается: http://localhost:15672 (smp/smp)
- [ ] Web Dashboard открывается: http://localhost:3000

**Живость сервисов (health отвечает 200)**

- [ ] Gateway — http://localhost:8080/health
- [ ] Card Management — http://localhost:8081/health (в ответе есть cardsInDatabase)
- [ ] Switch — http://localhost:8082/health
- [ ] Authorization — http://localhost:8083/health
- [ ] Merchant Simulator — http://localhost:8084/health
- [ ] Terminal Simulator — http://localhost:8085/health
- [ ] Transaction Logger — http://localhost:8088/health
- [ ] Bin Lookup — http://localhost:8096/actuator/health
- [ ] Notification — http://localhost:8097/actuator/health

**Базовая работоспособность**

- [ ] Генерация карт: POST /api/cards/generate (count=20) — 200
- [ ] Список карт: GET /api/cards?limit=5 — 200, total ≥ 20
- [ ] Успешная транзакция по активной карте — APPROVED, код «00»
- [ ] Отклонённая транзакция (сумма больше баланса) — DECLINED, код «51»
- [ ] Обе транзакции видны в Transaction Logger и на Dashboard

## 2. Критический путь

Сквозные сценарии через всю цепочку: терминал → Gateway → Switch → Authorization → Card Management → Logger → Dashboard.

**Жизненный цикл карты**

- [ ] Создание вручную (POST /api/cards): PAN проходит Луна, срок +3 года, статус ACTIVE
- [ ] Блокировка (PATCH status=BLOCKED): транзакция отклоняется CARD_BLOCKED
- [ ] Неактивная карта: транзакция отклоняется CARD_INACTIVE
- [ ] Истёкшая (статус EXPIRED или срок прошлым месяцем): отклонение «54»
- [ ] Удаление (DELETE): GET возвращает 404, транзакции по карте не проходят

**Транзакции**

- [ ] Покупка по активной карте: APPROVED, баланс уменьшился ровно на сумму
- [ ] Дневной лимит: сумма впритык — успех; на 1 копейку больше — отклонение «61»
- [ ] Месячный лимит: аналогично дневному — отклонение «61»
- [ ] Баланс: сумма ровно баланс — успех и остаток 0; на копейку больше — отклонение «51»
- [ ] Несуществующая карта — отклонение «14»
- [ ] Откат транзакции по RRN: средства вернулись на баланс
- [ ] RRN уникален для каждой транзакции (12 цифр), authCode — 6 символов A–Z0–9

**Устойчивость**

- [ ] Остановка card-management: транзакция отклоняется «05» ISSUER_TIMEOUT; после старта авторизация восстанавливается
- [ ] Dashboard показывает транзакции в реальном времени

## 3. Периферийные сервисы

Для остальных сервисов достаточно проверок верхнего уровня.

- [ ] Gateway: маршрутизация запросов на сервисы, rate limit работает
- [ ] Switch: определяет issuerId по BIN, публикует события в RabbitMQ, делает reversal при недоступном Logger
- [ ] Terminal Simulator: получает пул карт из CMS, отправляет транзакции pos/atm/ecom
- [ ] Merchant Simulator: ecom-покупка проходит, подтверждение и отмена работают
- [ ] Transaction Logger: все транзакции записаны, поиск по RRN находит нужную
- [ ] Bin Lookup: отвечает issuerId по BIN; при таймауте срабатывает fallback
- [ ] Notification: получает события из RabbitMQ
- [ ] Web Dashboard: списки и фильтры работают после рестарта цепочки
