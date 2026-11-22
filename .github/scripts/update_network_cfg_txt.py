#!/usr/bin/env python3
"""
Curates live Cloudflare Web Edge Anycast subnets against Wenku8 Relay's authenticated
`POST /__diagnostics/latency` endpoint and safely updates the `_network_cfg.wenku8.app`
DNS TXT record via Cloudflare REST API v4 (zero third-party Python dependencies).

========================================================================================
ARCHITECTURE & END-TO-END ANTI-BLOCKING DESIGN
========================================================================================
1. Why `_network_cfg.wenku8.app` DNS TXT + CIDR Subnets?
   - Static single-IP pools in an APK can be enumerated and blocked, while `raw.githubusercontent.com`
     is blocked in mainland China.
   - Domestic pure-IP HTTPS JSON DoH resolvers (`223.5.5.5`, `1.12.12.12`, `223.6.6.6`, `120.53.53.53`)
     serve valid TLS certificates with numeric IP SANs (requiring ZERO bootstrap OS DNS) and
     resolve authoritative `TXT` records from `wenku8.app` seamlessly inside mainland China.
   - Instead of individual IPs, the `TXT` record publishes ~20 verified Cloudflare `/24` IPv4
     subnets + `/48` IPv6 prefixes (`~5,000+` rotated IPv4 addresses per day out of Cloudflare's
     `4,000,000+` Web Edge Anycast pool). Android `:app-v2` (`ManifestRepository` + `IpLiteralParser`)
     randomly samples `.1..254` host addresses inside each subnet per tournament.

2. Defense-in-Depth Pre-Publish Validation Gates (`validate_txt_record_before_publish`):
   Before ANY `POST` / `PUT` / `DELETE` call is sent to Cloudflare's DNS API, the candidate TXT
   payload MUST pass all of the following hard gates (aborting non-zero on any violation so the
   previously published 30-day DNS record stays untouched):
   - Record target guard: FQDN must match `_network_cfg.<zone>` under `wenku8.app` (never touches
     apex `@`, `www`, MX, SPF, DKIM, or DMARC records), and existing Cloudflare records are checked
     for `type == "TXT"` and exact `name` equality before update.
   - RFC 1035 wire & chunking check: pure printable ASCII without spaces/quotes/backslashes,
     total size `<= 1,100` bytes (avoids UDP/DoH truncation), split into `<= 255`-byte quoted
     `<character-string>` chunks with 100% lossless round-trip unquoting.
   - Exact Android `:app-v2` `wk8-txt-v1` schema parity: only known keys (`v`, `rev`, `en`,
     `hosts`, `min`, `ro`, `iat`, `exp`, `fb`, `c`), no duplicate keys, no empty segments,
     `iat` within 3 minutes of current UTC wall clock, `1h <= exp - iat <= 45d`, and monotonic
     anti-rollback (`new_iat >= existing_live_iat`) so Android's `RoutingLocalStore` never rejects
     an update as a replay/rollback.
   - Quorum, diversity & Cloudflare ASN ownership proof (when `en=1`):
     * Requires `>= 8` distinct IPv4 subnets across `>= 3` independent `/16` blocks (`<= 64` total).
     * Zero duplicate or overlapping CIDRs; strict network bitmask (`strict=True`, zero host bits).
     * Every subnet must be globally routable public unicast (mirrors Android `IpLiteralParser`).
     * Every IPv4 and IPv6 subnet MUST be a mathematical subset (`subnet_of`) of Cloudflare's
       official published supernets (`cloudflare.com/ips-v4` & `ips-v6`). Arbitrary third-party,
       private, reserved, or documentation IPs can NEVER be written to DNS.

3. Operational Modes:
   - Dry-run (probes live relay & runs full validator without touching Cloudflare DNS):
     `python3 .github/scripts/update_network_cfg_txt.py --dry-run`
   - Scheduled / CI apply (requires `CF_API_TOKEN` and `WENKU8_APP_ZONE_ID`):
     `CF_API_TOKEN="..." WENKU8_APP_ZONE_ID="..." python3 .github/scripts/update_network_cfg_txt.py --apply`
   - Emergency remote kill-switch (`en=0`, immediately reverts all `:app-v2` clients to System DNS):
     `CF_API_TOKEN="..." WENKU8_APP_ZONE_ID="..." python3 .github/scripts/update_network_cfg_txt.py --kill-switch --apply`
   - Offline unit & negative-validation self-test:
     `python3 .github/scripts/update_network_cfg_txt.py --self-test`
"""

from __future__ import annotations

import argparse
import concurrent.futures
import dataclasses
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
from typing import Callable, Dict, List, Optional, Sequence, Set, Tuple, Union

DEFAULT_RECORD_NAME = "_network_cfg.wenku8.app"
ALLOWED_ZONE_SUFFIX = ".wenku8.app"
PRIMARY_RELAY_HOST = "wenku8-relay.mewx.org"
SECONDARY_RELAY_HOST = "mewx-android.wenku8.app"
RELAY_HOSTS = (PRIMARY_RELAY_HOST, SECONDARY_RELAY_HOST)

TXT_SCHEMA_V1 = "wk8-txt-v1"
PROBE_SCHEMA = "wenku8-network-probe-v1"
CLOUDFLARE_IPS_V4_URL = "https://www.cloudflare.com/ips-v4"
CLOUDFLARE_IPS_V6_URL = "https://www.cloudflare.com/ips-v6"
USER_AGENT = "Mozilla/5.0 (compatible; wenku8-network-cfg-curator/1.1)"

PRIMARY_PROBE_TIMEOUT_SEC = 2.5
SECONDARY_SPOT_CHECK_TIMEOUT_SEC = 5.0
SECONDARY_SPOT_CHECK_MAX_SUBNETS = 3
SECONDARY_SPOT_CHECK_TRANSIENT_RETRIES = 1
TRANSIENT_HTTP_STATUS_CODES = frozenset({429, 500, 502, 503, 504, 520, 521, 522, 523, 524})

MAX_RFC1035_TXT_CHUNK_BYTES = 255
MAX_TOTAL_TXT_WIRE_BYTES = 1100
MAX_ANDROID_CANDIDATE_COUNT = 64
MIN_REQUIRED_IPV4_SUBNETS = 8
MIN_REQUIRED_IPV4_SLASH16_FAMILIES = 3
MIN_VALIDITY_SECONDS = 3600
MAX_VALIDITY_SECONDS = 45 * 86400
MAX_CLOCK_SKEW_SECONDS = 180

RECORD_NAME_REGEX = re.compile(r"^_network_cfg\.[a-z0-9-]+(?:\.[a-z0-9-]+)+$")
REVISION_REGEX = re.compile(r"^[A-Za-z0-9._-]{1,40}$")
CANDIDATE_ID_REGEX = re.compile(r"^[A-Za-z0-9._-]{1,40}$")
ALLOWED_TXT_KEYS = frozenset({"v", "rev", "en", "hosts", "min", "ro", "iat", "exp", "fb", "c"})

# Official Cloudflare published IPv4 & IPv6 supernets (floor allowlist supplemented by live fetch).
KNOWN_CLOUDFLARE_V4_SUPERNETS = (
    "173.245.48.0/20",
    "103.21.244.0/22",
    "103.22.200.0/22",
    "103.31.4.0/22",
    "141.101.64.0/18",
    "108.162.192.0/18",
    "190.93.240.0/20",
    "188.114.96.0/20",
    "197.234.240.0/22",
    "198.41.128.0/17",
    "162.158.0.0/15",
    "104.16.0.0/13",
    "104.24.0.0/14",
    "172.64.0.0/13",
    "131.0.72.0/22",
)

KNOWN_CLOUDFLARE_V6_SUPERNETS = (
    "2400:cb00::/32",
    "2606:4700::/32",
    "2803:f800::/32",
    "2405:b500::/32",
    "2405:8100::/32",
    "2a06:98c0::/29",
    "2c0f:f248::/32",
)

# Cloudflare global Anycast supernets that host Web Edge / Workers (~4,000,000+ IPv4 addresses).
# Excludes Authoritative-DNS-only ranges (108.162/18, 198.41/17) that drop TCP 443 Web traffic.
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

# Pre-verified Cloudflare Web Edge /24 anchor subnets across 7 independent supernets.
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


