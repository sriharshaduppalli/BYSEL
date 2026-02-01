#!/usr/bin/env python3
"""
Create a self-signed certificate and PKCS12 keystore for Android app signing.
This script generates a keystore without requiring Java/keytool.
"""

import os
import sys
from datetime import datetime, timedelta
from cryptography import x509
from cryptography.x509.oid import NameOID
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.backends import default_backend
import zipfile
import struct
import hashlib

def create_keystore(
    keystore_path="bysel.jks",
    keystore_password="BYSEL@2026",
    key_alias="bysel_key",
    key_password="BYSEL@2026",
    validity_days=3650,  # 10 years
):
    """Create a PKCS12 keystore file."""
    
    print("🔑 Generating BYSEL signing keystore...")
    print(f"   Keystore: {keystore_path}")
    print(f"   Alias: {key_alias}")
    print(f"   Validity: {validity_days} days (~{validity_days//365} years)")
    
    # Generate private key
    print("\n📝 Generating RSA private key (2048-bit)...")
    private_key = rsa.generate_private_key(
        public_exponent=65537,
        key_size=2048,
        backend=default_backend()
    )
    
    # Generate certificate
    print("📜 Generating self-signed certificate...")
    subject = issuer = x509.Name([
        x509.NameAttribute(NameOID.COUNTRY_NAME, "IN"),
        x509.NameAttribute(NameOID.STATE_OR_PROVINCE_NAME, "India"),
        x509.NameAttribute(NameOID.LOCALITY_NAME, "India"),
        x509.NameAttribute(NameOID.ORGANIZATION_NAME, "BYSEL"),
        x509.NameAttribute(NameOID.ORGANIZATIONAL_UNIT_NAME, "Trading"),
        x509.NameAttribute(NameOID.COMMON_NAME, "BYSEL Trader"),
    ])
    
    cert = x509.CertificateBuilder().subject_name(
        subject
    ).issuer_name(
        issuer
    ).public_key(
        private_key.public_key()
    ).serial_number(
        x509.random_serial_number()
    ).not_valid_before(
        datetime.utcnow()
    ).not_valid_after(
        datetime.utcnow() + timedelta(days=validity_days)
    ).sign(private_key, hashes.SHA256(), default_backend())
    
    # Save as PKCS12 (Java keystore format)
    print(f"💾 Saving keystore to {keystore_path}...")
    from cryptography.hazmat.primitives.serialization import pkcs12
    
    p12 = pkcs12.serialize_key_and_certificates(
        name=key_alias.encode(),
        key=private_key,
        cert=cert,
        cas=None,
        encryption_algorithm=serialization.BestAvailableEncryption(keystore_password.encode()),
    )
    
    with open(keystore_path, "wb") as f:
        f.write(p12)
    
    # Get file size
    file_size = os.path.getsize(keystore_path)
    print(f"✅ Keystore created successfully!")
    print(f"   File size: {file_size:,} bytes")
    print(f"   Location: {os.path.abspath(keystore_path)}")
    
    # Print certificate details
    print(f"\n📋 Certificate Details:")
    print(f"   Subject: {cert.subject.rfc4514_string()}")
    print(f"   Serial Number: {cert.serial_number}")
    print(f"   Valid From: {cert.not_valid_before}")
    print(f"   Valid Until: {cert.not_valid_after}")
    print(f"   Fingerprint (SHA256): {cert.fingerprint(hashes.SHA256()).hex()}")
    
    print(f"\n🔐 Keystore Credentials:")
    print(f"   Store Password: {keystore_password}")
    print(f"   Key Alias: {key_alias}")
    print(f"   Key Password: {key_password}")
    
    return keystore_path

if __name__ == "__main__":
    try:
        keystore_file = create_keystore()
        print(f"\n✨ Ready for deployment!")
        print(f"   Next: Base64 encode and add to GitHub secrets")
    except Exception as e:
        print(f"\n❌ Error creating keystore: {e}")
        sys.exit(1)
