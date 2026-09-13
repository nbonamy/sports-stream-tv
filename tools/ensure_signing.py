#!/usr/bin/env python3
"""Create a persistent local sideload signing identity, without printing passwords."""
from pathlib import Path
import os, secrets, shutil, subprocess
root = Path(__file__).resolve().parent.parent
props = root / 'signing.properties'
key = root / 'keys/sports.jks'
if props.exists() and key.exists():
    raise SystemExit(0)
if props.exists() or key.exists():
    raise SystemExit('Incomplete signing setup. Restore both keys/sports.jks and signing.properties before continuing.')
password = secrets.token_urlsafe(32)
key.parent.mkdir(exist_ok=True, mode=0o700)
keytool = shutil.which('keytool')
if not keytool:
    raise SystemExit('A Java 17+ JDK with keytool is required.')
result = subprocess.run([keytool, '-genkeypair', '-keystore', str(key), '-alias', 'sports',
    '-keyalg', 'RSA', '-keysize', '2048', '-validity', '10000', '-dname', 'CN=Sports, O=Bonamy',
    '-storepass:env', 'SPORTS_SIGNING_PASSWORD', '-keypass:env', 'SPORTS_SIGNING_PASSWORD'],
    env={**os.environ, 'SPORTS_SIGNING_PASSWORD': password}, capture_output=True)
if result.returncode:
    raise SystemExit('Could not create the Sports signing key. Check your Java installation.')
key.chmod(0o600)
with props.open('x') as f:
    props.chmod(0o600)
    f.write(f'storeFile=keys/sports.jks\nstorePassword={password}\nkeyAlias=sports\nkeyPassword={password}\n')
print('Created the local Sports signing identity.')
