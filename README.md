# ReCases

`ReCases` разделен на Maven-модули, чтобы публичный API аддонов можно было использовать отдельно от основного JAR-файла плагина

## JitPack

После того как вы отправите этот репозиторий на GitHub и создадите тег релиза, JitPack сможет собрать его напрямую

Добавьте репозиторий в ваш pom.xml:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
```

Для этого многомодульного репозитория JitPack использует следующие параметры:

- groupId: `com.github.<GitHubUser>.<RepoName>`
- artifactId: ID Maven-модуля (artifact id)
- version: Тег Git, релиз или хеш коммита

Таким образом, зависимость для API аддонов будет выглядеть так:

```xml
<dependency>
    <groupId>com.github.<GitHubUser>.<RepoName></groupId>
    <artifactId>recases-api</artifactId>
    <version>1.0.0</version>
    <scope>provided</scope>
</dependency>
```

Если ваш репозиторий называется  `sty/ReCases`, зависимость примет вид:

```xml
<dependency>
    <groupId>com.github.sty.ReCases</groupId>
    <artifactId>recases-api</artifactId>
    <version>1.0.0</version>
    <scope>provided</scope>
</dependency>
```

## Модули

- `recases-api` - публичный API для разработчиков аддонов
- `recases-plugin` - основной плагин `ReCases`
- `example-addon` - пример аддона, использующий `recases-api`