class TxtValidationError(ValueError):
    """Raised when a candidate DNS TXT payload violates any safety, schema, or ownership invariant."""


def fetch_official_cloudflare_supernets(
    url: str,
    version: int,
    timeout_sec: float = 8.0,
) -> List[Union[ipaddress.IPv4Network, ipaddress.IPv6Network]]:
    req = urllib.request.Request(
        url=url,
        headers={"User-Agent": USER_AGENT, "Accept": "text/plain"},
    )
    try:
        with urllib.request.urlopen(req, timeout=timeout_sec) as resp:
            lines = resp.read().decode("utf-8", errors="replace").splitlines()
    except Exception as exc:
        print(f"WARN: Could not fetch {url} ({exc}); falling back to pinned Cloudflare supernets.", file=sys.stderr)
        return []

    networks: List[Union[ipaddress.IPv4Network, ipaddress.IPv6Network]] = []
    for raw in lines:
        token = raw.strip()
        if not token or token.startswith("#"):
            continue
        try:
            net = ipaddress.ip_network(token, strict=False)
            if net.version == version and net.is_global:
                networks.append(net)
        except ValueError:
            continue
    return networks


def resolve_cloudflare_allowlists(
    fetch_live: bool = True,
) -> Tuple[List[ipaddress.IPv4Network], List[ipaddress.IPv6Network]]:
    """Returns authoritative Cloudflare IPv4 & IPv6 supernet allowlists.
    When `fetch_live` is True and `cloudflare.com/ips-v*` responds with a healthy list, uses the
    live list; otherwise uses `KNOWN_CLOUDFLARE_V*_SUPERNETS`."""
    live_v4 = (
        [n for n in fetch_official_cloudflare_supernets(CLOUDFLARE_IPS_V4_URL, 4) if isinstance(n, ipaddress.IPv4Network)]
        if fetch_live
        else []
    )
    live_v6 = (
        [n for n in fetch_official_cloudflare_supernets(CLOUDFLARE_IPS_V6_URL, 6) if isinstance(n, ipaddress.IPv6Network)]
        if fetch_live
        else []
    )
    v4_list = live_v4 if len(live_v4) >= 8 else [ipaddress.IPv4Network(c, strict=True) for c in KNOWN_CLOUDFLARE_V4_SUPERNETS]
    v6_list = live_v6 if len(live_v6) >= 4 else [ipaddress.IPv6Network(c, strict=True) for c in KNOWN_CLOUDFLARE_V6_SUPERNETS]
    return v4_list, v6_list


def is_android_public_routable_unicast(addr: Union[ipaddress.IPv4Address, ipaddress.IPv6Address]) -> bool:
    """Exact mirror of Android `:app-v2` `IpLiteralParser.isPublicRoutableUnicast`."""
    if isinstance(addr, ipaddress.IPv4Address):
        b0, b1, b2, _ = addr.packed
        if b0 == 0 or b0 == 10 or b0 == 127:
            return False
        if b0 == 100 and (b1 & 0xC0) == 64:  # 100.64.0.0/10 CGNAT
            return False
        if b0 == 169 and b1 == 254:  # 169.254.0.0/16 Link-local
            return False
        if b0 == 172 and 16 <= b1 <= 31:  # 172.16.0.0/12 Private
            return False
        if b0 == 192 and b1 == 0 and b2 == 0:  # 192.0.0.0/24 IETF protocol
            return False
        if b0 == 192 and b1 == 0 and b2 == 2:  # 192.0.2.0/24 TEST-NET-1
            return False
        if b0 == 192 and b1 == 168:  # 192.168.0.0/16 Private
            return False
        if b0 == 198 and b1 in (18, 19):  # 198.18.0.0/15 Benchmarking
            return False
        if b0 == 198 and b1 == 51 and b2 == 100:  # 198.51.100.0/24 TEST-NET-2
            return False
        if b0 == 203 and b1 == 0 and b2 == 113:  # 203.0.113.0/24 TEST-NET-3
            return False
        if b0 >= 224:  # Multicast 224/4 & Reserved 240/4 & Broadcast
            return False
        return addr.is_global

    # IPv6
    packed = addr.packed
    b0, b1 = packed[0], packed[1]
    if addr.is_unspecified or addr.is_loopback or addr.is_multicast or addr.is_link_local or addr.is_site_local:
        return False
    if packed[:12] == b"\x00" * 10 + b"\xff\xff" or packed[:12] == b"\x00" * 12:
        return False  # IPv4-mapped or IPv4-compatible
    if (b0 & 0xFE) == 0xFC:  # fc00::/7 ULA
        return False
    if b0 == 0x20 and b1 == 0x01 and packed[2] == 0x0D and packed[3] == 0xB8:  # 2001:db8::/32 doc
        return False
    if b0 == 0x20 and b1 == 0x02:  # 2002::/16 6to4
        return False
    if b0 == 0x20 and b1 == 0x01 and packed[2] == 0x00 and packed[3] == 0x00:  # 2001::/32 Teredo
        return False
    return addr.is_global


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
    """Strict RFC 1035 character-string unquoter matching Android `ManifestRepository.unquoteTxtData`."""
    trimmed = api_content.strip()
    if not trimmed.startswith('"'):
        return trimmed
    out: List[str] = []
    idx = 0
    n = len(trimmed)
    while idx < n:
        while idx < n and trimmed[idx].isspace():
            idx += 1
        if idx >= n:
            break
        if trimmed[idx] != '"':
            raise TxtValidationError(f"Malformed RFC 1035 TXT chunk at offset {idx}: expected '\"'")
        idx += 1
        chunk_chars: List[str] = []
        closed = False
        while idx < n:
            ch = trimmed[idx]
            if ch == "\\":
                if idx + 1 >= n:
                    raise TxtValidationError("Trailing backslash in RFC 1035 TXT chunk")
                chunk_chars.append(trimmed[idx + 1])
                idx += 2
            elif ch == '"':
                closed = True
                idx += 1
                break
            else:
                chunk_chars.append(ch)
                idx += 1
        if not closed:
            raise TxtValidationError("Unclosed quote in RFC 1035 TXT chunk")
        chunk_str = "".join(chunk_chars)
        chunk_bytes = chunk_str.encode("utf-8")
        if len(chunk_bytes) > MAX_RFC1035_TXT_CHUNK_BYTES:
            raise TxtValidationError(
                f"RFC 1035 TXT chunk exceeds {MAX_RFC1035_TXT_CHUNK_BYTES} bytes ({len(chunk_bytes)} bytes)"
            )
        out.append(chunk_str)
    return "".join(out)


def extract_iat_from_existing_txt(raw_api_content: str) -> Optional[int]:
    """Extracts `iat` epoch seconds from an existing Cloudflare TXT record (if valid `wk8-txt-v1`)."""
    try:
        unquoted = unquote_rfc1035_txt_chunks(raw_api_content)
    except TxtValidationError:
        return None
    kv: Dict[str, str] = {}
    for part in unquoted.split(";"):
        if "=" in part:
            k, v = part.split("=", 1)
            kv[k.strip().lower()] = v.strip()
    if kv.get("v") != TXT_SCHEMA_V1:
        return None
    raw_iat = kv.get("iat")
    if raw_iat is not None and raw_iat.isdigit():
        return int(raw_iat)
    return None


def validate_record_name_target(record_name: str, allow_custom_record_name: bool = False) -> None:
    """Prevents accidental overwrite of apex, www, mail, or unrelated DNS records."""
    normalized = record_name.strip().rstrip(".").lower()
    if not RECORD_NAME_REGEX.match(normalized):
        raise TxtValidationError(
            f"Refusing unsafe DNS record name {record_name!r}: must match '_network_cfg.<domain>'."
        )
    if not allow_custom_record_name and (
        normalized != DEFAULT_RECORD_NAME and not normalized.endswith(ALLOWED_ZONE_SUFFIX)
    ):
        raise TxtValidationError(
            f"Refusing unexpected zone in {record_name!r}: expected {DEFAULT_RECORD_NAME!r} "
            f"(pass --allow-custom-record-name only for isolated test zones)."
        )


