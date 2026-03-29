# ReCases

`ReCases` is split into Maven modules so the public addon API can be consumed separately from the main plugin jar.

## JitPack

After you push this repository to GitHub and create a release tag, JitPack can build it directly.

Add the repository:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
```

For this multi-module repository, JitPack uses:

- groupId: `com.github.<GitHubUser>.<RepoName>`
- artifactId: Maven module artifact id
- version: Git tag, release, or commit hash

So the addon API dependency will look like this:

```xml
<dependency>
    <groupId>com.github.<GitHubUser>.<RepoName></groupId>
    <artifactId>recases-api</artifactId>
    <version>1.0.0</version>
    <scope>provided</scope>
</dependency>
```

If your repository is `sty/lieCases`, the dependency becomes:

```xml
<dependency>
    <groupId>com.github.sty.lieCases</groupId>
    <artifactId>recases-api</artifactId>
    <version>1.0.0</version>
    <scope>provided</scope>
</dependency>
```

## Modules

- `recases-api` - public API for addon developers
- `recases-plugin` - main `ReCases` plugin
- `example-addon` - sample addon using `recases-api`

## Build

```powershell
mvn -q -DskipTests package
```
