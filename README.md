# ReCases

`ReCases` разделен на Maven-модули, чтобы публичный API для аддонов можно было подключать отдельно от основного jar-файла плагина.

## JitPack

После публикации репозитория на GitHub и создания тега релиза JitPack сможет собрать проект напрямую.

Добавьте репозиторий:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
```

Для этого multi-module репозитория JitPack использует:

- `groupId`: `com.github.<GitHubUser>.<RepoName>`
- `artifactId`: идентификатор Maven-модуля
- `version`: git-тег, релиз или commit hash

Тогда зависимость на API для аддона будет выглядеть так:

```xml
<dependency>
    <groupId>com.github.<GitHubUser>.<RepoName></groupId>
    <artifactId>recases-api</artifactId>
    <version>1.0.0</version>
    <scope>provided</scope>
</dependency>
```

Если ваш репозиторий называется `sxkadamn/ReCases`, зависимость будет такой:

```xml
<dependency>
    <groupId>com.github.sxkadamn.ReCases</groupId>
    <artifactId>recases-api</artifactId>
    <version>1.0.0</version>
    <scope>provided</scope>
</dependency>
```

## Модули

- `recases-api` - публичный API для разработчиков аддонов
- `recases-plugin` - основной плагин `ReCases`
- `example-addon` - пример внешнего аддона с использованием `recases-api`