def validate_txt_record_before_publish(
    txt_value: str,
    *,
    record_name: str = DEFAULT_RECORD_NAME,
    dns_ttl: int = 300,
    require_timestamps: bool = True,
    require_fresh_iat: bool = True,
    now_epoch: Optional[int] = None,
    previous_iat: Optional[int] = None,
    min_ipv4_subnets: int = MIN_REQUIRED_IPV4_SUBNETS,
    min_ipv4_slash16_families: int = MIN_REQUIRED_IPV4_SLASH16_FAMILIES,
    allowed_v4_supernets: Optional[Sequence[ipaddress.IPv4Network]] = None,
    allowed_v6_supernets: Optional[Sequence[ipaddress.IPv6Network]] = None,
    allow_custom_record_name: bool = False,
) -> Dict[str, object]:
    """
    Comprehensive pre-publish validator ensuring `txt_value` is 100% compliant with Android
    `:app-v2`'s `ManifestRepository` + `IpLiteralParser`, Cloudflare REST API v4 RFC 1035 limits,
    minimum subnet quorum/diversity, monotonic anti-rollback, and official Cloudflare IP ownership.

    Raises `TxtValidationError` if ANY check fails.
    """
    validate_record_name_target(record_name, allow_custom_record_name=allow_custom_record_name)

    if not (60 <= dns_ttl <= 86400):
        raise TxtValidationError(f"DNS TTL {dns_ttl}s out of safe bounds [60, 86400].")

    if not txt_value:
        raise TxtValidationError("TXT record value must not be empty.")

    try:
        raw_bytes = txt_value.encode("ascii")
    except UnicodeEncodeError as exc:
        raise TxtValidationError(f"TXT record contains non-ASCII characters: {exc}") from exc

    if len(raw_bytes) > MAX_TOTAL_TXT_WIRE_BYTES:
        raise TxtValidationError(
            f"TXT record length ({len(raw_bytes)} bytes) exceeds safe DoH/EDNS0 ceiling ({MAX_TOTAL_TXT_WIRE_BYTES} bytes)."
        )

    for ch in txt_value:
        code = ord(ch)
        if code <= 0x20 or code >= 0x7F or ch in ('"', "\\"):
            raise TxtValidationError(
                f"TXT record contains forbidden whitespace/quote/control character {ch!r} (0x{code:02x})."
            )

    # Verify RFC 1035 <=255-byte chunk formatting & lossless round-trip reassembly
    chunked = format_rfc1035_txt_chunks(txt_value)
    roundtrip = unquote_rfc1035_txt_chunks(chunked)
    if roundtrip != txt_value:
        raise TxtValidationError("RFC 1035 chunking round-trip mismatch.")

    # Parse strict semicolon-separated key=value grammar
    segments = txt_value.split(";")
    if any(seg == "" for seg in segments):
        raise TxtValidationError("TXT record contains empty ';'-delimited segment (leading/trailing/double ';').")

    kv_pairs: List[Tuple[str, str]] = []
    kv_map: Dict[str, str] = {}
    for seg in segments:
        if "=" not in seg:
            raise TxtValidationError(f"Malformed segment without '=': {seg!r}")
        key, val = seg.split("=", 1)
        if not key or key != key.lower():
            raise TxtValidationError(f"Invalid or non-lowercase key {key!r} in segment {seg!r}")
        if key not in ALLOWED_TXT_KEYS:
            raise TxtValidationError(f"Unknown TXT key {key!r}; allowed keys: {sorted(ALLOWED_TXT_KEYS)}")
        if key in kv_map:
            raise TxtValidationError(f"Duplicate TXT key {key!r} is forbidden.")
        kv_pairs.append((key, val))
        kv_map[key] = val

    if kv_pairs[0][0] != "v" or kv_map.get("v") != TXT_SCHEMA_V1:
        raise TxtValidationError(f"First field must be 'v={TXT_SCHEMA_V1}', got {kv_pairs[0]!r}.")

    rev = kv_map.get("rev", "")
    if not REVISION_REGEX.match(rev):
        raise TxtValidationError(f"Invalid 'rev' identifier {rev!r}; must match {REVISION_REGEX.pattern}.")

    en_raw = kv_map.get("en")
    if en_raw not in ("0", "1"):
        raise TxtValidationError(f"Field 'en' must be '0' or '1', got {en_raw!r}.")
    enabled = en_raw == "1"

    if "min" in kv_map:
        if not kv_map["min"].isdigit() or int(kv_map["min"]) < 0:
            raise TxtValidationError(f"Invalid 'min' version code: {kv_map['min']!r}")

    if "ro" in kv_map:
        if not kv_map["ro"].isdigit() or not (0 <= int(kv_map["ro"]) <= 1000):
            raise TxtValidationError(f"Invalid 'ro' permille [0..1000]: {kv_map['ro']!r}")

    if "hosts" in kv_map and kv_map["hosts"] != "*":
        hosts = [h.strip().lower() for h in kv_map["hosts"].split(",") if h.strip()]
        if PRIMARY_RELAY_HOST not in hosts:
            raise TxtValidationError(f"'hosts' restriction {hosts} omits primary relay {PRIMARY_RELAY_HOST}.")

    has_iat = "iat" in kv_map
    has_exp = "exp" in kv_map
    if has_iat != has_exp:
        raise TxtValidationError("'iat' and 'exp' must either both be present or both be omitted.")
    if require_timestamps and not has_iat:
        raise TxtValidationError("Automated DNS updates must include 'iat' and 'exp' timestamps.")

    effective_now = int(time.time()) if now_epoch is None else now_epoch
    iat_val: Optional[int] = None
    exp_val: Optional[int] = None
    if has_iat:
        if not kv_map["iat"].isdigit() or not kv_map["exp"].isdigit():
            raise TxtValidationError(f"'iat' and 'exp' must be positive integer epoch seconds: {kv_map}")
        iat_val = int(kv_map["iat"])
        exp_val = int(kv_map["exp"])
        if require_fresh_iat and abs(iat_val - effective_now) > MAX_CLOCK_SKEW_SECONDS:
            raise TxtValidationError(
                f"'iat' ({iat_val}) differs from current UTC epoch ({effective_now}) by more than {MAX_CLOCK_SKEW_SECONDS}s."
            )
        if not require_fresh_iat and iat_val > effective_now + 300:
            raise TxtValidationError(
                f"Live TXT 'iat' ({iat_val}) is more than 5 minutes in the future ({effective_now})."
            )
        if effective_now >= exp_val:
            raise TxtValidationError(f"TXT record is already expired (exp={exp_val} <= now={effective_now}).")
        lifetime = exp_val - iat_val
        if not (MIN_VALIDITY_SECONDS <= lifetime <= MAX_VALIDITY_SECONDS):
            raise TxtValidationError(
                f"Validity window (exp - iat = {lifetime}s) outside safe bounds [{MIN_VALIDITY_SECONDS}s, {MAX_VALIDITY_SECONDS}s]."
            )
        if previous_iat is not None and iat_val < previous_iat:
            raise TxtValidationError(
                f"Anti-rollback violation: new iat ({iat_val}) < existing live Cloudflare iat ({previous_iat})."
            )

    if "c" not in kv_map:
        raise TxtValidationError("Field 'c' must be present (use 'c=' when en=0).")

    raw_c = kv_map["c"]
    if not enabled:
        if raw_c != "":
            raise TxtValidationError(f"Kill-switch record (en=0) must have empty 'c=', got {raw_c!r}.")
        return {
            "enabled": False,
            "revision": rev,
            "iat": iat_val,
            "exp": exp_val,
            "ipv4_count": 0,
            "ipv6_count": 0,
        }

    if not raw_c:
        raise TxtValidationError("Enabled record (en=1) must contain non-empty candidate list 'c=...'.")

    tokens = raw_c.split(",")
    if any(t == "" for t in tokens):
        raise TxtValidationError(f"Candidate CSV 'c=' contains empty token: {raw_c!r}")
    if len(tokens) > MAX_ANDROID_CANDIDATE_COUNT:
        raise TxtValidationError(
            f"Candidate count ({len(tokens)}) exceeds Android MAX_CANDIDATE_COUNT ({MAX_ANDROID_CANDIDATE_COUNT})."
        )

    cf_v4 = list(allowed_v4_supernets) if allowed_v4_supernets is not None else [
        ipaddress.IPv4Network(c, strict=True) for c in KNOWN_CLOUDFLARE_V4_SUPERNETS
    ]
    cf_v6 = list(allowed_v6_supernets) if allowed_v6_supernets is not None else [
        ipaddress.IPv6Network(c, strict=True) for c in KNOWN_CLOUDFLARE_V6_SUPERNETS
    ]

    seen_ids: Set[str] = set()
    parsed_v4: List[ipaddress.IPv4Network] = []
    parsed_v6: List[ipaddress.IPv6Network] = []

    for index, token in enumerate(tokens):
        if "@" in token:
            explicit_id, spec_str = token.split("@", 1)
            if not CANDIDATE_ID_REGEX.match(explicit_id):
                raise TxtValidationError(f"Invalid candidate ID {explicit_id!r} in token {token!r}.")
            cid = explicit_id
        else:
            cid = f"cf-txt-{index + 1:02d}"
            spec_str = token

        if cid in seen_ids:
            raise TxtValidationError(f"Duplicate candidate ID {cid!r}.")
        seen_ids.add(cid)

        try:
            if "/" in spec_str:
                net = ipaddress.ip_network(spec_str, strict=True)
            else:
                addr = ipaddress.ip_address(spec_str)
                net = ipaddress.ip_network(f"{addr}/{32 if addr.version == 4 else 128}", strict=True)
        except ValueError as exc:
            raise TxtValidationError(f"Invalid or non-canonical IP/CIDR {spec_str!r}: {exc}") from exc

        if isinstance(net, ipaddress.IPv4Network):
            if net.prefixlen not in tuple(range(16, 25)) + (32,):
                raise TxtValidationError(
                    f"IPv4 candidate {net} prefix /{net.prefixlen} outside allowed Anycast bounds (/16../24 or /32)."
                )
            first_host = net.network_address if net.prefixlen == 32 else net.network_address + 1
            last_host = net.network_address if net.prefixlen == 32 else net.broadcast_address - 1
            if not (is_android_public_routable_unicast(first_host) and is_android_public_routable_unicast(last_host)):
                raise TxtValidationError(f"IPv4 candidate {net} fails public routable unicast check.")
            if not any(net.subnet_of(supernet) for supernet in cf_v4):
                raise TxtValidationError(
                    f"Ownership check failed: IPv4 candidate {net} is NOT inside official Cloudflare IPv4 supernets!"
                )
            for existing_v4 in parsed_v4:
                if net.overlaps(existing_v4):
                    raise TxtValidationError(f"Duplicate or overlapping IPv4 subnets: {net} vs {existing_v4}.")
            parsed_v4.append(net)
        else:
            if net.prefixlen not in tuple(range(32, 65)) + (128,):
                raise TxtValidationError(
                    f"IPv6 candidate {net} prefix /{net.prefixlen} outside allowed Anycast bounds (/32../64 or /128)."
                )
            sample_v6 = net.network_address if net.prefixlen == 128 else net.network_address + 1
            if not is_android_public_routable_unicast(sample_v6):
                raise TxtValidationError(f"IPv6 candidate {net} fails public routable unicast check.")
            if not any(net.subnet_of(supernet) for supernet in cf_v6):
                raise TxtValidationError(
                    f"Ownership check failed: IPv6 candidate {net} is NOT inside official Cloudflare IPv6 supernets!"
                )
            for existing_v6 in parsed_v6:
                if net.overlaps(existing_v6):
                    raise TxtValidationError(f"Duplicate or overlapping IPv6 prefixes: {net} vs {existing_v6}.")
            parsed_v6.append(net)

    if len(parsed_v4) < min_ipv4_subnets:
        raise TxtValidationError(
            f"Quorum check failed: got {len(parsed_v4)} IPv4 subnets, minimum required is {min_ipv4_subnets}."
        )

    slash16_families = {(n.network_address.packed[0], n.network_address.packed[1]) for n in parsed_v4}
    if len(slash16_families) < min_ipv4_slash16_families:
        raise TxtValidationError(
            f"Diversity check failed: IPv4 subnets span only {len(slash16_families)} /16 blocks "
            f"(minimum required is {min_ipv4_slash16_families})."
        )

    return {
        "enabled": True,
        "revision": rev,
        "iat": iat_val,
        "exp": exp_val,
        "ipv4_count": len(parsed_v4),
        "ipv6_count": len(parsed_v6),
        "slash16_diversity": len(slash16_families),
    }


