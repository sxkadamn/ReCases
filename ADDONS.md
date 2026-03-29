# ReCases Addon API

`ReCases` предоставляет Bukkit service для регистрации пользовательских анимаций открытия из других плагинов.

## Maven-зависимость

Публичный API вынесен в отдельный артефакт.

Для локальной сборки внутри reactor:

```xml
<dependency>
    <groupId>net.recases</groupId>
    <artifactId>recases-api</artifactId>
    <version>1.0-SNAPSHOT</version>
    <scope>provided</scope>
</dependency>
```

Для подключения через JitPack из GitHub:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
```

```xml
<dependency>
    <groupId>com.github.<GitHubUser>.<RepoName></groupId>
    <artifactId>recases-api</artifactId>
    <version><GitTag></version>
    <scope>provided</scope>
</dependency>
```

## Как получить API

В `plugin.yml` аддона должна быть зависимость от `ReCases`:

```yml
depend:
  - ReCases
```

После этого получите API через Bukkit `ServicesManager`:

```java
import net.recases.api.ReCasesApi;
import net.recases.api.animation.OpeningAnimationContext;
import net.recases.api.animation.OpeningAnimationRegistration;

ReCasesApi api = getServer().getServicesManager().load(ReCasesApi.class);
if (api == null) {
    getLogger().severe("API ReCases недоступно.");
    getServer().getPluginManager().disablePlugin(this);
    return;
}

api.getOpeningAnimationRegistry().register(
        OpeningAnimationRegistration.create(
                this,
                "my-addon-animation",
                "Моя анимация аддона",
                1,
                context -> new MyOpeningAnimation(this, context)
        )
);
```

## Правила регистрации

- `id` должен быть уникальным и сравнивается без учета регистра.
- `requiredSelections` определяет, сколько выборов награды ожидает анимация из `OpeningSession`.
- Встроенные анимации нельзя переопределить или удалить.
- Анимации, зарегистрированные аддоном, автоматически удаляются при его отключении.

## OpeningAnimationContext

Фабрики анимаций аддонов получают `OpeningAnimationContext` вместо внутренних классов плагина. Контекст предоставляет:

- `getPlayer()` и `getRuntimeLocation()` для построения визуала.
- `getSession()` для просмотра награды и текущего прогресса выбора.
- `registerTargetChest(location)` и `registerTargetEntity(entity)`, чтобы ваши цели работали с логикой выбора ReCases.
- `abortOpening(refundKey)` и `completeOpening()` для явного управления процессом.
- `removeRuntimeHologram()`, если анимации нужно скрыть стандартный голограм кейса.
- Для `BukkitRunnable` и scheduler используйте экземпляр собственного плагина аддона, а не API-объект.

## Использование в конфиге

После регистрации новый `id` анимации можно использовать везде, где плагин уже принимает идентификатор анимации:

- `profiles.<profile>.animation`
- `cases.instances.<instance>.animation`
