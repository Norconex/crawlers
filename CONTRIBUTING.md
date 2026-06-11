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

## 🔑 Creating a GPG Key (First-Time Setup)

If you do not already have a GPG key, here is the quickest way to create one. For the full walkthrough, see **[GitHub's official GPG documentation](https://docs.github.com/en/authentication/managing-commit-signature-verification/generating-a-new-gpg-key)**.

**1. Generate a key**

```bash
gpg --full-generate-key
```

When prompted: choose **RSA and RSA**, size **4096**, and enter the name and email that match your Git `user.name` / `user.email`.

**2. Find your key ID**

```bash
gpg --list-secret-keys --keyid-format=long
```

Your key ID is the value after the slash on the `sec` line — for example, `3AA5C34371567BD2` in `sec rsa4096/3AA5C34371567BD2`.

**3. Configure Git to use it**

```bash
git config --global user.signingkey YOUR_KEY_ID
git config --global commit.gpgsign true
git config --global format.signoff true
```

**4. Add your public key to GitHub**

```bash
gpg --armor --export YOUR_KEY_ID
```

Copy the output and paste it into **GitHub → Settings → SSH and GPG keys → New GPG key**.

**Windows users:** also run this so IDEs can find the GPG binary (replace path with actual install location):

```bash
git config --global gpg.program "C:\Program Files\GnuPG\bin\gpg.exe"
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
