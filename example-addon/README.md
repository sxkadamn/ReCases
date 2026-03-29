# Пример аддона ReCases

Это пример внешнего аддона для `ReCases`, который регистрирует пользовательскую анимацию открытия `crystal-burst`.

## Что показывает пример

- получение `ReCasesApi` через Bukkit `ServicesManager`
- регистрацию новой анимации через `recases-api`
- создание 3 сундуков выбора вокруг кейса
- передачу этих сундуков обратно в `ReCases` через `registerTargetChest(...)`

## Сборка

Собрать весь reactor можно из корня репозитория:

```powershell
mvn -q -DskipTests package
```

Или собрать только модуль примера:

```powershell
mvn -q -DskipTests -pl example-addon -am package
```

## Maven-зависимость

Пример использует публичный артефакт API:

```xml
<dependency>
    <groupId>net.recases</groupId>
    <artifactId>recases-api</artifactId>
    <version>1.0-SNAPSHOT</version>
    <scope>provided</scope>
</dependency>
```

## Использование

После установки аддона на сервер укажите его `id` анимации в конфиге `ReCases`:

```yml
profiles:
  my-case:
    animation: crystal-burst
```
