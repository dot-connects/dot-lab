// D1 storage behind the small interface handle() uses; tests use the in-memory one.

export class D1Store {
  constructor(db) { this.db = db; }
  async addGroup(g) { await this.db.prepare("INSERT INTO groups (id, created) VALUES (?, ?)").bind(g.id, g.created).run(); }
  async addMember(m) {
    await this.db.prepare("INSERT INTO members (id, group_id, name, secret_hash, fcm_token, owner, created) VALUES (?, ?, ?, ?, ?, ?, ?)")
      .bind(m.id, m.groupId, m.name, m.secretHash, m.fcmToken, m.owner ? 1 : 0, m.created).run();
  }
  async member(id) {
    const r = await this.db.prepare("SELECT * FROM members WHERE id = ?").bind(id).first();
    return r && rowMember(r);
  }
  async members(groupId) {
    const { results } = await this.db.prepare("SELECT * FROM members WHERE group_id = ? ORDER BY created").bind(groupId).all();
    return results.map(rowMember);
  }
  async removeMember(groupId, id) { await this.db.prepare("DELETE FROM members WHERE id = ? AND group_id = ?").bind(id, groupId).run(); }
  async setToken(id, token) { await this.db.prepare("UPDATE members SET fcm_token = ? WHERE id = ?").bind(token, id).run(); }
  async clearToken(token) { await this.db.prepare("UPDATE members SET fcm_token = NULL WHERE fcm_token = ?").bind(token).run(); }
  async invite(codeHash, now) {
    const r = await this.db.prepare("SELECT group_id, expires FROM invites WHERE code_hash = ? AND expires >= ?").bind(codeHash, now).first();
    return r ? { groupId: r.group_id, expires: r.expires } : null;
  }
  async dropInvites(groupId) { await this.db.prepare("DELETE FROM invites WHERE group_id = ?").bind(groupId).run(); }
  async makeOwner(id) { await this.db.prepare("UPDATE members SET owner = 1 WHERE id = ?").bind(id).run(); }
  async dropGroup(groupId) {
    await this.db.batch([
      this.db.prepare("DELETE FROM invites WHERE group_id = ?").bind(groupId),
      this.db.prepare("DELETE FROM parks WHERE group_id = ?").bind(groupId),
      this.db.prepare("DELETE FROM groups WHERE id = ?").bind(groupId),
    ]);
  }
  async addInvite(i) {
    await this.db.prepare("INSERT INTO invites (code_hash, group_id, expires) VALUES (?, ?, ?)").bind(i.codeHash, i.groupId, i.expires).run();
  }
  /** One-time: the invite is deleted when taken; expired ones are swept on the way. */
  async takeInvite(codeHash, now) {
    await this.db.prepare("DELETE FROM invites WHERE expires < ?").bind(now).run();
    const r = await this.db.prepare("DELETE FROM invites WHERE code_hash = ? RETURNING group_id, expires").bind(codeHash).first();
    return r ? { groupId: r.group_id, expires: r.expires } : null;
  }
  async park(groupId) {
    const r = await this.db.prepare("SELECT * FROM parks WHERE group_id = ?").bind(groupId).first();
    return r ? { park: JSON.parse(r.json), by: r.by_name, byId: r.by_id, updated: r.updated } : null;
  }
  async setPark(groupId, v) {
    await this.db.prepare(
      "INSERT INTO parks (group_id, json, by_name, by_id, updated) VALUES (?, ?, ?, ?, ?) " +
      "ON CONFLICT(group_id) DO UPDATE SET json = excluded.json, by_name = excluded.by_name, by_id = excluded.by_id, updated = excluded.updated",
    ).bind(groupId, JSON.stringify(v.park), v.by, v.byId, v.updated).run();
  }
}

const rowMember = r => ({
  id: r.id, groupId: r.group_id, name: r.name, secretHash: r.secret_hash, fcmToken: r.fcm_token, owner: !!r.owner, created: r.created,
});

export class MemoryStore {
  constructor() { this.groups = new Map(); this.membersById = new Map(); this.invites = new Map(); this.parks = new Map(); }
  async addGroup(g) { this.groups.set(g.id, g); }
  async addMember(m) { this.membersById.set(m.id, { ...m }); }
  async member(id) { return this.membersById.get(id) || null; }
  async members(groupId) { return [...this.membersById.values()].filter(m => m.groupId === groupId).sort((a, b) => a.created - b.created); }
  async removeMember(groupId, id) { const m = this.membersById.get(id); if (m && m.groupId === groupId) this.membersById.delete(id); }
  async setToken(id, token) { const m = this.membersById.get(id); if (m) m.fcmToken = token; }
  async clearToken(token) { for (const m of this.membersById.values()) if (m.fcmToken === token) m.fcmToken = null; }
  async addInvite(i) { this.invites.set(i.codeHash, i); }
  async invite(codeHash, now) { const i = this.invites.get(codeHash); return i && i.expires >= now ? i : null; }
  async dropInvites(groupId) { for (const [k, v] of this.invites) if (v.groupId === groupId) this.invites.delete(k); }
  async makeOwner(id) { const m = this.membersById.get(id); if (m) m.owner = true; }
  async dropGroup(groupId) {
    for (const [k, v] of this.invites) if (v.groupId === groupId) this.invites.delete(k);
    this.parks.delete(groupId); this.groups.delete(groupId);
  }
  async takeInvite(codeHash, now) {
    for (const [k, v] of this.invites) if (v.expires < now) this.invites.delete(k);
    const i = this.invites.get(codeHash);
    if (!i) return null;
    this.invites.delete(codeHash);
    return i;
  }
  async park(groupId) { return this.parks.get(groupId) || null; }
  async setPark(groupId, v) { this.parks.set(groupId, structuredClone(v)); }
}