def validate_live_doh_url(
    doh_url: str,
    expected_record_name: str = DEFAULT_RECORD_NAME,
    allow_kill_switch: bool = False,
) -> Dict[str, object]:
    """Fetches a live DNS-over-HTTPS JSON response (RFC 8427 / Cloudflare / Alidns format)
    and validates the returned TXT record through `validate_txt_record_before_publish`."""
    req = urllib.request.Request(
        url=doh_url,
        headers={"Accept": "application/dns-json", "User-Agent": USER_AGENT},
    )
    with urllib.request.urlopen(req, timeout=10) as resp:
        payload = json.loads(resp.read().decode("utf-8"))

    if payload.get("Status") != 0:
        raise TxtValidationError(f"Live DoH returned non-NOERROR Status={payload.get('Status')}: {payload}")

    answers = payload.get("Answer") or []
    txt_records = [
        unquote_rfc1035_txt_chunks(ans["data"])
        for ans in answers
        if ans.get("type") == 16 and isinstance(ans.get("data"), str)
    ]
    wk8_records = [r for r in txt_records if r.startswith(f"v={TXT_SCHEMA_V1};")]
    if len(wk8_records) != 1:
        raise TxtValidationError(f"Expected exactly 1 {TXT_SCHEMA_V1} TXT answer on {doh_url}, got {len(wk8_records)}")

    cf_v4, cf_v6 = resolve_cloudflare_allowlists(fetch_live=False)
    summary = validate_txt_record_before_publish(
        wk8_records[0],
        record_name=expected_record_name,
        require_timestamps=False,
        require_fresh_iat=False,
        now_epoch=int(time.time()),
        allowed_v4_supernets=cf_v4,
        allowed_v6_supernets=cf_v6,
    )
    if not allow_kill_switch and not summary["enabled"]:
        raise TxtValidationError(f"Live DoH TXT has en=0 when en=1 was expected: {wk8_records[0]}")
    return summary


def build_candidate_v4_pool(
    rng: random.Random,
    official_v4_supernets: Sequence[ipaddress.IPv4Network],
    samples_per_supernet: int = 4,
) -> List[str]:
    """Builds a diverse candidate list mixing fresh random /24 slices from Cloudflare's
    Web Edge supernets with pre-verified anchor /24s, round-robined across supernets."""
    buckets: Dict[str, List[str]] = {}

    for cidr_str in WEB_EDGE_IPV4_SUPERNETS:
        sub_supernet = ipaddress.IPv4Network(cidr_str, strict=True)
        if official_v4_supernets and not any(sub_supernet.subnet_of(pub) for pub in official_v4_supernets):
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
        if official_v4_supernets and not any(net.subnet_of(pub) for pub in official_v4_supernets):
            continue
        bucket_key = f"anchor-{net.network_address.packed[0]}"
        bucket = buckets.setdefault(bucket_key, [])
        if cidr not in bucket:
            bucket.append(cidr)

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


@dataclasses.dataclass(frozen=True)
class ProbeResult:
    ok: bool
    detail: str
    transient: bool = False


def _parse_content_length(header_text: str) -> Optional[int]:
    if "transfer-encoding: chunked" in header_text.lower():
        return None
    for line in header_text.splitlines()[1:]:
        if line.lower().startswith("content-length:"):
            raw = line.split(":", 1)[1].strip()
            if raw.isdigit():
                return int(raw)
    return None


