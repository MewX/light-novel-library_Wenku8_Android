#!/usr/bin/env python3
"""
Curates live Cloudflare Web Edge Anycast subnets against Wenku8 Relay's authenticated
`POST /__diagnostics/latency` endpoint and prints or upserts the `_network_cfg.wenku8.app`
DNS TXT record via Cloudflare's REST API v4 (zero third-party Python dependencies).

Environment variables for `--apply`:
  - `CF_API_TOKEN`: Cloudflare API Token with `Zone -> DNS -> Edit` permission on `wenku8.app`
  - `WENKU8_APP_ZONE_ID` (or `CF_ZONE_ID`): Cloudflare Zone ID for `wenku8.app`

Usage:
  1) Dry-run (fetches official Cloudflare CIDRs, probes live relay & prints TXT record):
     python3 scripts/update_network_cfg_txt.py --dry-run

  2) Automatic Cloudflare DNS upsert (for GitHub Actions cron / workflow_dispatch):
     CF_API_TOKEN="..." WENKU8_APP_ZONE_ID="..." python3 scripts/update_network_cfg_txt.py --apply

  3) Emergency remote kill-switch (`en=0`, forces all clients back to System DNS):
     CF_API_TOKEN="..." WENKU8_APP_ZONE_ID="..." python3 scripts/update_network_cfg_txt.py --kill-switch --apply
"""

from __future__ import annotations

import argparse
import concurrent.futures
import datetime as dt
import ipaddress
import json
import os
import random
import re
import secrets
import socket
import ssl
import sys
import time
import urllib.error
import urllib.request
from typing import Dict, List, Optional, Sequence, Tuple

DEFAULT_RECORD_NAME = "_network_cfg.wenku8.app"
PRIMARY_RELAY_HOST = "wenku8-relay.mewx.org"
SECONDARY_RELAY_HOST = "wenku8-android.mewx.org"
RELAY_HOSTS = (PRIMARY_RELAY_HOST, SECONDARY_RELAY_HOST)
PROBE_SCHEMA = "wenku8-network-probe-v1"
CLOUDFLARE_IPS_V4_URL = "https://www.cloudflare.com/ips-v4"
USER_AGENT = "Mozilla/5.0 (compatible; wenku8-network-cfg-curator/1.0)"
MAX_RFC1035_TXT_CHUNK_BYTES = 255

