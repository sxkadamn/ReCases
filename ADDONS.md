# ReCases Addon API

`ReCases` now exposes a Bukkit service for registering custom opening animations from other plugins.

## Maven dependency

The public API now lives in a separate artifact.

For local reactor builds:

```xml
<dependency>
    <groupId>net.recases</groupId>
    <artifactId>recases-api</artifactId>
    <version>1.0-SNAPSHOT</version>
    <scope>provided</scope>
</dependency>
```

For JitPack builds from GitHub:

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

## How to get the API

Your addon should depend on `ReCases` in `plugin.yml`:

```yml
depend:
  - ReCases
```

Then resolve the API from the Bukkit services manager:

```java
import net.recases.api.ReCasesApi;
import net.recases.api.animation.OpeningAnimationContext;
import net.recases.api.animation.OpeningAnimationRegistration;

ReCasesApi api = getServer().getServicesManager().load(ReCasesApi.class);
if (api == null) {
    getLogger().severe("ReCases API is unavailable.");
    getServer().getPluginManager().disablePlugin(this);
    return;
}

api.getOpeningAnimationRegistry().register(
        OpeningAnimationRegistration.create(
                this,
                "my-addon-animation",
                "My Addon Animation",
                1,
                context -> new MyOpeningAnimation(context)
        )
);
```

## Publishing

For local development:

```powershell
mvn -q -DskipTests install
```

This installs `net.recases:recases-api` into your local Maven cache.

For public consumption through JitPack:

1. Push the repository to GitHub.
2. Create a release tag such as `1.0.0`.
3. Let JitPack build that tag.
4. Use the JitPack coordinates shown above.

## Registration rules

- `id` must be unique and is matched case-insensitively.
- `requiredSelections` controls how many reward selections the animation expects from `OpeningSession`.
- Built-in animations cannot be overridden or unregistered.
- Animations registered by an addon are automatically removed when that addon is disabled.

## OpeningAnimationContext

Addon animation factories receive `OpeningAnimationContext` instead of internal plugin classes. The context provides:

- `getPlayer()` and `getRuntimeLocation()` for building visuals.
- `getSession()` for reward preview and current selection progress.
- `registerTargetChest(location)` and `registerTargetEntity(entity)` so your targets work with ReCases selection logic.
- `abortOpening(refundKey)` and `completeOpening()` for explicit flow control.
- `removeRuntimeHologram()` if your animation needs to hide the default case hologram.

## Config usage

After registration, the new animation id can be used anywhere the plugin already accepts animation ids:

- `profiles.<profile>.animation`
- `cases.instances.<instance>.animation`