def probe_ip_for_host_detailed(
    ip_text: str,
    relay_host: str,
    timeout_sec: float = PRIMARY_PROBE_TIMEOUT_SEC,
    max_429_retries: int = 2,
    max_transient_retries: int = 0,
) -> ProbeResult:
    """Connects directly to `ip_text:443` with TLS SNI + X.509 hostname verification for
    `relay_host` and validates a cryptographic `wenku8-network-probe-v1` nonce echo.
    Returns a `ProbeResult` containing structured diagnostic details for logging & retry policy."""
    addr_obj = ipaddress.ip_address(ip_text)
    sock_family = socket.AF_INET6 if addr_obj.version == 6 else socket.AF_INET
    tls_ctx = ssl.create_default_context()

    retries_429_used = 0
    retries_transient_used = 0
    last_result = ProbeResult(False, "no_attempts", transient=True)

    while True:
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

        started_at = time.monotonic()
        deadline = started_at + timeout_sec
        raw_resp = b""
        try:
            with socket.socket(sock_family, socket.SOCK_STREAM) as raw_sock:
                raw_sock.settimeout(timeout_sec)
                raw_sock.connect((ip_text, 443))
                remaining = deadline - time.monotonic()
                if remaining <= 0:
                    raise TimeoutError("deadline exceeded before TLS handshake")
                raw_sock.settimeout(remaining)
                with tls_ctx.wrap_socket(raw_sock, server_hostname=relay_host) as tls_sock:
                    remaining = deadline - time.monotonic()
                    if remaining <= 0:
                        raise TimeoutError("deadline exceeded after TLS handshake")
                    tls_sock.settimeout(remaining)
                    tls_sock.sendall(request_bytes)
                    chunks: List[bytes] = []
                    total = 0
                    expected_total_bytes: Optional[int] = None
                    while total < 8192:
                        remaining = deadline - time.monotonic()
                        if remaining <= 0:
                            raise TimeoutError("deadline exceeded while reading response")
                        tls_sock.settimeout(remaining)
                        data = tls_sock.recv(2048)
                        if not data:
                            break
                        chunks.append(data)
                        total += len(data)
                        if expected_total_bytes is None:
                            joined_so_far = b"".join(chunks)
                            hdr_end = joined_so_far.find(b"\r\n\r\n")
                            if hdr_end >= 0:
                                hdr_preview = joined_so_far[:hdr_end].decode("latin1", errors="replace")
                                content_len = _parse_content_length(hdr_preview)
                                if content_len is not None:
                                    expected_total_bytes = hdr_end + 4 + content_len
                        if expected_total_bytes is not None and total >= expected_total_bytes:
                            break
            raw_resp = b"".join(chunks)
        except ssl.SSLCertVerificationError as exc:
            return ProbeResult(False, f"tls_cert_error: {exc}", transient=False)
        except (socket.timeout, TimeoutError):
            last_result = ProbeResult(False, f"timeout after {timeout_sec:.1f}s", transient=True)
            if retries_transient_used < max_transient_retries:
                retries_transient_used += 1
                time.sleep(min(3.0, 0.75 * retries_transient_used))
                continue
            return last_result
        except OSError as exc:
            last_result = ProbeResult(False, f"os_error ({type(exc).__name__}: {exc})", transient=True)
            if retries_transient_used < max_transient_retries:
                retries_transient_used += 1
                time.sleep(min(3.0, 0.75 * retries_transient_used))
                continue
            return last_result

        header_end = raw_resp.find(b"\r\n\r\n")
        if header_end < 0:
            last_result = ProbeResult(False, f"malformed_http_response ({len(raw_resp)} bytes)", transient=True)
            if retries_transient_used < max_transient_retries:
                retries_transient_used += 1
                time.sleep(min(3.0, 0.75 * retries_transient_used))
                continue
            return last_result

        header_text = raw_resp[:header_end].decode("latin1", errors="replace")
        status_line = header_text.splitlines()[0] if header_text else ""
        status_parts = status_line.split()
        status_code = int(status_parts[1]) if len(status_parts) >= 2 and status_parts[1].isdigit() else None

        if status_code == 429:
            last_result = ProbeResult(False, f"http_429 ({status_line})", transient=True)
            if retries_429_used < max_429_retries:
                retries_429_used += 1
                retry_after = 2.0
                for line in header_text.splitlines()[1:]:
                    if line.lower().startswith("retry-after:"):
                        try:
                            retry_after = min(10.0, max(1.0, float(line.split(":", 1)[1].strip())))
                        except ValueError:
                            pass
                time.sleep(retry_after)
                continue
            return last_result

        if status_code in TRANSIENT_HTTP_STATUS_CODES:
            last_result = ProbeResult(False, f"http_{status_code} ({status_line})", transient=True)
            if retries_transient_used < max_transient_retries:
                retries_transient_used += 1
                time.sleep(min(3.0, 0.75 * retries_transient_used))
                continue
            return last_result

        if status_code != 200:
            return ProbeResult(False, f"http_{status_code or 'unknown'} ({status_line})", transient=False)

        body_part = raw_resp[header_end + 4 :]
        if "transfer-encoding: chunked" in header_text.lower():
            decoded = bytearray()
            cursor = 0
            try:
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
            except ValueError:
                last_result = ProbeResult(False, "invalid_chunked_encoding", transient=True)
                if retries_transient_used < max_transient_retries:
                    retries_transient_used += 1
                    time.sleep(min(3.0, 0.75 * retries_transient_used))
                    continue
                return last_result
            body_part = bytes(decoded)

        try:
            parsed = json.loads(body_part.decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError) as exc:
            return ProbeResult(False, f"invalid_json ({exc})", transient=False)

        if (
            isinstance(parsed, dict)
            and parsed.get("schema") == PROBE_SCHEMA
            and parsed.get("nonce") == nonce
        ):
            elapsed_ms = int((time.monotonic() - started_at) * 1000)
            return ProbeResult(True, f"ok ({elapsed_ms}ms)", transient=False)

        got_schema = parsed.get("schema") if isinstance(parsed, dict) else type(parsed).__name__
        return ProbeResult(False, f"probe_payload_mismatch (schema={got_schema!r})", transient=False)


def probe_ip_for_host(
    ip_text: str,
    relay_host: str,
    timeout_sec: float = PRIMARY_PROBE_TIMEOUT_SEC,
    max_429_retries: int = 2,
    max_transient_retries: int = 0,
) -> bool:
    """Boolean wrapper around `probe_ip_for_host_detailed`."""
    return probe_ip_for_host_detailed(
        ip_text,
        relay_host,
        timeout_sec=timeout_sec,
        max_429_retries=max_429_retries,
        max_transient_retries=max_transient_retries,
    ).ok


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


def select_diverse_spot_check_subnets(verified_v4: Sequence[str], max_count: int) -> List[str]:
    """Selects up to `max_count` verified subnets prioritizing distinct `/16` families."""
    selected: List[str] = []
    seen_slash16: Set[Tuple[int, int]] = set()
    for cidr in verified_v4:
        if len(selected) >= max_count:
            break
        packed = ipaddress.IPv4Network(cidr, strict=True).network_address.packed
        family = (packed[0], packed[1])
        if family not in seen_slash16:
            seen_slash16.add(family)
            selected.append(cidr)
    for cidr in verified_v4:
        if len(selected) >= max_count:
            break
        if cidr not in selected:
            selected.append(cidr)
    return selected


