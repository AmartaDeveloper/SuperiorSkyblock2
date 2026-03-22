<p align="center">
<img src="https://static.bg-software.com/imgs/superiorskyblock-logo.png" />
<h2 align="center">The most optimized Skyblock core on the market.</h2>
</p>
<br>
<p align="center">
<a href="https://bg-software.com/discord/"><img src="https://img.shields.io/discord/293212540723396608?color=7289DA&label=Discord&logo=discord&logoColor=7289DA&link=https://bg-software.com/discord/"></a>
<a href="https://bg-software.com/patreon/"><img src="https://img.shields.io/badge/-Support_on_Patreon-F96854.svg?logo=patreon&style=flat&logoColor=white&link=https://bg-software.com/patreon/"></a><br>
<a href=""><img src="https://img.shields.io/maintenance/yes/2025"></a>
<a href="https://www.codacy.com/gh/BG-Software-LLC/SuperiorSkyblock2/dashboard?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=BG-Software-LLC/SuperiorSkyblock2&amp;utm_campaign=Badge_Grade"><img src="https://app.codacy.com/project/badge/Grade/cf81db478cf74983abac6f3605dc53b4"/></a>
</p>

## Compiling

You can compile the project using gradlew.<br>
Run `gradlew build` in console to build the project.<br>
You can find already compiled jars on our [Jenkins](https://hub.bg-software.com/) hub!<br>

## API

The plugin is packed with a rich API for interacting with islands, players and more. When hooking into the plugin, it's
highly recommended to only use the API and not the compiled plugin, as the API methods are not only commented, but also
will not get removed or changed unless they are marked as deprecated. This means that when using the API, you won't have
to do any additional changes to your code between updates.

### Maven

```xml

<repositories>
    <repository>
        <id>bg-repo</id>
        <url>https://repo.bg-software.com/repository/api/</url>
    </repository>
</repositories>

<dependencies>
<dependency>
    <groupId>com.bgsoftware</groupId>
    <artifactId>SuperiorSkyblockAPI</artifactId>
    <version>VERSION</version>
    <scope>provided</scope>
</dependency>
</dependencies>
```

### Gradle

```text
repositories {
    maven { url 'https://repo.bg-software.com/repository/api/' }
}

dependencies {
    compileOnly 'com.bgsoftware:SuperiorSkyblockAPI:VERSION'
}
```

Make sure you replace `VERSION` with the matching version.

## Updates

This plugin is provided "as is", which means no updates or new features are guaranteed. We will do our best to keep
updating and pushing new updates, and you are more than welcome to contribute your time as well and make pull requests
for bug fixes.

## License

This plugin is licensed under GNU GPL v3.0

This plugin uses HikariCP which you can find [here](https://github.com/brettwooldridge/HikariCP).

---

## Fork Changes

This is a custom fork with additional features built for a Velocity-based network.

### Island Lobby Portal System

Players who step into a nether portal on their island are instantly transferred to the configured lobby/hub server via Velocity's BungeeCord plugin messaging channel. Mimics Hypixel Skyblock's behaviour — no 80-tick countdown, no world change animation.

**How it works:**
- Intercepts the portal at tick 0 (`EntityEnterPortalEvent`) before vanilla nether travel begins.
- Sends a `Connect` plugin message using the correct `ByteArrayDataOutput` wire format Velocity expects.
- A 5-tick immunity window prevents `/is go` (and other plugin teleports) from falsely triggering the send when the landing spot is near a portal block.
- End portals still send players back to their island home as normal.

**Config (`config.yml`):**
```yaml
island-lobby-portal:
  enabled: true
  destination-server: 'skyblock-hub'   # must match your velocity.toml server name
  channel-name: 'BungeeCord'           # for Velocity with bungee-plugin-message-channel = true
```

**Velocity requirement** — in `velocity.toml`:
```toml
[advanced]
  bungee-plugin-message-channel = true
```

---

### Per-Schematic Portal Region Protection

Because each island schematic can place the nether portal at a different position relative to the island center, protection regions are stored **per schematic type** (e.g. `normal`, `mycel`, `desert`). The same offsets protect the portal on every island of that type regardless of where it is in the world.

Protected blocks cannot be broken or placed by players. Additional protections:
- Players cannot light new nether portals with flint & steel or fire charges.
- Nether portal blocks cannot spread to form new portal columns.

**Admin setup (one-time per schematic):**
1. `/is admin portalwand` — receive the selection wand (blaze rod).
2. Left-click a block → position 1, right-click → position 2 (select the full portal frame and portal blocks).
3. `/is admin setportalregion <schematic>` — Tab completes from the live schematic list.
4. Repeat for each schematic type that contains a portal.

Region data is saved to `plugins/SuperiorSkyblock/portal-region.yml` and loaded on restart.

**Permissions:**

| Permission | Default | Description |
|---|---|---|
| `superior.admin.portalwand` | op | Receive the portal region selection wand |
| `superior.admin.setportalregion` | op | Save a portal region for a schematic |
| `superior.island.portal.bypass` | false | Bypass portal block break/place protection |

---

### New Files

| File | Description |
|---|---|
| `API/.../api/service/portals/IslandLobbyPortalService.java` | Public API interface for the lobby portal service |
| `src/.../service/portals/IslandLobbyPortalServiceImpl.java` | Service implementation — messaging, per-schematic region storage |
| `src/.../listener/LobbyPortalWandListener.java` | Wand left/right click handling for region selection |
| `src/.../commands/admin/CmdAdminPortalWand.java` | `/is admin portalwand` command |
| `src/.../commands/admin/CmdAdminSetPortalRegion.java` | `/is admin setportalregion <schematic>` command |

### Modified Files

| File | Change |
|---|---|
| `src/.../listener/PortalsListener.java` | Instant lobby portal intercept, teleport immunity, block break/place/ignite/spread protection |
| `src/.../listener/BukkitListeners.java` | Registered `LobbyPortalWandListener` |
| `src/.../commands/admin/AdminCommandsMap.java` | Registered new portal wand commands |
| `src/.../service/ServicesHandler.java` | Registered `IslandLobbyPortalServiceImpl` |
| `src/main/resources/config.yml` | Added `island-lobby-portal` config section |
