#!/usr/bin/env python3
"""
Curates live Cloudflare Web Edge Anycast subnets against Wenku8 Relay's authenticated
`POST /__diagnostics/latency` endpoint and prints or upserts the `_network_cfg.wenku8.app`
DNS TXT record via Cloudflare's API (zero third-party Python dependencies).

Usage:
  1) Dry-run (probes live relay & prints exact TXT record value to paste in Cloudflare DNS):
     python3 scripts/update_network_cfg_txt.py --dry-run

  2) Automatic Cloudflare DNS upsert (for cron / GitHub Actions):
     CF_API_TOKEN="..." CF_ZONE_ID="..." python3 scripts/update_network_cfg_txt.py --apply

  3) Emergency remote kill-switch (`en=0`, forces all clients back to System DNS):
     CF_API_TOKEN="..." CF_ZONE_ID="..." python3 scripts/update_network_cfg_txt.py --kill-switch --apply
"""

from __future__ import annotations

import argparse
import concurrent.futures
import datetime as dt
import ipaddress
import json
import os
import random
import secrets
import socket
import ssl
import sys
import urllib.error
import urllib.request
from typing import Iterable, List, Optional, Sequence, Tuple

DEFAULT_RECORD_NAME = "_network_cfg.wenku8.app"
RELAY_HOSTS = ("wenku8-relay.mewx.org", "wenku8-android.mewx.org")
PROBE_SCHEMA = "wenku8-network-probe-v1"
USER_AGENT = "wenku8-network-cfg-curator/1.0"

# Diverse candidate /24 slices across Cloudflare's global Anycast supernets.
# Each /24 is live-tested against BOTH relay hostnames before inclusion.
CANDIDATE_IPV4_SUBNETS = (
    "104.16.80.0/24",
    "104.17.120.0/24",
    "104.18.32.0/24",
    "104.19.144.0/24",
    "104.20.40.0/24",
    "104.21.48.0/24",
    "104.24.32.0/24",
    "104.25.80.0/24",
    "104.26.10.0/24",
    "104.27.64.0/24",
    "141.101.120.0/24",
    "162.159.135.0/24",
    "162.159.140.0/24",
    "162.159.152.0/24",
    "172.64.144.0/24",
    "172.66.40.0/24",
    "172.67.150.0/24",
    "172.67.200.0/24",
    "188.114.97.0/24",
    "188.114.98.0/24",
    "190.93.244.0/24",
    "190.93.245.0/24",
)

CANDIDATE_IPV6_PREFIXES = (
    "2606:4700:3030::/48",
    "2606:4700:3031::/48",
)


def probe_ip_for_host(ip_text: str, relay_host: str, timeout_sec: float = 4.0) -> bool:
    """Connects directly to `ip_text:443` with TLS SNI + cert verification for `relay_host`
    and verifies a valid `wenku8-network-probe-v1` cryptographic nonce echo."""
    nonce = secrets.token_hex(16)
    body_bytes = json.dumps(
        {"schema": PROBE_SCHEMA, "nonce": nonce},
        separators=(",", ":"),
    ).encode("utf-8")

    request_bytes = (
        f"POST /__diagnostics/latency HTTP/1.1\r\n"
        f"Host: {relay_host}\r\n"
        f"User-Agent: {USER_AGENT}\r\n"
        f"Content-Type: application/json\r\n"
        f"Accept: application/json\r\n"
        f"Accept-Encoding: identity\r\n"
        f"Content-Length: {len(body_bytes)}\r\n"
        f"Connection: close\r\n\r\n"
    ).encode("ascii") + body_bytes

    addr_obj = ipaddress.ip_address(ip_text)
    sock_family = socket.AF_INET6 if addr_obj.version == 6 else socket.AF_INET
    tls_ctx = ssl.create_default_context()

    try:
        with socket.socket(sock_family, socket.SOCK_STREAM) as raw_sock:
            raw_sock.settimeout(timeout_sec)
            raw_sock.connect((ip_text, 443))
            with tls_ctx.wrap_socket(raw_sock, server_hostname=relay_host) as tls_sock:
                tls_sock.sendall(request_bytes)
                chunks: List[bytes] = []
                total = 0
                while total < 8192:
                    data = tls_sock.recv(2048)
                    if not data:
                        break
                    chunks.append(data)
                    total += len(data)
    except OSError:
        return False

    raw_resp = b"".join(chunks)
    header_end = raw_resp.find(b"\r\n\r\n")
    if header_end < 0:
        return False
    status_line = raw_resp[: raw_resp.find(b"\r\n")].decode("latin1", errors="replace")
    if " 200 " not in status_line:
        return False

    body_part = raw_resp[header_end + 4 :]
    # Handle chunked transfer encoding if present
    headers_lower = raw_resp[:header_end].decode("latin1", errors="replace").lower()
    if "transfer-encoding: chunked" in headers_lower:
        decoded = bytearray()
        cursor = 0
        while cursor < len(body_part):
            rn = body_part.find(b"\r\n", cursor)
            if rn < 0:
                break
            size_hex = body_part[cursor:rn].split(b";", 1)[0].strip()
            if not size_hex:
                break
            chunk_len = int(size_hex, 16)
            if chunk_len == 0:
                break
            start = rn + 2
            decoded.extend(body_part[start : start + chunk_len])
            cursor = start + chunk_len + 2
        body_part = bytes(decoded)

    try:
        parsed = json.loads(body_part.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError):
        return False

    return (
        isinstance(parsed, dict)
        and parsed.get("schema") == PROBE_SCHEMA
        and parsed.get("nonce") == nonce
    )