def spot_check_secondary_host(
    verified_v4: Sequence[str],
    secondary_host: str = SECONDARY_RELAY_HOST,
    *,
    probe_fn: Optional[Callable[[str, str], ProbeResult]] = None,
    max_subnets: int = SECONDARY_SPOT_CHECK_MAX_SUBNETS,
    timeout_sec: float = SECONDARY_SPOT_CHECK_TIMEOUT_SEC,
    max_transient_retries: int = SECONDARY_SPOT_CHECK_TRANSIENT_RETRIES,
    quiet: bool = False,
) -> Tuple[str, List[str]]:
    """Spot-checks `secondary_host` across up to `max_subnets` diverse verified IPv4 subnets
    with transient retry & generous cross-region Smart Placement timeout.

    Returns `(succeeded_cidr, failed_cidrs)` or raises `RuntimeError` with detailed per-IP
    diagnostics if every checked subnet fails."""
    if not verified_v4:
        raise RuntimeError(f"Spot-check on secondary host {secondary_host} skipped: no verified IPv4 subnets.")

    effective_probe = probe_fn or (
        lambda ip_text, host: probe_ip_for_host_detailed(
            ip_text,
            host,
            timeout_sec=timeout_sec,
            max_429_retries=2,
            max_transient_retries=max_transient_retries,
        )
    )

    candidate_cidrs = select_diverse_spot_check_subnets(verified_v4, max_subnets)
    failed_cidrs: List[str] = []
    failure_notes: List[str] = []

    for cidr in candidate_cidrs:
        sample_ip = str(ipaddress.IPv4Network(cidr, strict=True).network_address + 1)
        result = effective_probe(sample_ip, secondary_host)
        if result.ok:
            if failure_notes and not quiet:
                print(
                    f"WARN: Secondary host {secondary_host} spot-check recovered on {cidr} ({sample_ip}: "
                    f"{result.detail}) after earlier failure(s): {'; '.join(failure_notes)}",
                    file=sys.stderr,
                )
            return cidr, failed_cidrs
        failed_cidrs.append(cidr)
        failure_notes.append(f"{cidr} ({sample_ip}): {result.detail}")

    raise RuntimeError(
        f"Spot-check on secondary host {secondary_host} failed across {len(failure_notes)} verified subnet(s) "
        f"({'; '.join(failure_notes)}); aborting DNS update."
    )


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

    return all(
        probe_ip_for_host(
            sample_ip,
            host,
            timeout_sec=SECONDARY_SPOT_CHECK_TIMEOUT_SEC,
            max_transient_retries=SECONDARY_SPOT_CHECK_TRANSIENT_RETRIES,
        )
        for host in relay_hosts
    )


def build_txt_value(
    cidrs: Sequence[str],
    enabled: bool,
    ttl_valid_days: Optional[int],
    revision: Optional[str] = None,
    min_iat_floor: Optional[int] = None,
) -> str:
    now_utc = dt.datetime.now(dt.timezone.utc)
    rev = revision or now_utc.strftime("%Y%m%d-%H%M")
    en_flag = "1" if enabled else "0"
    cidr_csv = ",".join(cidrs) if enabled else ""
    if ttl_valid_days is None:
        return f"v={TXT_SCHEMA_V1};rev={rev};en={en_flag};c={cidr_csv}"
    iat = int(now_utc.timestamp())
    if min_iat_floor is not None and iat <= min_iat_floor:
        iat = min_iat_floor + 1
    exp = iat + ttl_valid_days * 86400
    return f"v={TXT_SCHEMA_V1};rev={rev};en={en_flag};iat={iat};exp={exp};c={cidr_csv}"


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


def fetch_existing_cloudflare_txt_records(
    zone_id: str,
    api_token: str,
    record_name: str,
) -> List[dict]:
    base_url = f"https://api.cloudflare.com/client/v4/zones/{zone_id}/dns_records"
    list_url = f"{base_url}?type=TXT&name={record_name}"
    existing = cf_api_request("GET", list_url, api_token)
    if not existing.get("success"):
        raise RuntimeError(f"Cloudflare API list failed: {existing}")

    records = existing.get("result") or []
    expected_norm = record_name.strip().rstrip(".").lower()
    for rec in records:
        rec_type = rec.get("type")
        rec_name = str(rec.get("name", "")).strip().rstrip(".").lower()
        if rec_type != "TXT" or rec_name != expected_norm:
            raise TxtValidationError(
                f"Safety abort: Cloudflare API returned unexpected record type={rec_type!r} name={rec_name!r} "
                f"while querying {expected_norm!r}!"
            )
    return records


def upsert_cloudflare_txt_record(
    zone_id: str,
    api_token: str,
    record_name: str,
    txt_value: str,
    dns_ttl: int,
    existing_records: Sequence[dict],
    allowed_v4_supernets: Sequence[ipaddress.IPv4Network],
    allowed_v6_supernets: Sequence[ipaddress.IPv6Network],
    require_timestamps: bool,
    allow_custom_record_name: bool,
) -> None:
    previous_iat: Optional[int] = None
    for rec in existing_records:
        rec_iat = extract_iat_from_existing_txt(str(rec.get("content", "")))
        if rec_iat is not None:
            previous_iat = rec_iat if previous_iat is None else max(previous_iat, rec_iat)

    # HARD PRE-PUBLISH VALIDATION GATE — raises TxtValidationError before ANY write API call!
    summary = validate_txt_record_before_publish(
        txt_value,
        record_name=record_name,
        dns_ttl=dns_ttl,
        require_timestamps=require_timestamps,
        previous_iat=previous_iat,
        allowed_v4_supernets=allowed_v4_supernets,
        allowed_v6_supernets=allowed_v6_supernets,
        allow_custom_record_name=allow_custom_record_name,
    )
    print(f"Pre-publish validation passed: {summary}", file=sys.stderr)

    base_url = f"https://api.cloudflare.com/client/v4/zones/{zone_id}/dns_records"
    chunked_content = format_rfc1035_txt_chunks(txt_value)
    record_body = {
        "type": "TXT",
        "name": record_name,
        "content": chunked_content,
        "ttl": dns_ttl,
        "comment": "Managed by update_network_cfg_txt.py for Wenku8 Android v2 anti-blocking",
    }

    if existing_records:
        primary_id = existing_records[0]["id"]
        res = cf_api_request("PUT", f"{base_url}/{primary_id}", api_token, record_body)
        if not res.get("success"):
            raise RuntimeError(f"Cloudflare API update failed: {res}")
        stored_content = res.get("result", {}).get("content", "")
        for extra in existing_records[1:]:
            cf_api_request("DELETE", f"{base_url}/{extra['id']}", api_token)
        action = f"Updated TXT record {record_name} (id={primary_id}, ttl={dns_ttl}s)"
    else:
        res = cf_api_request("POST", base_url, api_token, record_body)
        if not res.get("success"):
            raise RuntimeError(f"Cloudflare API create failed: {res}")
        created_id = res["result"]["id"]
        stored_content = res["result"].get("content", "")
        action = f"Created TXT record {record_name} (id={created_id}, ttl={dns_ttl}s)"

    # Post-write round-trip check against Cloudflare API's stored representation
    roundtrip = unquote_rfc1035_txt_chunks(stored_content)
    if roundtrip != txt_value:
        raise RuntimeError(
            f"Cloudflare stored TXT mismatch! Expected {len(txt_value)} chars, got {len(roundtrip)}: {stored_content!r}"
        )
    validate_txt_record_before_publish(
        roundtrip,
        record_name=record_name,
        dns_ttl=dns_ttl,
        require_timestamps=require_timestamps,
        allowed_v4_supernets=allowed_v4_supernets,
        allowed_v6_supernets=allowed_v6_supernets,
        allow_custom_record_name=allow_custom_record_name,
    )
    print(f"{action} — verified {len(txt_value)} bytes stored and re-validated cleanly.", file=sys.stderr)


