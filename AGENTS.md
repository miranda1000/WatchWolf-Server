# AGENTS.md — WatchWolf-Server

The in-game half of WatchWolf: a **Spigot plugin** that opens a socket and executes the Tester's
petitions against the live server (place blocks, spawn entities, run commands, read inventories,
record video, pull timings…).

`dev.watchwolf:watchwolf-server` · **Java 8** · Maven · DST `0b001` in the
[WatchWolf protocol](https://github.com/watch-wolf/WatchWolf).
Pre-compiled builds: <https://watchwolf.dev/versions/>.

## How it runs

WatchWolf-ServersManager drops this jar into a server's `plugins/` folder together with
`plugins/WatchWolf/config.yml`:

```yaml
target-ip: 127.0.0.1   # the only IP allowed to talk to the socket
use-port: 25566        # published by the container as <mcPort>+1
```

`onEnable` reads that config, starts a `ServerConnector` thread on `use-port`, installs a log4j
appender to capture console output, and registers a 1-tick repeating Bukkit task that drains a
queue of `ThrowableRunnable`s — **every petition that touches the Bukkit API is executed from
that task**, because the socket thread is not the main thread.

## Layout

```
src/main/java/dev/watchwolf/
├── server/
│   ├── Server.java                 the JavaPlugin; implements ServerPetition + SequentialExecutor
│   ├── ServerConnector.java        socket loop; raw binary opcode dispatch (DST 0b001)
│   ├── LogAppender.java            log4j appender -> console capture + command replies
│   ├── SequentialExecutor.java     "run this on the main thread" contract
│   ├── EnhancedInformationProvider.java   camera recording / timings entry points
│   ├── ExtendedPetitionManager.java       base for optional-plugin integrations
│   ├── events/invincibility/       damage cancelling (setInvincibleMode)
│   ├── events/whitelist/           offline whitelist handling
│   ├── timings/                    Spigot vs Paper timings reports
│   └── worldguard/                 WorldGuard API 6 vs 7 adapters
├── utils/
│   ├── ServerTypeGetter.java       Spigot or Paper? (1.8 needs a Paperclip class probe)
│   ├── SpigotToWatchWolfTranslator.java   BlockData/ItemStack/Entity -> WatchWolf entities
│   └── WatchWolfToSpigotTranslator.java   the reverse
└── src/main/resources/
    ├── plugin.yml                  main=dev.watchwolf.server.Server, api-version 1.13,
    │                               softdepend [WorldGuard, Residence]
    └── blocks.json                 legacy id:data defaults per block base name
```

## Build

```bash
./ci/build.sh --preclean
```

Runs `mvn compile assembly:single` inside `maven:3.8.4-openjdk-8`; the artifact lands in
`target/` named **`WatchWolf-<version>-1.8-LATEST.jar`** (`finalName` in `pom.xml`). That
`-<minMc>-<maxMc>` suffix is the naming convention the ServersManager's `usual-plugins/` folder
expects, so the jar can be copied straight there.

### Dependencies you must provide

`lib/` is gitignored; put these in place before building:

- `lib/spigot-1.16.5.jar` — compiled against the 1.16.5 API, but the plugin is expected to run on
  **1.8 through the latest**, hence XSeries and the reflection/adapters under `worldguard/`,
  `timings/` and `utils/`.
- `lib/watchwolf-tester-0.2.1.jar` — see the note below.

Both are installed into `~/.m2` by the default `local-ww-core-profile` during the **`clean`**
phase, which is why `--preclean` matters after swapping a jar.

## Conventions and gotchas

- **This module still uses the *old* shared library.** It imports `dev.watchwolf.entities.*`,
  `dev.watchwolf.server.ServerPetition`, `SocketData`, etc. from **watchwolf-tester 0.2.1** — not
  from `dev.watchwolf.core.*` in [WatchWolf-Core](https://github.com/watch-wolf/WatchWolf-Core).
  The ServersManager has already migrated; this module has not. Entity/block changes may need to
  be made in both trees.
- **The protocol is hand-written here**, not generated: `ServerConnector` switches on raw
  literals such as `0b0001_1_001` and reads arguments field by field. When adding an operation,
  the encoding must match `API/API.tex` in the WatchWolf repo exactly, and the Tester side must
  be updated in lockstep.
- **Never call Bukkit from the socket thread.** Wrap the work in `this.run(() -> …)` (the
  `SequentialExecutor` queue) and let the repeating task execute it.
- **Cross-version support is the hard part.** Prefer `XSeries` (`XMaterial`) over raw `Material`
  constants, and go through `SpigotToWatchWolfTranslator` / `WatchWolfToSpigotTranslator` rather
  than touching `BlockData` directly. New block properties come from
  [WatchWolf-MaterialGetter](https://github.com/miranda1000/WatchWolf-MaterialGetter), whose
  README documents the end-to-end procedure for adding one.
- Optional integrations are resolved at runtime: WorldGuard is picked by version
  (`WorldGuardManagerFactory`: `<7.0.0` → `API6WorldGuardManager`) and falls back to
  `UnimplementedWorldGuardManager` when absent; timings pick Spigot vs Paper via
  `ServerTypeGetter.isPaper()`. Keep that fallback shape — a missing soft-dependency must never
  break the plugin.
- There are **no automated tests in this repo**; coverage lives in WatchWolf-Tester's
  `src/test/java`, which drives a real server running this plugin.
- `ServerConnector` accepts any connection today — the `allowedIp` check is a `TODO`.
