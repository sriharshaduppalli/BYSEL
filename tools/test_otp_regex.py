#!/usr/bin/env python3
import re

def extract_otp(message):
    pattern = re.compile(r"BYSEL.*?(?:verification code|OTP|code).*?(\d{6})", re.IGNORECASE)
    m = pattern.search(message)
    return m.group(1) if m else None

if __name__ == '__main__':
    test_messages = [
        ("Your BYSEL verification code is: 123456", "123456"),
        ("BYSEL OTP: 789012. Valid for 5 minutes.", "789012"),
        ("Hi! Your BYSEL code is 456789", "456789"),
        ("BYSEL verification code: 111222", "111222"),
        ("Random message without OTP", None),
        ("BYSEL code: abc123", None),
        ("BYSEL OTP: 12345", None),
    ]

    for msg, expected in test_messages:
        otp = extract_otp(msg)
        print(f"Message: {msg}")
        print(f" Extracted: {otp!r}  Expected: {expected!r}")
        print()