# Pre-verified Cloudflare Web Edge /24 anchor subnets across 7 independent supernets.
# Used both as seed anchors and as offline fallback if cloudflare.com/ips-v4 is unreachable.
ANCHOR_IPV4_SUBNETS = (
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


def fetch_official_cloudflare_v4_supernets(timeout_sec: float = 8.0) -> List[ipaddress.IPv4Network]:
    req = urllib.request.Request(
        url=CLOUDFLARE_IPS_V4_URL,
        headers={"User-Agent": USER_AGENT, "Accept": "text/plain"},
    )
    try:
        with urllib.request.urlopen(req, timeout=timeout_sec) as resp:
            lines = resp.read().decode("utf-8", errors="replace").splitlines()
    except Exception as exc:
        print(f"WARN: Could not fetch {CLOUDFLARE_IPS_V4_URL} ({exc}); using anchor supernets.", file=sys.stderr)
        return []

    networks: List[ipaddress.IPv4Network] = []
    for raw in lines:
        token = raw.strip()
        if not token or token.startswith("#"):
            continue
        try:
            net = ipaddress.IPv4Network(token, strict=False)
            if net.prefixlen <= 24 and net.is_global:
                networks.append(net)
        except ValueError:
            continue
    return networks


# Cloudflare global Anycast supernets that host Web Edge / Workers (~4,000,000+ IPv4 addresses).
# Excludes Authoritative-DNS-only ranges (108.162/18, 198.41/17) that drop port 443 Web traffic.
WEB_EDGE_IPV4_SUPERNETS = (
    "104.16.0.0/13",
    "104.24.0.0/14",
    "172.64.0.0/13",
    "162.159.128.0/18",
    "188.114.96.0/22",
    "190.93.240.0/20",
    "141.101.112.0/20",
    "103.21.244.0/22",
)


def build_candidate_v4_pool(rng: random.Random, samples_per_supernet: int = 4) -> List[str]:
    """Builds a diverse candidate list mixing fresh random /24 slices from Cloudflare's
    Web Edge supernets with pre-verified anchor /24s, round-robined across supernets."""
    official_supernets = fetch_official_cloudflare_v4_supernets()
    buckets: Dict[str, List[str]] = {}

    for cidr_str in WEB_EDGE_IPV4_SUPERNETS:
        sub_supernet = ipaddress.IPv4Network(cidr_str, strict=True)
        # Ensure it remains covered by Cloudflare's official published ips-v4 list (if fetched)
        if official_supernets and not any(sub_supernet.subnet_of(pub) for pub in official_supernets):
            continue
        bucket_key = cidr_str
        num_24s = 1 << (24 - sub_supernet.prefixlen)
        k = min(samples_per_supernet, num_24s)
        chosen_indices = rng.sample(range(num_24s), k)
        base_int = int(sub_supernet.network_address)
        for idx in chosen_indices:
            subnet_addr = ipaddress.IPv4Address(base_int + (idx << 8))
            cidr = f"{subnet_addr}/24"
            buckets.setdefault(bucket_key, []).append(cidr)

    anchor_list = list(ANCHOR_IPV4_SUBNETS)
    rng.shuffle(anchor_list)
    for cidr in anchor_list:
        net = ipaddress.IPv4Network(cidr, strict=True)
        bucket_key = f"anchor-{net.network_address.packed[0]}"
        bucket = buckets.setdefault(bucket_key, [])
        if cidr not in bucket:
            bucket.append(cidr)

    # Round-robin across supernet buckets so every /13-/22 family is represented
    bucket_keys = sorted(buckets.keys())
    rng.shuffle(bucket_keys)
    interleaved: List[str] = []
    seen = set()
    made_progress = True
    while made_progress:
        made_progress = False
        for key in bucket_keys:
            items = buckets[key]
            if items:
                candidate = items.pop(0)
                if candidate not in seen:
                    seen.add(candidate)
                    interleaved.append(candidate)
                made_progress = True
    return interleaved


def probe_ip_for_host(
    ip_text: str,
    relay_host: str,
    timeout_sec: float = 2.2,
    max_429_retries: int = 2,
) -> bool:
    """Connects directly to `ip_text:443` with TLS SNI + X.509 hostname verification for
    `relay_host` and validates a cryptographic `wenku8-network-probe-v1` nonce echo."""
    addr_obj = ipaddress.ip_address(ip_text)
    sock_family = socket.AF_INET6 if addr_obj.version == 6 else socket.AF_INET
    tls_ctx = ssl.create_default_context()

    for attempt in range(max_429_retries + 1):
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
        header_text = raw_resp[:header_end].decode("latin1", errors="replace")
        status_line = header_text.splitlines()[0] if header_text else ""

        if " 429 " in status_line and attempt < max_429_retries:
            retry_after = 2.0
            for line in header_text.splitlines()[1:]:
                if line.lower().startswith("retry-after:"):
                    try:
                        retry_after = min(10.0, max(1.0, float(line.split(":", 1)[1].strip())))
                    except ValueError:
                        pass
            time.sleep(retry_after)
            continue

        if " 200 " not in status_line:
            return False

        body_part = raw_resp[header_end + 4 :]
        if "transfer-encoding: chunked" in header_text.lower():
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

    return False


def verify_ipv4_subnet(cidr: str) -> bool:
    """Verifies both boundary usable addresses (.1 and .254) of `cidr` serve `PRIMARY_RELAY_HOST`."""
    net = ipaddress.IPv4Network(cidr, strict=True)
    boundary_ips = (
        str(net.network_address + 1),
        str(net.network_address + (net.num_addresses - 2)),
    )
    for ip_text in boundary_ips:
        if not probe_ip_for_host(ip_text, PRIMARY_RELAY_HOST):
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
        if getattr(exc, "errno", None) in (101, 113, 99, 65):
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


def format_rfc1035_txt_chunks(raw_value: str) -> str:
    """Formats `raw_value` into RFC 1035 `<= 255`-byte quoted `<character-string>` chunks
    (`"chunk1" "chunk2"`) as required by Cloudflare DNS REST API v4 for long TXT records."""
    encoded = raw_value.encode("ascii")
    if not encoded:
        return '""'
    chunks = [
        encoded[i : i + MAX_RFC1035_TXT_CHUNK_BYTES].decode("ascii")
        for i in range(0, len(encoded), MAX_RFC1035_TXT_CHUNK_BYTES)
    ]
    return " ".join(f'"{chunk}"' for chunk in chunks)


def unquote_rfc1035_txt_chunks(api_content: str) -> str:
    trimmed = api_content.strip()
    if not trimmed.startswith('"'):
        return trimmed
    parts = re.findall(r'"((?:\\.|[^"\\])*)"', trimmed)
    return "".join(parts)


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
    try:
        with urllib.request.urlopen(req, timeout=20) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as http_err:
        err_body = http_err.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"Cloudflare API HTTP {http_err.code}: {err_body}") from http_err


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
    chunked_content = format_rfc1035_txt_chunks(txt_value)
    record_body = {
        "type": "TXT",
        "name": record_name,
        "content": chunked_content,
        "ttl": dns_ttl,
        "comment": "Managed by update_network_cfg_txt.py for Wenku8 Android v2 anti-blocking",
    }

    if records:
        primary_id = records[0]["id"]
        res = cf_api_request("PUT", f"{base_url}/{primary_id}", api_token, record_body)
        if not res.get("success"):
            raise RuntimeError(f"Cloudflare API update failed: {res}")
        stored_content = res.get("result", {}).get("content", "")
        for extra in records[1:]:
            cf_api_request("DELETE", f"{base_url}/{extra['id']}", api_token)
        action = f"Updated TXT record {record_name} (id={primary_id}, ttl={dns_ttl}s)"
    else:
        res = cf_api_request("POST", base_url, api_token, record_body)
        if not res.get("success"):
            raise RuntimeError(f"Cloudflare API create failed: {res}")
        created_id = res["result"]["id"]
        stored_content = res["result"].get("content", "")
        action = f"Created TXT record {record_name} (id={created_id}, ttl={dns_ttl}s)"

    roundtrip = unquote_rfc1035_txt_chunks(stored_content)
    if roundtrip != txt_value:
        raise RuntimeError(
            f"Cloudflare stored TXT mismatch! Expected {len(txt_value)} chars, got {len(roundtrip)}: {stored_content!r}"
        )
    print(f"{action} — verified {len(txt_value)} bytes stored cleanly.", file=sys.stderr)


