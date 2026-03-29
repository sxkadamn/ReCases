# ReCases Example Addon

Example external addon for `ReCases` that registers the custom `crystal-burst` opening animation.

## What it shows

- loading `ReCasesApi` from Bukkit `ServicesManager`
- registering a custom animation through `recases-api`
- spawning 3 selection chests around the case
- handing those chests back to `ReCases` through `registerTargetChest(...)`

## Build

Build the whole reactor from the repository root:

```powershell
mvn -q -DskipTests package
```

Or build only the example module:

```powershell
mvn -q -DskipTests -pl example-addon -am package
```

## Maven dependency

The example uses the public API artifact:

```xml
<dependency>
    <groupId>net.recases</groupId>
    <artifactId>recases-api</artifactId>
    <version>1.0-SNAPSHOT</version>
    <scope>provided</scope>
</dependency>
```

## Usage

After installing the addon on the server, reference its animation id in the `ReCases` config:

```yml
profiles:
  my-case:
    animation: crystal-burst
```
