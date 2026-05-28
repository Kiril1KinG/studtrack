# Тестирование и покрытие

## Запуск unit-тестов

Для основного окружения проекта (Java 21):

```powershell
./mvnw.cmd test
```

## Полная проверка с JaCoCo

```powershell
./mvnw.cmd verify
```

Команда `verify`:

- запускает тесты;
- собирает данные покрытия JaCoCo;
- генерирует отчеты XML/HTML;
- применяет настроенный quality gate для выбранных пакетов.

## Где смотреть отчеты JaCoCo

После успешного запуска `verify`:

- HTML-отчет: `target/site/jacoco/index.html`
- XML-отчет: `target/site/jacoco/jacoco.xml`
- CSV-отчет: `target/site/jacoco/jacoco.csv`

## Примечания

- `StudtrackApplicationTests` в этой ветке отключен намеренно, так как требует внешнее окружение БД/Liquibase и делает локальный unit-пайплайн нестабильным.
- Coverage gate сейчас ограничен ключевыми целевыми пакетами:
  - `ru.diploma.studtrack.service`
  - `ru.diploma.studtrack.controller.api`
  - `ru.diploma.studtrack.controller.web`
- Если в локальном окружении недоступен JDK 21, можно временно запустить проверку с override:

```powershell
./mvnw.cmd "-Djava.version=17" "-Dmaven.compiler.release=17" verify
```
