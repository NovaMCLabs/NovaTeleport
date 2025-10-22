# 语言文件键值扩展
# Language Keys Extension

## 需要添加到 lang/zh_CN.yml 和 lang/en_US.yml 的新键

### Towny 相关
```yaml
towny:
  not_enabled: "&c城镇传送功能未启用"
  no_town: "&c你还没有加入任何城镇"
  no_spawn: "&c该城镇没有设置出生点"
  teleported_to_town: "&a已传送到城镇 &e{0}"
  town_not_found: "&c找不到城镇: &e{0}"
  town_private: "&c该城镇是私有的，你没有权限传送"
```

### 工会相关
```yaml
guild:
  not_enabled: "&c工会功能未启用"
  no_guild: "&c你还没有加入任何工会"
  warp_created: "&a工会传送点 &e{0} &a已创建"
  warp_deleted: "&a工会传送点 &e{0} &a已删除"
  warp_not_found: "&c找不到工会传送点: &e{0}"
  warp_limit_reached: "&c工会传送点数量已达上限"
  headquarters_set: "&a工会据点已设置"
  not_admin: "&c只有工会管理员才能执行此操作"
  same_guild_only: "&c只能传送给同工会成员"
```

### 付费传送点相关
```yaml
toll:
  created: "&a付费传送点 &e{0} &a已创建，价格: &6{1}"
  deleted: "&a付费传送点 &e{0} &a已删除"
  price_updated: "&a传送点 &e{0} &a的价格已更新为 &6{1}"
  insufficient_funds: "&c你的余额不足，需要 &6{0}"
  owner_received: "&a你收到了 &6{0} &a的传送费用"
  teleported_toll: "&a已传送到 &e{0}&a，花费 &6{1}"
  limit_reached: "&c你的付费传送点数量已达上限"
```

### 时空领航员相关
```yaml
navigator:
  request_sent: "&a领航请求已发送给 &e{0}&a，酬金: &6{1}"
  request_received: "&e{0} &a请求你的领航服务！酬金: &6{1} &7- &a/nav accept &7或 &c/nav deny"
  task_accepted: "&a你接受了来自 &e{0} &a的领航任务"
  task_denied: "&c你拒绝了领航任务"
  beacon_found: "&a发现目标信标！手持谐振器右键长按稳定信标"
  stabilizing: "&e稳定信标中... &a[&f{0}%&a]"
  stabilized: "&a信标已稳定！虫洞即将开启"
  wormhole_opened: "&b虫洞已开启！请进入虫洞传送"
  teleported: "&a领航完成！你已抵达目的地"
  reward_received: "&a任务完成！获得酬金 &6{0}"
  rift_bonus: "&6维度裂隙任务奖励加倍！"
  space_shard_obtained: "&d你获得了一个空间碎片！"
  locked: "&c你正在执行领航任务，无法使用其他传送功能"
```

### 维度裂隙相关
```yaml
rift:
  announced: "&5&l[维度裂隙] &d在 &f{0} &d附近侦测到不稳定的维度裂隙！"
  entered: "&d你进入了维度裂隙区域"
  left: "&a你离开了维度裂隙区域"
  hijacked: "&c你的传送被裂隙劫持了！"
  pocket_dimension: "&d&l你被传送到了神秘的口袋维度！"
  warning: "&c警告！在裂隙中使用传送非常危险"
```

### 传送技能相关
```yaml
skills:
  exp_gained: "&a传送经验 +{0}"
  level_up: "&6&l传送技能升级！ &e当前等级: {0}"
  unlock: "&a解锁新能力: &e{0}"
  current_level: "&a当前传送技能等级: &e{0}"
  current_exp: "&a当前经验: &e{0}/{1}"
```

### 法力系统相关
```yaml
mana:
  insufficient: "&c法力不足！需要 &e{0}&c，当前 &e{1}"
  cost: "&7消耗法力: &b{0}"
  regen: "&b法力恢复中... &e{0}/{1}"
```

### 传送日志相关
```yaml
log:
  title: "&6{0} &a的传送记录"
  entry: "&7{0} &f- &e{1} &f-> &e{2}"
  rewound: "&a已将 &e{0} &a传送回 &7{1}"
  no_records: "&c没有找到传送记录"
```

### 通用
```yaml
command:
  player_only: "&c此命令只能由玩家执行"
  no_permission: "&c你没有权限执行此命令"
  reload:
    success: "&a配置已重新加载"
```
