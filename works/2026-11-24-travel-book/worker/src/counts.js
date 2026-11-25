// copied from works/2026-11-09-catch/worker/src/index.js (MemoryCounts / D1Counts), keep in sync.
// take(k, limit) adds one only while below limit and returns the new count (0 = refused); give(k) hands one back.
// add(k, n, limit) adds n only if the total stays <= limit and returns the new total (0 = refused); add(k, -n) refunds.
// settle(k, n) adds n with no limit (the real cost is recorded even past the ceiling; D1 cannot bind Infinity).
export class MemoryCounts {
  constructor() { this.m = new Map(); }
  async take(k, limit) { const n = this.m.get(k) || 0; if (n >= limit) return 0; this.m.set(k, n + 1); return n + 1; }
  async give(k) { const n = this.m.get(k) || 0; if (n > 0) this.m.set(k, n - 1); }
  async add(k, n, limit) { const t = (this.m.get(k) || 0) + n; if (t > limit) return 0; this.m.set(k, t); return t; }
  async settle(k, n) { this.m.set(k, (this.m.get(k) || 0) + n); }
  get(k) { return this.m.get(k) || 0; }
}
// ponytail: one row per key per day/month, never pruned; prune if it ever matters
export class D1Counts {
  constructor(db) { this.db = db; }
  async take(k, limit) {
    if (limit <= 0) return 0;   // "0" = off: the INSERT branch below has no WHERE
    const r = await this.db.prepare("INSERT INTO counts (k, n) VALUES (?1, 1) ON CONFLICT(k) DO UPDATE SET n = n + 1 WHERE n < ?2 RETURNING n").bind(k, limit).first();
    return r ? r.n : 0;
  }
  async give(k) { await this.db.prepare("UPDATE counts SET n = n - 1 WHERE k = ?1 AND n > 0").bind(k).run(); }
  async add(k, n, limit) {
    if (n > limit) return 0;   // the INSERT branch has no WHERE
    const r = await this.db.prepare("INSERT INTO counts (k, n) VALUES (?1, ?2) ON CONFLICT(k) DO UPDATE SET n = n + ?2 WHERE n + ?2 <= ?3 RETURNING n").bind(k, n, limit).first();
    return r ? r.n : 0;
  }
  async settle(k, n) {
    await this.db.prepare("INSERT INTO counts (k, n) VALUES (?1, ?2) ON CONFLICT(k) DO UPDATE SET n = n + ?2").bind(k, n).run();
  }
}
