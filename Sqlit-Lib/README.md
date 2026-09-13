NovaStorage (CN/EN)

CN:
- 统一的存储抽象库，供 Nova 系列插件依赖。
- JDBC: H2/MySQL/MariaDB/PostgreSQL（使用可移植的 upsert，驱动由运行环境提供）
- 注意：本模块目前**未被 NovaTeleport 主插件使用**（NovaTeleport 使用自己的 YAML DataStore）。
  它作为可复用的存储库保留；主插件不再把本模块打进发布包。
- 原 `redis/RedisBus` 已删除：其反射实现无法创建 JedisPubSub 匿名子类，实际不可用。
  跨服消息由主插件 `lib/jedis`（shade 内嵌 Jedis）直接实现。

EN:
- Unified storage abstraction library for Nova plugins.
- JDBC: H2/MySQL/MariaDB/PostgreSQL (portable upsert; driver supplied by the runtime)
- Note: this module is **not currently used by the NovaTeleport plugin** (which ships its own YAML
  DataStore). It is kept as a reusable library and is no longer bundled into the released jars.
- The old `redis/RedisBus` was removed: its reflection-based implementation could not instantiate an
  anonymous JedisPubSub subclass and was non-functional. Cross-server messaging is implemented
  directly in the plugin on top of the shaded Jedis.
