# NovaTeleport Folia 支持

本目录用于标记与记录 Folia 支持说明：

- 通过在 `Bukkit` 模块中适配调度与传送逻辑，同时在 `plugin.yml` 增加 `folia-supported: true` 标记，实现对 Folia 的兼容。
- 通用代码抽取在 `Common` 模块，供各平台模块复用。
- 构建/发版仍由父 POM 统一管理，Folia 目录作为结构化占位与文档说明，无需单独打包。