def curate_verified_subnets(
    max_v4: int,
    include_unverified_v6: bool,
    official_v4_supernets: Sequence[ipaddress.IPv4Network],
) -> List[str]:
    rng = random.Random()
    candidates = build_candidate_v4_pool(rng, official_v4_supernets=official_v4_supernets)
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

    if len(verified_v4) < MIN_REQUIRED_IPV4_SUBNETS:
        raise RuntimeError(
            f"Only {len(verified_v4)} IPv4 subnets passed verification (minimum quorum is "
            f"{MIN_REQUIRED_IPV4_SUBNETS}, probed {probed_count}); aborting DNS update."
        )

    # Spot-check secondary relay hostname (`mewx-android.wenku8.app`) across diverse verified subnets
    # with transient retry & generous cross-region Smart Placement timeout.
    _, failed_secondary_cidrs = spot_check_secondary_host(verified_v4, SECONDARY_RELAY_HOST)
    if failed_secondary_cidrs:
        failed_set = set(failed_secondary_cidrs)
        filtered_v4 = [cidr for cidr in verified_v4 if cidr not in failed_set]
        filtered_families = {
            (
                ipaddress.IPv4Network(cidr, strict=True).network_address.packed[0],
                ipaddress.IPv4Network(cidr, strict=True).network_address.packed[1],
            )
            for cidr in filtered_v4
        }
        if (
            len(filtered_v4) >= MIN_REQUIRED_IPV4_SUBNETS
            and len(filtered_families) >= MIN_REQUIRED_IPV4_SLASH16_FAMILIES
        ):
            verified_v4 = filtered_v4
            print(
                f"Excluded {len(failed_secondary_cidrs)} subnet(s) that failed secondary host spot-check: "
                f"{', '.join(failed_secondary_cidrs)}",
                file=sys.stderr,
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


def _expect_validation_failure(label: str, fn) -> None:
    try:
        fn()
    except TxtValidationError:
        return
    raise AssertionError(f"Expected TxtValidationError for negative test case: {label}")


def run_self_test() -> int:
    cf_v4, cf_v6 = resolve_cloudflare_allowlists(fetch_live=False)
    now = 1_800_000_000
    exp = now + 30 * 86400
    valid_cidrs = list(ANCHOR_IPV4_SUBNETS[:12]) + list(CANDIDATE_IPV6_PREFIXES)
    valid_txt = f"v=wk8-txt-v1;rev=20261118-1200;en=1;iat={now};exp={exp};c=" + ",".join(valid_cidrs)

    # 1. Valid multi-chunk TXT (>255 bytes) passes all checks
    assert len(valid_txt) > 255
    chunked = format_rfc1035_txt_chunks(valid_txt)
    parts = re.findall(r'"([^"]*)"', chunked)
    assert len(parts) == 2 and all(len(p) <= 255 for p in parts)
    assert unquote_rfc1035_txt_chunks(chunked) == valid_txt
    summary = validate_txt_record_before_publish(
        valid_txt,
        now_epoch=now,
        previous_iat=now - 60,
        allowed_v4_supernets=cf_v4,
        allowed_v6_supernets=cf_v6,
    )
    assert summary["enabled"] is True and summary["ipv4_count"] == 12 and summary["ipv6_count"] == 2

    # 2. Valid emergency kill-switch (`en=0`) passes
    kill_txt = f"v=wk8-txt-v1;rev=20261118-kill;en=0;iat={now};exp={exp};c="
    kill_summary = validate_txt_record_before_publish(
        kill_txt,
        now_epoch=now,
        previous_iat=now,
        allowed_v4_supernets=cf_v4,
        allowed_v6_supernets=cf_v6,
    )
    assert kill_summary["enabled"] is False and kill_summary["ipv4_count"] == 0

    # 3. Negative safety tests: every single malformed/dangerous case MUST raise TxtValidationError!
    _expect_validation_failure(
        "wrong record name (apex overwrite attempt)",
        lambda: validate_txt_record_before_publish(valid_txt, record_name="wenku8.app", now_epoch=now),
    )
    _expect_validation_failure(
        "wrong zone domain",
        lambda: validate_txt_record_before_publish(valid_txt, record_name="_network_cfg.evil.example", now_epoch=now),
    )
    _expect_validation_failure(
        "non-Cloudflare public IPv4 (Google DNS 8.8.8.0/24)",
        lambda: validate_txt_record_before_publish(
            f"v=wk8-txt-v1;rev=r1;en=1;iat={now};exp={exp};c=" + ",".join(list(ANCHOR_IPV4_SUBNETS[:8]) + ["8.8.8.0/24"]),
            now_epoch=now,
            allowed_v4_supernets=cf_v4,
        ),
    )
    _expect_validation_failure(
        "private RFC1918 IPv4 (10.0.0.0/24)",
        lambda: validate_txt_record_before_publish(
            f"v=wk8-txt-v1;rev=r1;en=1;iat={now};exp={exp};c=" + ",".join(list(ANCHOR_IPV4_SUBNETS[:8]) + ["10.0.0.0/24"]),
            now_epoch=now,
            allowed_v4_supernets=cf_v4,
        ),
    )
    _expect_validation_failure(
        "TEST-NET-1 blackhole IPv4 (192.0.2.0/24)",
        lambda: validate_txt_record_before_publish(
            f"v=wk8-txt-v1;rev=r1;en=1;iat={now};exp={exp};c=" + ",".join(list(ANCHOR_IPV4_SUBNETS[:8]) + ["192.0.2.0/24"]),
            now_epoch=now,
            allowed_v4_supernets=cf_v4,
        ),
    )
    _expect_validation_failure(
        "non-Cloudflare IPv6 (2001:4860:4860::/48)",
        lambda: validate_txt_record_before_publish(
            f"v=wk8-txt-v1;rev=r1;en=1;iat={now};exp={exp};c="
            + ",".join(list(ANCHOR_IPV4_SUBNETS[:8]) + ["2001:4860:4860::/48"]),
            now_epoch=now,
            allowed_v4_supernets=cf_v4,
            allowed_v6_supernets=cf_v6,
        ),
    )
    _expect_validation_failure(
        "unmasked host bits in CIDR (104.16.80.5/24)",
        lambda: validate_txt_record_before_publish(
            f"v=wk8-txt-v1;rev=r1;en=1;iat={now};exp={exp};c="
            + ",".join(list(ANCHOR_IPV4_SUBNETS[:8]) + ["104.16.80.5/24"]),
            now_epoch=now,
            allowed_v4_supernets=cf_v4,
        ),
    )
    _expect_validation_failure(
        "overlapping / duplicate subnets",
        lambda: validate_txt_record_before_publish(
            f"v=wk8-txt-v1;rev=r1;en=1;iat={now};exp={exp};c="
            + ",".join(list(ANCHOR_IPV4_SUBNETS[:8]) + [ANCHOR_IPV4_SUBNETS[0]]),
            now_epoch=now,
            allowed_v4_supernets=cf_v4,
        ),
    )
    _expect_validation_failure(
        "quorum too low (<8 IPv4 subnets)",
        lambda: validate_txt_record_before_publish(
            f"v=wk8-txt-v1;rev=r1;en=1;iat={now};exp={exp};c=" + ",".join(ANCHOR_IPV4_SUBNETS[:3]),
            now_epoch=now,
            allowed_v4_supernets=cf_v4,
        ),
    )
    _expect_validation_failure(
        "insufficient /16 diversity (all from 104.16.x.0/24)",
        lambda: validate_txt_record_before_publish(
            f"v=wk8-txt-v1;rev=r1;en=1;iat={now};exp={exp};c="
            + ",".join(f"104.16.{i}.0/24" for i in range(1, 10)),
            now_epoch=now,
            allowed_v4_supernets=cf_v4,
        ),
    )
    _expect_validation_failure(
        "anti-rollback violation (new iat < previous live iat)",
        lambda: validate_txt_record_before_publish(
            valid_txt,
            now_epoch=now,
            previous_iat=now + 10,
            allowed_v4_supernets=cf_v4,
            allowed_v6_supernets=cf_v6,
        ),
    )
    _expect_validation_failure(
        "missing timestamps when require_timestamps=True",
        lambda: validate_txt_record_before_publish(
            "v=wk8-txt-v1;rev=r1;en=1;c=" + ",".join(ANCHOR_IPV4_SUBNETS[:10]),
            require_timestamps=True,
            now_epoch=now,
            allowed_v4_supernets=cf_v4,
        ),
    )
    _expect_validation_failure(
        "expired / inverted exp <= iat",
        lambda: validate_txt_record_before_publish(
            f"v=wk8-txt-v1;rev=r1;en=1;iat={now};exp={now};c=" + ",".join(ANCHOR_IPV4_SUBNETS[:10]),
            now_epoch=now,
            allowed_v4_supernets=cf_v4,
        ),
    )
    _expect_validation_failure(
        "millisecond epoch typo in iat",
        lambda: validate_txt_record_before_publish(
            f"v=wk8-txt-v1;rev=r1;en=1;iat={now * 1000};exp={(now + 86400) * 1000};c="
            + ",".join(ANCHOR_IPV4_SUBNETS[:10]),
            now_epoch=now,
            allowed_v4_supernets=cf_v4,
        ),
    )
    _expect_validation_failure(
        "unknown key typo (minv instead of min)",
        lambda: validate_txt_record_before_publish(
            f"v=wk8-txt-v1;rev=r1;en=1;minv=2;iat={now};exp={exp};c=" + ",".join(ANCHOR_IPV4_SUBNETS[:10]),
            now_epoch=now,
            allowed_v4_supernets=cf_v4,
        ),
    )
    _expect_validation_failure(
        "kill-switch with non-empty candidates",
        lambda: validate_txt_record_before_publish(
            f"v=wk8-txt-v1;rev=r1;en=0;iat={now};exp={exp};c={ANCHOR_IPV4_SUBNETS[0]}",
            now_epoch=now,
            allowed_v4_supernets=cf_v4,
        ),
    )
    _expect_validation_failure(
        "whitespace or trailing semicolon injection",
        lambda: validate_txt_record_before_publish(valid_txt + ";", now_epoch=now, allowed_v4_supernets=cf_v4),
    )

    # 4. Secondary host spot-check diversity, fallback recovery & failure diagnostic tests
    diverse_pick = select_diverse_spot_check_subnets(
        ["172.67.200.0/24", "172.67.150.0/24", "104.16.80.0/24", "188.114.97.0/24"],
        max_count=3,
    )
    assert diverse_pick == ["172.67.200.0/24", "104.16.80.0/24", "188.114.97.0/24"]

    seen_probe_ips: List[str] = []

    def flaky_first_probe(ip_text: str, host: str) -> ProbeResult:
        assert host == SECONDARY_RELAY_HOST
        seen_probe_ips.append(ip_text)
        if ip_text == "172.67.200.1":
            return ProbeResult(False, "timeout after 5.0s", transient=True)
        return ProbeResult(True, "ok (210ms)", transient=False)

    recovered_cidr, failed_cidrs = spot_check_secondary_host(
        ["172.67.200.0/24", "172.67.150.0/24", "104.16.80.0/24"],
        SECONDARY_RELAY_HOST,
        probe_fn=flaky_first_probe,
        quiet=True,
    )
    assert recovered_cidr == "104.16.80.0/24"
    assert failed_cidrs == ["172.67.200.0/24"]
    assert seen_probe_ips == ["172.67.200.1", "104.16.80.1"]

    all_fail_err = ""
    try:
        spot_check_secondary_host(
            ["172.67.200.0/24", "104.16.80.0/24"],
            SECONDARY_RELAY_HOST,
            probe_fn=lambda ip, _h: ProbeResult(False, f"http_403 via {ip}", transient=False),
        )
    except RuntimeError as exc:
        all_fail_err = str(exc)
    assert "http_403 via 172.67.200.1" in all_fail_err and "http_403 via 104.16.80.1" in all_fail_err

    assert _parse_content_length("HTTP/1.1 200 OK\r\nContent-Length: 279\r\nConnection: close") == 279
    assert _parse_content_length("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\nContent-Length: 279") is None

    print("Self-test passed (24/24 positive, negative & spot-check safety assertions).", file=sys.stderr)
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="Verify Cloudflare edge subnets & safely update _network_cfg.wenku8.app TXT")
    parser.add_argument("--record-name", default=DEFAULT_RECORD_NAME, help="DNS TXT record FQDN")
    parser.add_argument("--allow-custom-record-name", action="store_true", help="Allow _network_cfg.<other-domain> for isolated testing")
    parser.add_argument("--max-v4", type=int, default=20, help="Max verified IPv4 /24 subnets to include")
    parser.add_argument("--valid-days", type=int, default=30, help="Manifest exp validity window in days (ignored when --static is set)")
    parser.add_argument("--static", action="store_true", help="Omit iat/exp timestamps (only allowed with --dry-run)")
    parser.add_argument("--dns-ttl", type=int, default=300, help="Authoritative DNS TTL in seconds [60..86400]")
    parser.add_argument("--include-unverified-v6", action="store_true", default=True, help="Keep default /48 IPv6 prefixes when runner lacks IPv6")
    parser.add_argument("--kill-switch", action="store_true", help="Publish emergency en=0 kill-switch")
    parser.add_argument("--dry-run", action="store_true", help="Validate & print TXT record without calling Cloudflare write API")
    parser.add_argument("--apply", action="store_true", help="Validate & upsert TXT record via Cloudflare API (requires CF_API_TOKEN and WENKU8_APP_ZONE_ID)")
    parser.add_argument("--verify-doh-url", help="Fetch live DoH JSON URL and run full wk8-txt-v1 validator on the Answer")
    parser.add_argument("--self-test", action="store_true", help="Run unit & negative-validation self-tests")
    args = parser.parse_args()

    if args.self_test:
        return run_self_test()

    if args.verify_doh_url:
        try:
            summary = validate_live_doh_url(
                args.verify_doh_url,
                expected_record_name=args.record_name,
                allow_kill_switch=args.kill_switch,
            )
            print(f"Live DoH verification passed ({args.verify_doh_url}): {summary}", file=sys.stderr)
            return 0
        except Exception as exc:
            print(f"ERROR: Live DoH verification failed: {exc}", file=sys.stderr)
            return 3

    if not args.dry_run and not args.apply:
        args.dry_run = True

    if args.apply and args.static:
        print("ERROR: --static cannot be combined with --apply (automated updates require iat/exp).", file=sys.stderr)
        return 1

    cf_v4_supernets, cf_v6_supernets = resolve_cloudflare_allowlists(fetch_live=True)
    effective_days: Optional[int] = None if args.static else args.valid_days

    existing_records: List[dict] = []
    min_iat_floor: Optional[int] = None
    api_token = ""
    zone_id = ""

    if args.apply and not args.dry_run:
        api_token = os.environ.get("CF_API_TOKEN", "").strip()
        zone_id = (os.environ.get("WENKU8_APP_ZONE_ID") or os.environ.get("CF_ZONE_ID") or "").strip()
        if not api_token or not zone_id:
            print(
                "ERROR: --apply requires CF_API_TOKEN and WENKU8_APP_ZONE_ID (or CF_ZONE_ID) environment variables.",
                file=sys.stderr,
            )
            return 1
        try:
            validate_record_name_target(args.record_name, allow_custom_record_name=args.allow_custom_record_name)
            existing_records = fetch_existing_cloudflare_txt_records(zone_id, api_token, args.record_name)
            for rec in existing_records:
                rec_iat = extract_iat_from_existing_txt(str(rec.get("content", "")))
                if rec_iat is not None:
                    min_iat_floor = rec_iat if min_iat_floor is None else max(min_iat_floor, rec_iat)
        except Exception as exc:
            print(f"ERROR: Pre-flight Cloudflare DNS check failed: {exc}", file=sys.stderr)
            return 2

    if args.kill_switch:
        txt_value = build_txt_value(
            cidrs=[],
            enabled=False,
            ttl_valid_days=effective_days,
            min_iat_floor=min_iat_floor,
        )
    else:
        try:
            all_selected = curate_verified_subnets(
                max_v4=args.max_v4,
                include_unverified_v6=args.include_unverified_v6,
                official_v4_supernets=cf_v4_supernets,
            )
        except RuntimeError as exc:
            print(f"ERROR: {exc}", file=sys.stderr)
            return 2
        txt_value = build_txt_value(
            cidrs=all_selected,
            enabled=True,
            ttl_valid_days=effective_days,
            min_iat_floor=min_iat_floor,
        )

    # Always run full validator even in --dry-run mode so broken output is caught immediately!
    try:
        dry_summary = validate_txt_record_before_publish(
            txt_value,
            record_name=args.record_name,
            dns_ttl=args.dns_ttl,
            require_timestamps=not args.static,
            previous_iat=min_iat_floor,
            allowed_v4_supernets=cf_v4_supernets,
            allowed_v6_supernets=cf_v6_supernets,
            allow_custom_record_name=args.allow_custom_record_name,
        )
    except TxtValidationError as exc:
        print(f"VALIDATION FAILED — refusing to output/publish invalid TXT record: {exc}", file=sys.stderr)
        return 2

    print(txt_value)

    if args.apply and not args.dry_run:
        try:
            upsert_cloudflare_txt_record(
                zone_id=zone_id,
                api_token=api_token,
                record_name=args.record_name,
                txt_value=txt_value,
                dns_ttl=args.dns_ttl,
                existing_records=existing_records,
                allowed_v4_supernets=cf_v4_supernets,
                allowed_v6_supernets=cf_v6_supernets,
                require_timestamps=True,
                allow_custom_record_name=args.allow_custom_record_name,
            )
        except Exception as exc:
            print(f"ERROR: Failed to upsert Cloudflare TXT record: {exc}", file=sys.stderr)
            return 2
    else:
        print(f"Dry-run validation passed: {dry_summary}", file=sys.stderr)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
