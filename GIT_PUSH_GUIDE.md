# Git 推送指南

本文档说明如何向 GitHub 和 Gitee 推送代码。

## 📦 远程仓库配置

| 平台 | 远程名称 | 地址 |
|------|----------|------|
| GitHub | `origin` | `git@github.com:ChiangyangNPU/MattingDemo-Android.git` |
| Gitee | `gitee` | `git@gitee.com:chiangyangNPU/matting-demo-android.git` |

---

## 🚀 推送方式

### **1. 单独推送到 GitHub**

```bash
git push origin main
```

### **2. 单独推送到 Gitee**

```bash
git push gitee main
```

### **3. 同时推送到两个平台**

#### **方式一：使用 pushall 别名（推荐）**

本仓库已配置一键推送别名（仅本机生效，见下方"别名配置"）：

```bash
git pushall
```

#### **方式二：分别推送**

```bash
git push origin main
git push gitee main
```

#### **⚠️ 注意：`git push --all` 不会同时推两个平台**

```bash
git push --all   # ❌ 只推送到默认远端 origin（GitHub）！
```

不带远端名的 `git push --all` 等价于 `git push origin --all`，只会推
默认远程仓库。2026-08-29 曾因此导致 Gitee 落后三个提交，务必使用
`git pushall` 或分别指定远端推送。

#### **pushall 别名配置**

别名存在本机 `.git/config` 里，换机器后需重新配置：

```bash
git config alias.pushall '!git push origin main && git push gitee main'
```

#### **方式三：配置远端多推送地址（高级）**

```bash
# 配置 origin 同时推送到两个仓库
git remote set-url --add --push origin git@github.com:ChiangyangNPU/MattingDemo-Android.git
git remote set-url --add --push origin git@gitee.com:chiangyangNPU/matting-demo-android.git

# 之后只需要
git push origin main
```

> 注意：此方式会让 `origin` 的推送地址列表替换默认值，且只对显式
> 指定 `origin` 的推送生效，`git push --all` 仍然只推第一个地址。
> 更推荐使用上面的 `pushall` 别名。

---

## 📥 拉取代码

### **从 GitHub 拉取**
```bash
git pull origin main
```

### **从 Gitee 拉取**
```bash
git pull gitee main
```

---

## 🔍 查看远程仓库信息

### **查看所有远程仓库**
```bash
git remote -v
```

### **查看特定远程仓库详情**
```bash
# 查看 GitHub 仓库信息
git remote show origin

# 查看 Gitee 仓库信息
git remote show gitee
```

---

## 📝 完整工作流程示例

### **场景1：修改代码后推送到两个平台**

```bash
# 1. 查看修改状态
git status

# 2. 添加修改
git add .

# 3. 提交修改
git commit -m "feat: 添加新功能"

# 4. 一键推送到 GitHub 和 Gitee
git pushall
```

### **场景2：只推送到 GitHub**

```bash
git add .
git commit -m "fix: 修复 bug"
git push origin main
```

### **场景3：只推送到 Gitee**

```bash
git add .
git commit -m "docs: 更新文档"
git push gitee main
```

---

## ⚠️ 注意事项

### **1. 保持两个仓库同步**
- 建议每次修改后都用 `git pushall` 推送到两个平台
- `git push --all` / `git push` 不带远端名时**只推 origin（GitHub）**，
  不要用它替代双平台推送
- 可以用 `git ls-remote gitee refs/heads/main` 对比本地 HEAD，
  检查 Gitee 是否落后

### **2. 分支管理**
- 当前主分支：`main`
- 如果创建新分支，记得也推送到两个平台：
  ```bash
  git push origin feature-branch
  git push gitee feature-branch
  ```

### **3. SSH 密钥**
- 两个平台都已配置 SSH 密钥
- 无需输入密码，直接推送

### **4. 冲突处理**
- 如果两个仓库出现冲突，以 GitHub 为主
- 可以强制同步：
  ```bash
  git push --force origin main
  git push --force gitee main
  ```

---

## 🔗 仓库地址

| 平台 | 地址 |
|------|------|
| GitHub | https://github.com/ChiangyangNPU/MattingDemo-Android |
| Gitee | https://gitee.com/chiangyangNPU/matting-demo-android |

---

## 🆘 常见问题

### **Q1: 推送失败怎么办？**
```bash
# 检查远程仓库配置
git remote -v

# 测试 SSH 连接
ssh -T git@github.com
ssh -T git@gitee.com
```

### **Q2: 如何删除远程仓库？**
```bash
# 删除 GitHub 远程仓库（谨慎操作）
git remote remove origin

# 删除 Gitee 远程仓库（谨慎操作）
git remote remove gitee
```

### **Q3: 如何重新添加远程仓库？**
```bash
# 添加 GitHub
git remote add origin git@github.com:ChiangyangNPU/MattingDemo-Android.git

# 添加 Gitee
git remote add gitee git@gitee.com:chiangyangNPU/matting-demo-android.git
```

---

## 📚 更多 Git 命令

### **查看分支**
```bash
git branch -a
```

### **切换分支**
```bash
git checkout branch-name
```

### **创建新分支**
```bash
git checkout -b feature-branch
```

### **合并分支**
```bash
git checkout main
git merge feature-branch
```

### **查看提交历史**
```bash
git log --oneline
```

---

**作者：** chiangyang
**更新日期：** 2026-08-29