def verify_ipv4_subnet(cidr: str, relay_hosts: Sequence[str], rng: random.Random) -> bool:
    net = ipaddress.IPv4Network(cidr, strict=True)
    # Sample boundary (.1, .254) + 1 random interior host
    interior_offset = rng.randint(2, max(2, net.num_addresses - 3))
    sample_ips = [
        str(net.network_address + 1),
        str(net.network_address + interior_offset),
        str(net.network_address + (net.num_addresses - 2)),
    ]
    for host in relay_hosts:
        for ip_text in sample_ips:
            if not probe_ip_for_host(ip_text, host):
                return False
    return True


def verify_ipv6_prefix(cidr: str, relay_hosts: Sequence[str]) -> Optional[bool]:
    """Returns True if verified, False if failed, or None if host lacks IPv6 connectivity."""
    net = ipaddress.IPv6Network(cidr, strict=True)
    sample_ip = str(net.network_address + 0x6810_0001)
    try:
        with socket.socket(socket.AF_INET6, socket.SOCK_STREAM) as test_sock:
            test_sock.settimeout(2.5)
            test_sock.connect((sample_ip, 443))
    except OSError as exc:
        if getattr(exc, "errno", None) in (101, 113, 99, 65):  # ENETUNREACH / EHOSTUNREACH / EADDRNOTAVAIL
            return None
        return False

    return all(probe_ip_for_host(sample_ip, host) for host in relay_hosts)


def build_txt_value(
    cidrs: Sequence[str],
    enabled: bool,
    ttl_valid_days: Optional[int],
    revision: Optional[str] = None,
) -> str:
    now_utc = dt.datetime.now(dt.timezone.utc)
    rev = revision or now_utc.strftime("%Y%m%d-%H%M")
    en_flag = "1" if enabled else "0"
    cidr_csv = ",".join(cidrs) if enabled else ""
    if ttl_valid_days is None:
        return f"v=wk8-txt-v1;rev={rev};en={en_flag};c={cidr_csv}"
    iat = int(now_utc.timestamp())
    exp = iat + ttl_valid_days * 86400
    return f"v=wk8-txt-v1;rev={rev};en={en_flag};iat={iat};exp={exp};c={cidr_csv}"


def cf_api_request(method: str, url: str, token: str, payload: Optional[dict] = None) -> dict:
    body = json.dumps(payload).encode("utf-8") if payload is not None else None
    req = urllib.request.Request(
        url=url,
        data=body,
        method=method,
        headers={
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/json",
            "User-Agent": USER_AGENT,
        },
    )
    with urllib.request.urlopen(req, timeout=15) as resp:
        return json.loads(resp.read().decode("utf-8"))


def upsert_cloudflare_txt_record(
    zone_id: str,
    api_token: str,
    record_name: str,
    txt_value: str,
    dns_ttl: int,
) -> None:
    base_url = f"https://api.cloudflare.com/client/v4/zones/{zone_id}/dns_records"
    list_url = f"{base_url}?type=TXT&name={record_name}"
    existing = cf_api_request("GET", list_url, api_token)
    if not existing.get("success"):
        raise RuntimeError(f"Cloudflare API list failed: {existing}")

    records = existing.get("result") or []
    # Cloudflare DNS API expects TXT content wrapped in quotes when containing semicolons
    quoted_content = f'"{txt_value}"'
    record_body = {
        "type": "TXT",
        "name": record_name,
        "content": quoted_content,
        "ttl": dns_ttl,
        "comment": "Managed by update_network_cfg_txt.py for Wenku8 Android v2 anti-blocking",
    }

    if records:
        primary_id = records[0]["id"]
        res = cf_api_request("PUT", f"{base_url}/{primary_id}", api_token, record_body)
        if not res.get("success"):
            raise RuntimeError(f"Cloudflare API update failed: {res}")
        for extra in records[1:]:
            cf_api_request("DELETE", f"{base_url}/{extra['id']}", api_token)
        print(f"Updated TXT record {record_name} (id={primary_id}, ttl={dns_ttl}s)", file=sys.stderr)
    else:
        res = cf_api_request("POST", base_url, api_token, record_body)
        if not res.get("success"):
            raise RuntimeError(f"Cloudflare API create failed: {res}")
        created_id = res["result"]["id"]
        print(f"Created TXT record {record_name} (id={created_id}, ttl={dns_ttl}s)", file=sys.stderr)


