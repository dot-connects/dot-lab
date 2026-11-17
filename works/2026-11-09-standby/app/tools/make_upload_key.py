"""Create the Play upload key for the public flavor (plan-r15 A3). Run once by the operator:

    python tools/make_upload_key.py

Writes %USERPROFILE%/.standscreen/upload.jks (PKCS12, RSA 2048, alias "upload") and upload.properties next to it,
which standby/build.gradle.kts reads to sign :standby:bundlePublicRelease. Refuses to overwrite an existing key.
The password is random, handed to keytool through an environment variable (never on the command line) and printed
nowhere. PKCS12 uses one password for the store and the key, so both properties hold the same value.
Back up the folder: losing the upload key means a key reset request to Google Play.
"""
import os
import secrets
import subprocess
import sys
from pathlib import Path

DIR = Path.home() / ".standscreen"
JKS = DIR / "upload.jks"
PROPS = DIR / "upload.properties"
KEYTOOL = Path(os.environ.get("JAVA_HOME") or r"C:\DC\tools\jdk-17") / "bin" / ("keytool.exe" if os.name == "nt" else "keytool")


def main() -> int:
    if JKS.exists() or PROPS.exists():
        print(f"make_upload_key: {DIR} already holds a key; not overwriting")
        return 1
    DIR.mkdir(mode=0o700, parents=True, exist_ok=True)
    os.chmod(DIR, 0o700)  # owner only (mostly a no-op on Windows, where the profile folder is owner-only already)
    pw = secrets.token_urlsafe(24)
    env = dict(os.environ, SB_UPLOAD_PW=pw)
    r = subprocess.run([
        str(KEYTOOL), "-genkeypair", "-storetype", "PKCS12", "-keystore", str(JKS), "-alias", "upload",
        "-keyalg", "RSA", "-keysize", "2048", "-validity", "10000", "-dname", "CN=Stand Screen",
        "-storepass:env", "SB_UPLOAD_PW", "-keypass:env", "SB_UPLOAD_PW", "-noprompt",
    ], env=env, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE, text=True)
    if r.returncode:
        print(f"make_upload_key: keytool failed (exit {r.returncode})")
        JKS.unlink(missing_ok=True)
        return 1
    os.chmod(JKS, 0o600)
    # forward slashes: a backslash is an escape in .properties files
    fd = os.open(PROPS, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, "w", encoding="utf-8", newline="\n") as f:
        f.write(f"storeFile={JKS.as_posix()}\nstorePassword={pw}\nkeyAlias=upload\nkeyPassword={pw}\n")
    print(f"make_upload_key: wrote {JKS.name} and {PROPS.name} in {DIR}; back this folder up")
    return 0


if __name__ == "__main__":
    sys.exit(main())
