# Contributing to Norconex

Thank you for helping improve Norconex! To maintain legal clarity and security across our codebase, we strictly enforce the **Developer Certificate of Origin (DCO)** and **Cryptographic Commit Signing**.

Before submitting a Pull Request, please ensure your local workspace conforms to these requirements.

---

## 🏗️ Requirements for Contributions

### 1. Legal Sign-off (DCO)

Every commit message must include a `Signed-off-by:` text trailer. **By appending this line to your commit, you certify that your contribution complies with the official Developer Certificate of Origin (DCO) Version 1.1.**

👉 You can read the full, binding legal terms of this agreement right here in our repository root: **[DCO.txt](DCO.txt)**

The lowercase `-s` flag handles this automatically.

### 2. Cryptographic Security (GPG/SSH)

Every commit must be signed with a verified personal key to prevent commit spoofing and secure the integrity of the release supply chain. The uppercase `-S` flag handles this.

---

## 💻 Manual Command Line Usage

If you are committing manually from your terminal, you must combine both flags into the **same commit** so that it passes both automated GitHub security gates simultaneously:

```bash
git commit -s -S -m "Your descriptive commit message"
```

---

## 🤖 Automate Both Flags Natively

You do not need to type `-s -S` manually every time. You can instruct Git to apply both flags automatically across your entire machine for every IDE and CLI commit:

```bash
# 1. Automate the DCO Sign-off text line
git config --global format.signoff true

# 2. Automate GPG Cryptographic Key signing
git config --global commit.gpgsign true

# 3. Associate your personal GPG Key ID with Git
git config --global user.signingkey YOUR_GPG_KEY_ID
```

---

## 🛑 Fixing a Failed PR Check

If the automated GitHub protection check blocks your PR due to a missing sign-off, you can easily fix it without rewriting your code.

**If it's just the single latest commit:**

```bash
git commit --amend --signoff
git push --force-with-lease
```

**If multiple historical commits are missing sign-offs:**

```bash
git rebase --signoff origin/main
git push --force-with-lease
```
