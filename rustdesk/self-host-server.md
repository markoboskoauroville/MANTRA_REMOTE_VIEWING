# OPTIONAL: YOUR OWN RUSTDESK RELAY

You probably do **not** need this. Tailscale + direct IP access already
connects you with no server in the middle.

Build this only if you later want to reach machines that are not on your
tailnet — e.g. helping your father Marinko with his laptop without installing
Tailscale on it.

## What runs

- `hbbs` — rendezvous / ID server (ports 21114-21119)
- `hbbr` — relay, used only when NAT punching fails (port 21117)

## Where

Any small VPS with a **public IPv4**. 1 vCPU / 1 GB is enough for a handful of
devices. Hetzner CX22 (~4 €/mo, Nuremberg — low latency to Croatia) or an
Oracle Cloud always-free ARM instance (0 €, if you can get one).

Put it in Europe. Frankfurt or Vienna gives you ~20-30 ms from Zagreb.

## docker-compose.yml

```yaml
services:
  hbbs:
    image: rustdesk/rustdesk-server:latest
    command: hbbs
    volumes: [ "./data:/root" ]
    network_mode: host
    restart: unless-stopped

  hbbr:
    image: rustdesk/rustdesk-server:latest
    command: hbbr
    volumes: [ "./data:/root" ]
    network_mode: host
    restart: unless-stopped
```

```bash
docker compose up -d
cat data/id_ed25519.pub     # <- this is RS_PUB_KEY for the APK build
```

Open TCP 21115-21119 and UDP 21116 in the firewall. Nothing else.

## Bandwidth warning

If a session falls back to **relay** mode, all your pixels flow through this
VPS and count against its traffic allowance. Check your plan's limits before
you leave it running for months.