def curate_verified_subnets(max_v4: int, include_unverified_v6: bool) -> List[str]:
    rng = random.Random()
    candidates = build_candidate_v4_pool(rng)
    print(
        f"Curating up to {max_v4} verified IPv4 /24 subnets from {len(candidates)} diverse Cloudflare candidates...",
        file=sys.stderr,
    )

    verified_v4: List[str] = []
    probed_count = 0
    batch_size = 6

    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        for start in range(0, len(candidates), batch_size):
            if len(verified_v4) >= max_v4:
                break
            batch = candidates[start : start + batch_size]
            probed_count += len(batch)
            results = list(pool.map(verify_ipv4_subnet, batch))
            for cidr, ok in zip(batch, results):
                if ok and len(verified_v4) < max_v4:
                    verified_v4.append(cidr)

    if len(verified_v4) < 4:
        raise RuntimeError(
            f"Only {len(verified_v4)} IPv4 subnets passed verification (probed {probed_count}); aborting update."
        )

    # Spot-check secondary relay hostname (`wenku8-android.mewx.org`) on the first verified subnet
    first_ip = str(ipaddress.IPv4Network(verified_v4[0], strict=True).network_address + 1)
    if not probe_ip_for_host(first_ip, SECONDARY_RELAY_HOST):
        raise RuntimeError(
            f"Spot-check on secondary host {SECONDARY_RELAY_HOST} via {first_ip} failed; aborting update."
        )

    selected_v6: List[str] = []
    for v6_cidr in CANDIDATE_IPV6_PREFIXES:
        v6_ok = verify_ipv6_prefix(v6_cidr, RELAY_HOSTS)
        if v6_ok is True or (v6_ok is None and include_unverified_v6):
            selected_v6.append(v6_cidr)

    print(
        f"Verified {len(verified_v4)} IPv4 /24 subnets (probed {probed_count}) + {len(selected_v6)} IPv6 prefixes "
        f"({len(verified_v4) * 254:,}+ IPv4 hosts).",
        file=sys.stderr,
    )
    return verified_v4 + selected_v6


