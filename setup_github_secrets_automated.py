#!/usr/bin/env python3
"""
Automated GitHub Secrets Setup for BYSEL using GitHub API
Requires: GitHub personal access token
"""

import subprocess
import json
import base64
import sys
import os
from pathlib import Path

def get_github_token():
    """Get GitHub token from git credentials or environment."""
    # Try to get from git credential helper
    try:
        result = subprocess.run(
            'git credential approve',
            input='protocol=https\nhost=github.com\n',
            capture_output=True,
            text=True,
            shell=True
        )
        
        # Try to retrieve
        result = subprocess.run(
            'git config --global credential.https://github.com.helper',
            capture_output=True,
            text=True,
            shell=True
        )
        
        # Check environment
        if 'GH_TOKEN' in os.environ:
            return os.environ['GH_TOKEN']
        
        print("❌ No GitHub token found.")
        print("\nTo set up secrets automatically, you need a GitHub Personal Access Token:")
        print("1. Go to: https://github.com/settings/tokens/new")
        print("2. Select scopes: 'repo', 'workflow'")
        print("3. Copy the token and run:")
        print("   $env:GH_TOKEN = 'your_token_here'")
        return None
        
    except Exception as e:
        return None

def setup_secrets_via_git_command():
    """Try using git-secrets or gh CLI if available."""
    print("🔍 Checking for GitHub CLI...")
    
    result = subprocess.run(
        "where gh",
        capture_output=True,
        text=True,
        shell=True
    )
    
    if result.returncode != 0:
        print("❌ GitHub CLI not found. Will provide manual setup guide.")
        return False
    
    print("✅ GitHub CLI found! Setting up secrets...")
    
    secrets = {
        "KEYSTORE_BASE64": Path("keystore_base64.txt").read_text().strip(),
        "KEYSTORE_PASSWORD": "BYSEL@2026",
        "KEY_ALIAS": "bysel_key",
        "KEY_PASSWORD": "BYSEL@2026",
    }
    
    success = True
    for name, value in secrets.items():
        cmd = f'gh secret set {name} --body "{value}" --repo sriharshaduppalli/BYSEL'
        result = subprocess.run(cmd, capture_output=True, text=True, shell=True)
        
        if result.returncode == 0:
            print(f"✅ {name}")
        else:
            print(f"❌ {name}: {result.stderr}")
            success = False
    
    return success

def create_manual_setup_guide():
    """Create a guide for manual setup if automation fails."""
    
    keystore_base64 = Path("keystore_base64.txt").read_text().strip()
    
    guide = f"""
╔══════════════════════════════════════════════════════════════════════╗
║                 GITHUB SECRETS MANUAL SETUP GUIDE                    ║
╚══════════════════════════════════════════════════════════════════════╝

⚠️  Automated setup not available. Follow these manual steps:

STEP 1: Open GitHub Settings
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Go to: https://github.com/sriharshaduppalli/BYSEL/settings/secrets/actions

STEP 2: Add Secret #1 - KEYSTORE_BASE64
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. Click "New repository secret"
2. Name: KEYSTORE_BASE64
3. Value (copy exactly):

{keystore_base64}

4. Click "Add secret"

STEP 3: Add Secret #2 - KEYSTORE_PASSWORD
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. Click "New repository secret"
2. Name: KEYSTORE_PASSWORD
3. Value: BYSEL@2026
4. Click "Add secret"

STEP 4: Add Secret #3 - KEY_ALIAS
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. Click "New repository secret"
2. Name: KEY_ALIAS
3. Value: bysel_key
4. Click "Add secret"

STEP 5: Add Secret #4 - KEY_PASSWORD
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. Click "New repository secret"
2. Name: KEY_PASSWORD
3. Value: BYSEL@2026
4. Click "Add secret"

STEP 6: Verify All Secrets
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

You should see 4 secrets in the list:
  ✓ KEYSTORE_BASE64
  ✓ KEYSTORE_PASSWORD
  ✓ KEY_ALIAS
  ✓ KEY_PASSWORD

NEXT STEPS AFTER SECRETS ARE ADDED:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Once all 4 secrets are configured, run these commands:

  cd "c:\\Users\\sriha\\Desktop\\Applications\\BYSEL"
  git tag -a v1.0.0 -m "Release v1.0.0"
  git push origin v1.0.0

Then watch the build: https://github.com/sriharshaduppalli/BYSEL/actions

"""
    
    return guide

def main():
    """Main execution."""
    print("🚀 BYSEL GitHub Secrets Setup")
    print("=" * 60)
    
    # Try automated setup first
    if setup_secrets_via_git_command():
        print("\n✅ All secrets configured automatically!")
        print("\n🎯 Next: Tag and push release")
        print("   git tag -a v1.0.0 -m 'Release v1.0.0'")
        print("   git push origin v1.0.0")
        return True
    
    # Fallback to manual guide
    print("\n📋 Automated setup not available. Generating manual guide...")
    guide = create_manual_setup_guide()
    print(guide)
    
    # Save guide to file
    with open("GITHUB_SECRETS_MANUAL_SETUP.txt", "w") as f:
        f.write(guide)
    print("\n📁 Saved guide to: GITHUB_SECRETS_MANUAL_SETUP.txt")
    
    return False

if __name__ == "__main__":
    try:
        success = main()
        sys.exit(0 if success else 1)
    except Exception as e:
        print(f"\n❌ Error: {e}")
        sys.exit(1)
