#!/usr/bin/env python3
"""Production-safe backend smoke check using an existing isolated Tinc peer.

Credentials, tokens, and key material stay in memory and are never printed. The key upload
is intentionally identical to the current peer file, so a correct backend performs no HUP.
"""

import argparse
import hashlib
import io
import json
import os
import re
import subprocess
import sys
import time
import urllib.error
import urllib.request
import zipfile


IDENTIFIER = re.compile(r"^[A-Za-z0-9_]{1,64}$")
JDBC = re.compile(r"jdbc:mysql://([^/:?]+)(?::([0-9]+))?/([^?]+)")
MASTER = re.compile(
    r"(?ms)^\s*master:\s*$.*?^\s*url:\s*(\S+)\s*$.*?"
    r"^\s*username:\s*(\S*)\s*$.*?^\s*password:\s*(\S*)\s*$"
)


def request_json(url, payload, expected_status=200):
    body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    request = urllib.request.Request(
        url,
        data=body,
        method="POST",
        headers={"Content-Type": "application/json", "Cache-Control": "no-store"},
    )
    try:
        response = urllib.request.urlopen(request, timeout=20)
        data = response.read()
        if response.status != expected_status:
            raise RuntimeError("unexpected HTTP status")
        return response.status, response.headers, data
    except urllib.error.HTTPError as error:
        error.read()
        raise RuntimeError("backend request returned HTTP %d" % error.code) from None


def load_database_config(jar_path, profile):
    entry = "BOOT-INF/classes/application-druid-%s.yml" % profile
    with zipfile.ZipFile(jar_path) as archive:
        text = archive.read(entry).decode("utf-8")
    match = MASTER.search(text)
    if not match:
        raise RuntimeError("database configuration is unavailable")
    jdbc = JDBC.search(match.group(1))
    if not jdbc:
        raise RuntimeError("database URL is unsupported")
    return {
        "host": jdbc.group(1),
        "port": jdbc.group(2) or "3306",
        "database": jdbc.group(3),
        "username": match.group(2),
        "password": match.group(3),
    }


def find_existing_peer(db, net_name, config_root):
    environment = os.environ.copy()
    environment["MYSQL_PWD"] = db["password"]
    sql = (
        "SELECT node_name,password,network_ip FROM tinc_node "
        "WHERE network_name='%s' ORDER BY id" % net_name
    )
    process = subprocess.run(
        [
            "/usr/bin/mysql", "--batch", "--skip-column-names",
            "--host", db["host"], "--port", db["port"],
            "--user", db["username"], db["database"], "--execute", sql,
        ],
        env=environment,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        universal_newlines=True,
        timeout=15,
        check=False,
    )
    if process.returncode != 0:
        error_code = re.search(r"ERROR\s+([0-9]+)", process.stderr or "")
        suffix = " (mysql error %s)" % error_code.group(1) if error_code else ""
        raise RuntimeError("database lookup failed" + suffix)
    for line in process.stdout.splitlines():
        fields = line.split("\t")
        if len(fields) != 3:
            continue
        sid, password, node_ip = fields
        peer_path = os.path.join(config_root, net_name, "hosts", sid)
        if IDENTIFIER.fullmatch(sid) and password and os.path.isfile(peer_path):
            return sid, password, node_ip, peer_path
    raise RuntimeError("no existing isolated peer is available")


def normalized_sha256(data):
    return hashlib.sha256(data.replace(b"\r\n", b"\n").strip()).hexdigest()


def service_pid(net_name):
    value = subprocess.run(
        ["/usr/bin/systemctl", "show", "tinc@%s.service" % net_name,
         "--property", "MainPID", "--value"],
        stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, universal_newlines=True,
        timeout=10, check=False,
    ).stdout.strip()
    return int(value) if value.isdigit() else 0


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://127.0.0.1:8081")
    parser.add_argument("--jar", default="/www/wwwroot/ry-server-tinc/ruoyi-admin.jar")
    parser.add_argument("--profile", default="prod")
    parser.add_argument("--net-name", default="network1")
    parser.add_argument("--config-root", default="/etc/tinc")
    args = parser.parse_args()
    if not IDENTIFIER.fullmatch(args.net_name):
        raise RuntimeError("invalid network name")

    db = load_database_config(args.jar, args.profile)
    sid, password, node_ip, peer_path = find_existing_peer(db, args.net_name, args.config_root)
    peer_before = open(peer_path, "rb").read()
    stat_before = os.stat(peer_path)
    peer_sha = normalized_sha256(peer_before)
    pid_before = service_pid(args.net_name)

    started = time.monotonic()
    login_status, _, login_body = request_json(
        args.base_url.rstrip("/") + "/api/tinc/client/login",
        {"sid": sid, "password": password},
    )
    login = json.loads(login_body.decode("utf-8"))
    token = login.get("token")
    if login.get("status") != 1 or not isinstance(token, str) or not token:
        raise RuntimeError("client login failed")

    config_started = time.monotonic()
    config_status, config_headers, config_body = request_json(
        args.base_url.rstrip("/") + "/api/tinc/client/config/download",
        {"sid": sid, "token": token},
    )
    config_ms = round((time.monotonic() - config_started) * 1000)
    if not config_headers.get_content_type() == "application/zip":
        raise RuntimeError("configuration response is not a ZIP archive")
    with zipfile.ZipFile(io.BytesIO(config_body)) as archive:
        entries = sorted(archive.namelist())
    expected_entries = sorted([
        args.net_name + "/tinc.conf",
        args.net_name + "/hosts/server_master",
    ])
    if entries != expected_entries:
        raise RuntimeError("configuration ZIP contract mismatch")

    upload_started = time.monotonic()
    upload_status, upload_headers, upload_body = request_json(
        args.base_url.rstrip("/") + "/api/tinc/client/key/upload",
        {"sid": sid, "token": token, "action": "exchangeFile", "content": peer_before.decode("utf-8")},
    )
    upload_ms = round((time.monotonic() - upload_started) * 1000)
    if not upload_headers.get_content_type() == "text/plain" or not upload_body:
        raise RuntimeError("key upload response contract mismatch")

    peer_after = open(peer_path, "rb").read()
    stat_after = os.stat(peer_path)
    pid_after = service_pid(args.net_name)
    output = {
        "loginHttp": login_status,
        "configHttp": config_status,
        "configMs": config_ms,
        "zipEntries": entries,
        "uploadHttp": upload_status,
        "uploadMs": upload_ms,
        "peerShaUnchanged": peer_sha == normalized_sha256(peer_after),
        "peerMtimeUnchanged": stat_before.st_mtime_ns == stat_after.st_mtime_ns,
        "networkPidPresent": pid_after > 0,
        "networkPidUnchanged": pid_before > 0 and pid_before == pid_after,
        "totalMs": round((time.monotonic() - started) * 1000),
    }
    print(json.dumps(output, ensure_ascii=False, separators=(",", ":")))


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print(json.dumps({"ok": False, "error": str(error)}, ensure_ascii=False), file=sys.stderr)
        raise SystemExit(1)