def run_self_test() -> int:
    sample = "v=wk8-txt-v1;rev=20261118-1;en=1;c=" + ",".join(ANCHOR_IPV4_SUBNETS) + "," + ",".join(CANDIDATE_IPV6_PREFIXES)
    assert len(sample) > 255
    chunked = format_rfc1035_txt_chunks(sample)
    parts = re.findall(r'"([^"]*)"', chunked)
    assert len(parts) == 2 and all(len(p) <= 255 for p in parts), f"Bad chunks: {parts}"
    assert unquote_rfc1035_txt_chunks(chunked) == sample
    print("Self-test passed.", file=sys.stderr)
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="Verify Cloudflare edge subnets & update _network_cfg.wenku8.app TXT")
    parser.add_argument("--record-name", default=DEFAULT_RECORD_NAME, help="DNS TXT record FQDN")
    parser.add_argument("--max-v4", type=int, default=20, help="Max verified IPv4 /24 subnets to include")
    parser.add_argument("--valid-days", type=int, default=30, help="Manifest exp validity window in days (ignored when --static is set)")
    parser.add_argument("--static", action="store_true", help="Omit iat/exp timestamps (for manual Cloudflare Dashboard setup)")
    parser.add_argument("--dns-ttl", type=int, default=300, help="Authoritative DNS TTL in seconds")
    parser.add_argument("--include-unverified-v6", action="store_true", default=True, help="Keep default /48 IPv6 prefixes when runner lacks IPv6")
    parser.add_argument("--kill-switch", action="store_true", help="Publish emergency en=0 kill-switch")
    parser.add_argument("--dry-run", action="store_true", help="Print TXT record without calling Cloudflare API")
    parser.add_argument("--apply", action="store_true", help="Upsert TXT record via Cloudflare API (requires CF_API_TOKEN and WENKU8_APP_ZONE_ID)")
    parser.add_argument("--self-test", action="store_true", help="Run unit self-tests for RFC 1035 chunking")
    args = parser.parse_args()

    if args.self_test:
        return run_self_test()

    if not args.dry_run and not args.apply:
        args.dry_run = True

    effective_days: Optional[int] = None if args.static else args.valid_days

    if args.kill_switch:
        txt_value = build_txt_value(cidrs=[], enabled=False, ttl_valid_days=effective_days)
    else:
        try:
            all_selected = curate_verified_subnets(
                max_v4=args.max_v4,
                include_unverified_v6=args.include_unverified_v6,
            )
        except RuntimeError as exc:
            print(f"ERROR: {exc}", file=sys.stderr)
            return 2
        txt_value = build_txt_value(cidrs=all_selected, enabled=True, ttl_valid_days=effective_days)

    print(txt_value)

    if args.apply and not args.dry_run:
        api_token = os.environ.get("CF_API_TOKEN", "").strip()
        zone_id = (os.environ.get("WENKU8_APP_ZONE_ID") or os.environ.get("CF_ZONE_ID") or "").strip()
        if not api_token or not zone_id:
            print(
                "ERROR: --apply requires CF_API_TOKEN and WENKU8_APP_ZONE_ID (or CF_ZONE_ID) environment variables.",
                file=sys.stderr,
            )
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
