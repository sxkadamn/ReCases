# ReCases API

`ReCases` позволяет внешним плагинам регистрировать свои анимации открытия кейсов.

Этот `README` написан для разработчиков аддонов:
- как подключить API
- как получить `ReCasesApi`
- как зарегистрировать свою анимацию
- как правильно работать с целями выбора и завершением открытия

## Что нужно

- сервер с установленным `ReCases`
- для компиляции: `Java 17`
- для Maven-зависимости: `JitPack`

## Maven

Подключение API через JitPack:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.github.sxkadamn.ReCases</groupId>
        <artifactId>recases-api</artifactId>
        <version>1.0.2</version>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

Если будет новый релиз, просто замени `version`.

## plugin.yml

Аддон должен зависеть от `ReCases`, чтобы API точно было доступно в `onEnable()`:

```yml
depend:
  - ReCases
```

## Получение API

`ReCases` публикует Bukkit service:

```java
import net.recases.api.ReCasesApi;

ReCasesApi api = getServer().getServicesManager().load(ReCasesApi.class);
if (api == null) {
    getLogger().severe("API ReCases недоступно.");
    getServer().getPluginManager().disablePlugin(this);
    return;
}
```

## Регистрация анимации

Минимальная регистрация своей анимации:

```java
import net.recases.api.ReCasesApi;
import net.recases.api.animation.OpeningAnimationRegistration;
import org.bukkit.plugin.java.JavaPlugin;

public final class MyAddon extends JavaPlugin {

    @Override
    public void onEnable() {
        ReCasesApi api = getServer().getServicesManager().load(ReCasesApi.class);
        if (api == null) {
            getLogger().severe("API ReCases недоступно.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        boolean registered = api.getOpeningAnimationRegistry().register(
                OpeningAnimationRegistration.create(
                        this,
                        "my-addon-animation",
                        "Моя анимация",
                        3,
                        context -> new MyOpeningAnimation(this, context)
                )
        );

        if (!registered) {
            getLogger().severe("Анимация с таким id уже зарегистрирована.");
            getServer().getPluginManager().disablePlugin(this);
        }
    }
}
```

### Что значат параметры

- `owner` — экземпляр твоего плагина
- `id` — уникальный id анимации, потом он используется в конфиге `ReCases`
- `displayName` — читаемое имя анимации
- `requiredSelections` — сколько целей должен выбрать игрок, прежде чем открытие завершится
- `factory` — фабрика, которая создает экземпляр анимации

## Пример анимации

Минимальный каркас:

```java
import net.recases.api.animation.OpeningAnimation;
import net.recases.api.animation.OpeningAnimationContext;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class MyOpeningAnimation implements OpeningAnimation {

    private final Plugin plugin;
    private final OpeningAnimationContext context;

    public MyOpeningAnimation(Plugin plugin, OpeningAnimationContext context) {
        this.plugin = plugin;
        this.context = context;
    }

    @Override
    public boolean play() {
        if (!context.isRuntimeAvailable() || !context.isOpeningActive()) {
            return false;
        }

        World world = context.getRuntimeLocation().getWorld();
        if (world == null) {
            context.abortOpening(true);
            return false;
        }

        Player player = context.getPlayer();
        Location base = context.getRuntimeLocation().getBlock().getLocation();

        context.removeRuntimeHologram();
        player.teleport(base.clone().add(0.5, 0.0, 0.5));

        Location chestLocation = base.clone().add(3, 0, 0);
        Block chest = chestLocation.getBlock();
        chest.setType(Material.CHEST);

        context.registerTargetChest(chestLocation);
        return true;
    }
}
```

## OpeningAnimationContext

Через `OpeningAnimationContext` аддон получает все нужное для работы.

### Основные методы

- `getPlayer()` — игрок, который открывает кейс
- `getRuntimeId()` — id физического кейса
- `getRuntimeLocation()` — локация кейса
- `getProfileId()` — id профиля кейса
- `getSession()` — данные текущего открытия
- `isRuntimeAvailable()` — кейс еще существует и доступен
- `isOpeningActive()` — открытие еще не завершилось и не было отменено

### Управление открытием

- `removeRuntimeHologram()` — убрать стандартный голограм кейса
- `abortOpening(refundKey)` — отменить открытие
- `completeOpening()` — завершить открытие вручную

### Регистрация целей

- `registerTargetChest(location)` — зарегистрировать блок-цель
- `registerTargetEntity(entity)` — зарегистрировать entity-цель

Это критично: если ты просто заспавнил сундук или entity, но не зарегистрировал цель, `ReCases` не будет считать это валидным выбором игрока.

## OpeningSessionView

Через `context.getSession()` можно читать текущее состояние:

- `getPlayerId()`
- `getPlayerName()`
- `getProfileId()`
- `getAnimationId()`
- `getRequiredSelections()`
- `getOpenedCount()`
- `getRewardName()`
- `getRewardIcon()`
- `isRewardRare()`
- `isGuaranteedReward()`

Это полезно, если анимация должна менять визуал в зависимости от редкости награды или прогресса выбора.

## Как правильно писать анимацию

- Всегда сначала проверяй `context.isRuntimeAvailable()` и `context.isOpeningActive()`.
- Если анимация использует `BukkitRunnable`, запускай его от своего плагина, а не от API-объекта.
- Если создаешь временные блоки или entity, продумай их очистку.
- Если анимация не смогла стартовать, возвращай `false` или вызывай `abortOpening(true)`.
- Если у тебя несколько целей, `requiredSelections` должен совпадать с их количеством.

## Завершение открытия

Обычно встроенная логика `ReCases` сама завершает открытие, когда игрок выбрал все зарегистрированные цели.

То есть стандартный сценарий такой:
1. Ты создаешь цели.
2. Регистрируешь их через `registerTargetChest(...)` или `registerTargetEntity(...)`.
3. Игрок нажимает на них.
4. `ReCases` сам ведет прогресс и выдает награду в конце.

Ручной `completeOpening()` нужен только если ты делаешь полностью особый сценарий.

## Подключение анимации в конфиге ReCases

После регистрации id можно использовать в конфиге `ReCases`:

```yml
profiles:
  donate:
    animation: my-addon-animation
```

или для конкретной физической точки кейса:

```yml
cases:
  instances:
    main:
      animation: my-addon-animation
```

## Пример готового аддона

Смотри:

- [ReCasesExampleAddon.java](/D:/pluginslie/lieCases/example-addon/src/main/java/net/recases/exampleaddon/ReCasesExampleAddon.java)
- [CrystalBurstAnimation.java](/D:/pluginslie/lieCases/example-addon/src/main/java/net/recases/exampleaddon/CrystalBurstAnimation.java)

## Схематики

В самом `ReCases` есть поддержка схем для анимаций через `WorldEdit`, но это относится к настройке основного плагина, а не к API аддона.

Если нужно использовать сцены из `.schem`, смотри:

- [config.yml](/D:/pluginslie/lieCases/recases-plugin/src/main/resources/config.yml)
- [SchematicService.java](/D:/pluginslie/lieCases/recases-plugin/src/main/java/net/recases/services/SchematicService.java)

