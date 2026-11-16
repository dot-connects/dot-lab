// The purchase ledger: one Play purchase token draws one sheet, ever. Every step is one atomic SQL statement, so two
// requests with the same token can never both draw (D1 runs each statement on its own).
//   absent  -> claim inserts "drawing"            -> {go:"new"}  (the caller then asks Google; refused -> forget)
//   drawing -> claim within 3 min                 -> {go:"busy"}; older -> taken over -> {go:"draw"}
//   failed  -> claim                              -> {go:"draw"}
//   done    -> claim                              -> {go:"cached", key, layout, consumed}
const STALE = 180_000;

export class Ledger {
  constructor(db) { this.db = db; }
  #q(sql, ...a) { return this.db.prepare(sql).bind(...a); }

  async claim(token, product, device, now) {
    const ins = await this.#q("INSERT INTO purchases (token, product, device, state, at) VALUES (?1, ?2, ?3, 'drawing', ?4) ON CONFLICT(token) DO NOTHING RETURNING token",
      token, product, device, now).first();
    if (ins) return { go: "new" };
    const row = await this.#q("SELECT product, state, sheet_key, layout, consumed FROM purchases WHERE token = ?1", token).first();
    if (!row) return { go: "busy" };   // forgotten between the two statements: let the app retry
    if (row.product !== product) return { go: "mismatch" };
    if (row.state === "done") return { go: "cached", key: row.sheet_key, layout: row.layout, consumed: row.consumed };
    const took = row.state === "failed"
      ? await this.#q("UPDATE purchases SET state = 'drawing', at = ?2 WHERE token = ?1 AND state = 'failed' RETURNING token", token, now).first()
      : await this.#q("UPDATE purchases SET at = ?2 WHERE token = ?1 AND state = 'drawing' AND at <= ?3 RETURNING token", token, now, now - STALE).first();
    return took ? { go: "draw" } : { go: "busy" };
  }

  async forget(token) { await this.#q("DELETE FROM purchases WHERE token = ?1 AND state = 'drawing'", token).run(); }
  async finish(token, key, layout) { await this.#q("UPDATE purchases SET state = 'done', sheet_key = ?2, layout = ?3 WHERE token = ?1", token, key, layout).run(); }
  async fail(token) { await this.#q("UPDATE purchases SET state = 'failed' WHERE token = ?1 AND state = 'drawing'", token).run(); }
  async markConsumed(token) { await this.#q("UPDATE purchases SET consumed = 1 WHERE token = ?1", token).run(); }

  async freeLeft(device, total) {
    if (!(total > 0)) return 0;
    const r = await this.#q("SELECT n FROM free WHERE device = ?1", device).first();
    return Math.max(0, total - (r ? r.n : 0));
  }
  async useFree(device) { await this.#q("INSERT INTO free (device, n) VALUES (?1, 1) ON CONFLICT(device) DO UPDATE SET n = n + 1", device).run(); }

  async report({ device, entry, kind, style, reason }, now) {
    await this.#q("INSERT INTO reports (at, device, entry, kind, style, reason) VALUES (?1, ?2, ?3, ?4, ?5, ?6)", now, device, entry, kind, style, reason).run();
  }
}
