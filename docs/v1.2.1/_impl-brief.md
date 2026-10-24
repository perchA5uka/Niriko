# 批次 A 实现约定（Lead 下发）

## 工作区
C:\what i made\niriko（Kotlin + Compose，单模块 app）

## 硬性约束
1. **只改分配给你的文件**。清单外的文件一律不改，包括 README、docs、gradle 配置。
2. **不加依赖、不动 Room 表结构/迁移**（本批次 0 张卡动表）。需要 DAO 只读方法可以加，但不要改 schema。
3. **不重构、不格式化整文件、不重命名无关符号**；diff 越小越好。
4. 每个行为变更必须有 **JVM 单测**（放 app/src/test/java/com/otakup/niriko/... 对应目录），纯函数优先。
5. 注释用中文，说明「为什么」，不要写「改了什么」。
6. 允许你自己跑一次定向测试：./gradlew :app:testDebugUnitTest --tests "你的类名" --console=plain；**不要跑 assemble / 全量测试**（Lead 会统一跑，避免多进程抢 Gradle 锁）。

## 完成定义
- 代码可编译（至少你改的类与调用点自洽）。
- 新增/修改行为有测试覆盖。
- 汇报：改了哪些文件（含行数增删）、新增测试类、与任务卡的偏差、没做完的部分。

## 任务卡
- docs/v1.2.1/tasks-a-data.md（A-D1…A-D7）
- docs/v1.2.1/tasks-a-ux.md（A-X1…A-X5）
- docs/release-v1.2.1-batch-a.md（总表、顺序、裁决、A-D8）
