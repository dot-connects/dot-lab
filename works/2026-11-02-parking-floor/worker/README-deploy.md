# Worker v2 deploy order

Never run these from an automated agent; the operator runs them.

1. Backup: `npx wrangler@4 d1 export dot-lab-parking --remote --output backup-$(date +%F).sql`
2. Migrate D1 (idempotent): `python tools/migrate_v2.py --remote`
3. R2: `npx wrangler@4 r2 bucket create <photos-bucket>` then add a 30-day lifecycle rule
   (`npx wrangler@4 r2 bucket lifecycle add <photos-bucket> photos-30d --expire-days 30 -y`); bucket name must match `wrangler.toml`.
4. Set `ASSETLINKS_SHA256` (release signing cert SHA-256, comma-separated for several) in `wrangler.toml` vars or as a secret.
5. `npx wrangler@4 deploy --dry-run`, then `npx wrangler@4 deploy`.
6. Smoke test: `GET /j#ABCD-EFGH-JKMN` renders, `GET /.well-known/assetlinks.json` lists the fingerprint,
   create group, invite, join, add car, park, photo PUT/GET through `/v2/*`.

Rollback: `npx wrangler@4 rollback` (the D1 migration is additive, so old code keeps working).