def main() -> int:
    parser = argparse.ArgumentParser(description="Verify Cloudflare edge subnets & update _network_cfg.wenku8.app TXT")
    parser.add_argument("--record-name", default=DEFAULT_RECORD_NAME, help="DNS TXT record FQDN")
    parser.add_argument("--max-v4", type=int, default=22, help="Max verified IPv4 /24 subnets to include")
    parser.add_argument("--valid-days", type=int, default=30, help="Manifest exp validity window in days (ignored when --static is set)")
    parser.add_argument("--static", action="store_true", help="Omit iat/exp timestamps (ideal for manual Cloudflare Dashboard setup without cron)")
    parser.add_argument("--dns-ttl", type=int, default=300, help="Authoritative DNS TTL in seconds")
    parser.add_argument("--include-unverified-v6", action="store_true", default=True, help="Keep default /48 IPv6 prefixes when runner lacks IPv6")
    parser.add_argument("--kill-switch", action="store_true", help="Publish emergency en=0 kill-switch")
    parser.add_argument("--dry-run", action="store_true", help="Print TXT record without calling Cloudflare API")
    parser.add_argument("--apply", action="store_true", help="Upsert TXT record via Cloudflare API (requires CF_API_TOKEN and CF_ZONE_ID)")
    args = parser.parse_args()

    if not args.dry_run and not args.apply:
        args.dry_run = True

    effective_days: Optional[int] = None if args.static else args.valid_days

    if args.kill_switch:
        txt_value = build_txt_value(cidrs=[], enabled=False, ttl_valid_days=effective_days)
    else:
        rng = random.Random()
        verified_v4: List[str] = []
        print(f"Probing {len(CANDIDATE_IPV4_SUBNETS)} candidate IPv4 /24 subnets against {RELAY_HOSTS}...", file=sys.stderr)
        with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
            future_map = {
                pool.submit(verify_ipv4_subnet, cidr, RELAY_HOSTS, random.Random(rng.randrange(1 << 30))): cidr
                for cidr in CANDIDATE_IPV4_SUBNETS
            }
            for fut in concurrent.futures.as_completed(future_map):
                cidr = future_map[fut]
                if fut.result():
                    verified_v4.append(cidr)

        # Preserve candidate tuple ordering so diverse supernets stay represented when --max-v4 < len(CANDIDATE_IPV4_SUBNETS)
        order_index = {c: idx for idx, c in enumerate(CANDIDATE_IPV4_SUBNETS)}
        verified_v4.sort(key=lambda c: order_index.get(c, 999))
        if len(verified_v4) < 4:
            print(f"ERROR: Only {len(verified_v4)} IPv4 subnets passed verification; aborting to avoid bad update.", file=sys.stderr)
            return 2

        selected_v4 = verified_v4[: args.max_v4]
        selected_v6: List[str] = []
        for v6_cidr in CANDIDATE_IPV6_PREFIXES:
            v6_ok = verify_ipv6_prefix(v6_cidr, RELAY_HOSTS)
            if v6_ok is True or (v6_ok is None and args.include_unverified_v6):
                selected_v6.append(v6_cidr)

        all_selected = selected_v4 + selected_v6
        print(
            f"Verified {len(verified_v4)}/{len(CANDIDATE_IPV4_SUBNETS)} IPv4 /24 subnets; "
            f"publishing {len(selected_v4)} IPv4 + {len(selected_v6)} IPv6 prefixes ({len(selected_v4) * 254:,}+ IPv4 hosts).",
            file=sys.stderr,
        )
        txt_value = build_txt_value(cidrs=all_selected, enabled=True, ttl_valid_days=effective_days)

    print(txt_value)

    if args.apply:
        api_token = os.environ.get("CF_API_TOKEN", "").strip()
        zone_id = os.environ.get("CF_ZONE_ID", "").strip()
        if not api_token or not zone_id:
            print("ERROR: --apply requires CF_API_TOKEN and CF_ZONE_ID environment variables.", file=sys.stderr)
            return 1
        upsert_cloudflare_txt_record(
            zone_id=zone_id,
            api_token=api_token,
            record_name=args.record_name,
            txt_value=txt_value,
            dns_ttl=args.dns_ttl,
        )

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
