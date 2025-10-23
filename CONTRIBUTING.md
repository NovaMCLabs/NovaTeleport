# 贡献指南 | Contributing Guide

感谢您对 NovaTeleport 的关注！我们欢迎所有形式的贡献。

Thank you for your interest in NovaTeleport! We welcome all forms of contributions.

---

## 🤝 如何贡献 | How to Contribute

### 报告 Bug | Reporting Bugs

如果您发现了 Bug，请：

1. 检查 [Issues](https://github.com/novamclabs/NovaTeleport/issues) 是否已有相关报告
2. 如果没有，创建新的 Issue
3. 提供以下信息：
   - 服务器版本（Spigot/Paper/Folia）
   - 插件版本
   - 错误日志
   - 复现步骤
   - 预期行为 vs 实际行为

If you found a bug, please:

1. Check if there's an existing [Issue](https://github.com/novamclabs/NovaTeleport/issues)
2. If not, create a new Issue
3. Provide the following information:
   - Server version (Spigot/Paper/Folia)
   - Plugin version
   - Error logs
   - Steps to reproduce
   - Expected vs actual behavior

### 提出新功能 | Suggesting Features

我们欢迎新功能建议！

1. 在 Issues 中创建 Feature Request
2. 详细描述功能需求
3. 说明使用场景
4. 如果可能，提供实现思路

We welcome feature suggestions!

1. Create a Feature Request in Issues
2. Describe the feature in detail
3. Explain use cases
4. If possible, provide implementation ideas

### 提交代码 | Submitting Code

#### 准备工作 | Prerequisites

- JDK 17+
- Maven 3.8+
- Git
- IDE（推荐 IntelliJ IDEA）

#### 开发流程 | Development Workflow

1. **Fork 仓库**
   ```bash
   # 在 GitHub 上 Fork 本仓库
   ```

2. **克隆到本地**
   ```bash
   git clone https://github.com/YOUR_USERNAME/NovaTeleport.git
   cd NovaTeleport
   ```

3. **创建分支**
   ```bash
   git checkout -b feature/your-feature-name
   # 或
   git checkout -b fix/your-bug-fix
   ```

4. **开发和测试**
   ```bash
   # 编译
   mvn clean package
   
   # 运行测试
   mvn test
   ```

5. **提交更改**
   ```bash
   git add .
   git commit -m "feat: add amazing feature"
   
   # 或
   git commit -m "fix: resolve issue #123"
   ```

6. **推送到 Fork**
   ```bash
   git push origin feature/your-feature-name
   ```

7. **创建 Pull Request**
   - 在 GitHub 上打开 Pull Request
   - 详细描述更改内容
   - 关联相关 Issue（如果有）

---

## 📝 代码规范 | Code Standards

### Java 代码风格 | Java Code Style

- **缩进**: 4 个空格
- **命名**:
  - 类名: `PascalCase`
  - 方法名: `camelCase`
  - 常量: `UPPER_SNAKE_CASE`
  - 包名: `lowercase`
- **注释**: 使用中英文双语注释
  ```java
  // 检查权限 | Check permission
  if (!player.hasPermission("novateleport.use")) {
      // 拒绝访问 | Deny access
      return false;
  }
  ```

### 提交信息规范 | Commit Message Convention

使用 [Conventional Commits](https://www.conventionalcommits.org/) 规范：

```
<type>(<scope>): <subject>

<body>

<footer>
```

**类型 (type)**:
- `feat`: 新功能
- `fix`: Bug 修复
- `docs`: 文档更新
- `style`: 代码格式（不影响功能）
- `refactor`: 重构
- `test`: 测试相关
- `chore`: 构建/工具相关

**示例**:
```
feat(guild): add guild warp system

- Add GuildWarpManager
- Add /gtp warp command
- Add guild warp configuration

Closes #123
```

---

## 🏗️ 项目结构 | Project Structure

```
NovaTeleport/
├── Common/          # 共享代码
├── Bukkit/          # Bukkit 实现
│   ├── src/main/java/
│   │   └── com/novamclabs/
│   │       ├── commands/
│   │       ├── guild/
│   │       ├── towny/
│   │       ├── toll/
│   │       └── ...
│   └── src/main/resources/
│       ├── config.yml
│       ├── plugin.yml
│       └── ...
├── BungeeCore/      # BungeeCord 支持
├── Velocity/        # Velocity 支持
└── docs/            # 文档
```

---

## 🧪 测试 | Testing

### 运行测试 | Running Tests

```bash
# 运行所有测试
mvn test

# 运行特定测试类
mvn test -Dtest=TestClassName

# 跳过测试编译
mvn package -DskipTests
```

### 编写测试 | Writing Tests

- 为新功能编写单元测试
- 测试类命名: `ClassNameTest`
- 测试方法命名: `testMethodName_condition_expectedResult`

```java
@Test
public void testCanEnter_playerHasPermission_returnsTrue() {
    // Arrange
    Player player = mock(Player.class);
    when(player.hasPermission(anyString())).thenReturn(true);
    
    // Act
    boolean result = adapter.canEnter(player, location);
    
    // Assert
    assertTrue(result);
}
```

---

## 📚 文档 | Documentation

### 更新文档

如果您的更改涉及：
- 新功能 → 更新 README.md
- 新命令 → 更新 docs/COMMANDS.md
- 新配置 → 更新 docs/CONFIGURATION.md
- API 变更 → 更新 docs/API.md

### 文档风格

- 使用中英文双语
- 代码示例要完整可运行
- 使用 Markdown 格式

---

## 🔍 代码审查 | Code Review

### Pull Request 检查清单

在提交 PR 前，请确保：

- [ ] 代码遵循项目规范
- [ ] 添加了必要的注释
- [ ] 更新了相关文档
- [ ] 添加了单元测试
- [ ] 所有测试通过
- [ ] 无编译警告
- [ ] 提交信息符合规范

### 审查流程

1. 自动化检查（GitHub Actions）
2. 代码审查（至少一位维护者）
3. 测试验证
4. 合并到主分支

---

## 🎯 优先级 | Priorities

我们特别欢迎以下方面的贡献：

1. **Bug 修复** - 最高优先级
2. **性能优化** - 高优先级
3. **文档改进** - 中优先级
4. **新功能** - 中优先级
5. **代码重构** - 低优先级

---

## 💬 交流 | Communication

- **GitHub Issues** - Bug 报告和功能请求
- **Pull Requests** - 代码贡献和讨论
- **Discord** - 实时交流（如果有）

---

## 📜 许可证 | License

通过贡献，您同意您的贡献将在 MIT 许可证下授权。

By contributing, you agree that your contributions will be licensed under the MIT License.

---

## 🙏 致谢 | Acknowledgments

感谢所有贡献者！您的努力让 NovaTeleport 变得更好。

Thank you to all contributors! Your efforts make NovaTeleport better.

---

## ❓ 有疑问？| Questions?

如果您有任何疑问，请：
- 创建 GitHub Issue
- 查看现有文档
- 联系维护者

If you have any questions:
- Create a GitHub Issue
- Check existing documentation
- Contact maintainers

---

Happy Coding! 🚀

Made with ❤️ by NovaMC Labs
