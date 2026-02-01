#!/usr/bin/env python3
"""
Automatic GitHub Secrets Setup for BYSEL
This script sets up all required secrets for the CI/CD pipeline.
"""

import subprocess
import sys
import os
from pathlib import Path

def run_command(cmd, description):
    """Run a command and report results."""
    print(f"⏳ {description}...")
    try:
        result = subprocess.run(cmd, shell=True, capture_output=True, text=True)
        if result.returncode == 0:
            print(f"✅ {description}")
            return True
        else:
            print(f"❌ {description}")
            print(f"   Error: {result.stderr}")
            return False
    except Exception as e:
        print(f"❌ {description}")
        print(f"   Error: {e}")
        return False

def setup_github_secrets():
    """Set up GitHub secrets for BYSEL repository."""
    
    print("🔐 BYSEL GitHub Secrets Setup")
    print("=" * 50)
    
    # Check if GitHub CLI is installed
    if not run_command("gh --version", "Checking GitHub CLI"):
        print("\n❌ GitHub CLI not installed. Install from: https://cli.github.com/")
        print("   Or manually add secrets at: https://github.com/sriharshaduppalli/BYSEL/settings/secrets/actions")
        return False
    
    # Get repository info
    print("\n📍 Repository: sriharshaduppalli/BYSEL")
    
    # Check authentication
    print("\n🔑 Checking GitHub authentication...")
    if not run_command("gh auth status", "GitHub authentication check"):
        print("\n⚠️  Not authenticated. Run: gh auth login")
        return False
    
    # Read keystore file and encode to Base64
    keystore_path = Path("bysel.jks")
    if not keystore_path.exists():
        print(f"\n❌ Keystore not found: {keystore_path}")
        print("   Run: python create_keystore.py")
        return False
    
    print(f"\n📝 Reading keystore from {keystore_path}...")
    import base64
    with open(keystore_path, "rb") as f:
        keystore_base64 = base64.b64encode(f.read()).decode()
    print(f"✅ Keystore encoded ({len(keystore_base64)} characters)")
    
    # Secrets to set
    secrets = {
        "KEYSTORE_BASE64": keystore_base64,
        "KEYSTORE_PASSWORD": "BYSEL@2026",
        "KEY_ALIAS": "bysel_key",
        "KEY_PASSWORD": "BYSEL@2026",
    }
    
    print("\n🔐 Setting GitHub Secrets...")
    print("-" * 50)
    
    success_count = 0
    for secret_name, secret_value in secrets.items():
        cmd = f'gh secret set {secret_name} --body "{secret_value}" --repo sriharshaduppalli/BYSEL'
        if run_command(cmd, f"Setting {secret_name}"):
            success_count += 1
    
    print("-" * 50)
    print(f"\n✨ Results: {success_count}/{len(secrets)} secrets configured")
    
    if success_count == len(secrets):
        print("\n✅ All secrets set successfully!")
        print("\n📋 Next Steps:")
        print("   1. Tag release: git tag -a v1.0.0 -m 'Release v1.0.0'")
        print("   2. Push tag: git push origin v1.0.0")
        print("   3. Monitor: https://github.com/sriharshaduppalli/BYSEL/actions")
        return True
    else:
        print("\n⚠️  Some secrets failed. Try manual setup at:")
        print("   https://github.com/sriharshaduppalli/BYSEL/settings/secrets/actions")
        return False

if __name__ == "__main__":
    try:
        success = setup_github_secrets()
        sys.exit(0 if success else 1)
    except Exception as e:
        print(f"\n❌ Error: {e}")
        sys.exit(1)
