// D1 storage behind the small interface handle() uses; tests use the in-memory one.

export class D1Store {
  constructor(db) { this.db = db; }
  async addGroup(g) { await this.db.prepare("INSERT INTO groups (id, created, name) VALUES (?, ?, ?)").bind(g.id, g.created, g.name ?? null).run(); }
  async group(id) { const r = await this.db.prepare("SELECT * FROM groups WHERE id = ?").bind(id).first(); return r && { id: r.id, name: r.name, created: r.created }; }
  async addMember(m) {
    await this.db.prepare("INSERT INTO members (id, group_id, name, secret_hash, fcm_token, owner, created, last_seen) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")
      .bind(m.id, m.groupId, m.name, m.secretHash, m.fcmToken, m.owner ? 1 : 0, m.created, m.lastSeen).run();
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
  async touchMember(id, now) { await this.db.prepare("UPDATE members SET last_seen = ? WHERE id = ?").bind(now, id).run(); }
  async invite(codeHash, now) {
    const r = await this.db.prepare("SELECT group_id, expires FROM invites WHERE code_hash = ? AND expires >= ?").bind(codeHash, now).first();
    return r ? { groupId: r.group_id, expires: r.expires } : null;
  }
  /** [keep] = invite hashes that stay open (the ones handed to join requests). */
  async dropInvites(groupId, keep = []) {
    await this.db.prepare("DELETE FROM invites WHERE group_id = ? AND code_hash NOT IN (SELECT value FROM json_each(?))").bind(groupId, JSON.stringify(keep)).run();
  }
  async dropInvite(codeHash) { await this.db.prepare("DELETE FROM invites WHERE code_hash = ?").bind(codeHash).run(); }
  async makeOwner(id) { await this.db.prepare("UPDATE members SET owner = 1 WHERE id = ?").bind(id).run(); }
  async dropGroup(groupId) {
    await this.db.batch([
      this.db.prepare("DELETE FROM invites WHERE group_id = ?").bind(groupId),
      this.db.prepare("DELETE FROM requests WHERE group_id = ?").bind(groupId),
      this.db.prepare("DELETE FROM parks WHERE group_id = ?").bind(groupId),
      this.db.prepare("DELETE FROM parks2 WHERE group_id = ?").bind(groupId),
      this.db.prepare("DELETE FROM cars WHERE group_id = ?").bind(groupId),
      this.db.prepare("DELETE FROM groups WHERE id = ?").bind(groupId),
    ]);
  }
  async cars(groupId) {
    const { results } = await this.db.prepare("SELECT json FROM cars WHERE group_id = ? ORDER BY ord, id").bind(groupId).all();
    return results.map(r => JSON.parse(r.json));
  }
  async car(groupId, id) { const r = await this.db.prepare("SELECT json FROM cars WHERE group_id = ? AND id = ?").bind(groupId, id).first(); return r ? JSON.parse(r.json) : null; }
  async setCar(groupId, car) { await this.db.prepare("INSERT INTO cars (group_id,id,json,ord,updated) VALUES (?,?,?,?,?) ON CONFLICT(group_id,id) DO UPDATE SET json=excluded.json,ord=excluded.ord,updated=excluded.updated").bind(groupId, car.id, JSON.stringify(car), car.order, car.updated).run(); }
  async removeCar(groupId, id) { await this.db.prepare("DELETE FROM cars WHERE group_id = ? AND id = ?").bind(groupId, id).run(); await this.removePark2(groupId, id); }
  async park2(groupId, carId) {
    const r = await this.db.prepare("SELECT * FROM parks2 WHERE group_id = ? AND car_id = ?").bind(groupId, carId).first();
    return r ? { park: JSON.parse(r.json), prev: r.prev_json ? JSON.parse(r.prev_json) : null, by: r.by_name, byId: r.by_id, drivingBy: r.driving_by, updated: r.updated } : null;
  }
  async parks2(groupId) {
    const { results } = await this.db.prepare("SELECT car_id FROM parks2 WHERE group_id = ?").bind(groupId).all();
    const out = {}; for (const r of results) out[r.car_id] = await this.park2(groupId, r.car_id); return out;
  }
  async setPark2(groupId, carId, v) {
    await this.db.prepare("INSERT INTO parks2 (group_id,car_id,json,prev_json,by_name,by_id,driving_by,updated) VALUES (?,?,?,?,?,?,?,?) ON CONFLICT(group_id,car_id) DO UPDATE SET json=excluded.json,prev_json=excluded.prev_json,by_name=excluded.by_name,by_id=excluded.by_id,driving_by=excluded.driving_by,updated=excluded.updated")
      .bind(groupId, carId, JSON.stringify(v.park), v.prev ? JSON.stringify(v.prev) : null, v.by, v.byId, v.drivingBy, v.updated).run();
  }
  async removePark2(groupId, carId) { await this.db.prepare("DELETE FROM parks2 WHERE group_id = ? AND car_id = ?").bind(groupId, carId).run(); }
  /** Conditional write: applies only if the row is still `cur` (same park id and updated), or absent when cur is null. */
  async casPark2(groupId, carId, cur, v) {
    const args = [JSON.stringify(v.park), v.prev ? JSON.stringify(v.prev) : null, v.by, v.byId, v.drivingBy, v.updated];
    const r = cur
      ? await this.db.prepare("UPDATE parks2 SET json=?,prev_json=?,by_name=?,by_id=?,driving_by=?,updated=? WHERE group_id=? AND car_id=? AND json_extract(json,'$.id')=? AND updated=?")
          .bind(...args, groupId, carId, cur.park.id, cur.updated).run()
      : await this.db.prepare("INSERT INTO parks2 (json,prev_json,by_name,by_id,driving_by,updated,group_id,car_id) VALUES (?,?,?,?,?,?,?,?) ON CONFLICT(group_id,car_id) DO NOTHING")
          .bind(...args, groupId, carId).run();
    return (r.meta?.changes ?? 0) > 0;
  }
  async casRemovePark2(groupId, carId, cur) {
    const r = await this.db.prepare("DELETE FROM parks2 WHERE group_id=? AND car_id=? AND json_extract(json,'$.id')=? AND updated=?").bind(groupId, carId, cur.park.id, cur.updated).run();
    return (r.meta?.changes ?? 0) > 0;
  }
  async expiredPhotos(cutoff) {
    const { results } = await this.db.prepare("SELECT group_id, car_id, json, prev_json FROM parks2 WHERE (json_extract(json,'$.atMs') < ?1 AND json_extract(json,'$.photo') = 1) OR (prev_json IS NOT NULL AND json_extract(prev_json,'$.park.atMs') < ?1 AND json_extract(prev_json,'$.park.photo') = 1)").bind(cutoff).all();
    return results.flatMap(r => photoKeys(r.group_id, r.car_id, JSON.parse(r.json), r.prev_json ? JSON.parse(r.prev_json) : null, cutoff));
  }
  async expirePhotos(cutoff) {
    await this.db.prepare("UPDATE parks2 SET json = json_set(json, '$.photo', json('false')) WHERE json_extract(json, '$.atMs') < ? AND json_extract(json, '$.photo') = 1").bind(cutoff).run();
    await this.db.prepare("UPDATE parks2 SET prev_json = json_set(prev_json, '$.park.photo', json('false')) WHERE prev_json IS NOT NULL AND json_extract(prev_json, '$.park.atMs') < ? AND json_extract(prev_json, '$.park.photo') = 1").bind(cutoff).run();
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
  /** Join requests: first accept wins; an expired row no longer blocks. false = a live row already exists. */
  async addRequest(r, now) {
    await this.db.prepare("DELETE FROM requests WHERE req_hash = ? AND expires < ?").bind(r.reqHash, now).run();
    const res = await this.db.prepare("INSERT INTO requests (req_hash, group_id, code, expires, by_id, by_name) VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT(req_hash) DO NOTHING")
      .bind(r.reqHash, r.groupId, r.code, r.expires, r.byId, r.byName).run();
    return (res.meta?.changes ?? 0) > 0;
  }
  async request(reqHash, now) {
    const r = await this.db.prepare("SELECT group_id, code, by_name FROM requests WHERE req_hash = ? AND expires >= ?").bind(reqHash, now).first();
    return r ? { groupId: r.group_id, code: r.code, byName: r.by_name } : null;
  }
  async dropRequests(code) { await this.db.prepare("DELETE FROM requests WHERE code = ?").bind(code).run(); }
  async dropRequest(reqHash) { await this.db.prepare("DELETE FROM requests WHERE req_hash = ?").bind(reqHash).run(); }
  async groupRequests(groupId, now) {
    const { results } = await this.db.prepare("SELECT code, expires FROM requests WHERE group_id = ? AND expires >= ?").bind(groupId, now).all();
    return results.map(r => ({ code: r.code, expires: r.expires }));
  }
  async expireRequests(now) { await this.db.prepare("DELETE FROM requests WHERE expires < ?").bind(now).run(); }
  /** Requests this member accepted (any expiry): removed with the member. */
  async memberRequests(groupId, memberId) {
    const { results } = await this.db.prepare("SELECT req_hash, code FROM requests WHERE group_id = ? AND by_id = ?").bind(groupId, memberId).all();
    return results.map(r => ({ reqHash: r.req_hash, code: r.code }));
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

const photoKeys = (groupId, carId, park, prev, cutoff) => [park, prev?.park].filter(p => p && p.photo && p.atMs < cutoff).map(p => ({ groupId, carId, parkId: p.id }));

const rowMember = r => ({
  id: r.id, groupId: r.group_id, name: r.name, secretHash: r.secret_hash, fcmToken: r.fcm_token, owner: !!r.owner, created: r.created, lastSeen: r.last_seen,
});

export class MemoryStore {
  constructor() { this.groups = new Map(); this.membersById = new Map(); this.invites = new Map(); this.parks = new Map(); this.carsByGroup = new Map(); this.parks2ByGroup = new Map(); this.requests = new Map(); }
  async addGroup(g) { this.groups.set(g.id, g); }
  async group(id) { return this.groups.get(id) || null; }
  async addMember(m) { this.membersById.set(m.id, { ...m }); }
  async member(id) { return this.membersById.get(id) || null; }
  async members(groupId) { return [...this.membersById.values()].filter(m => m.groupId === groupId).sort((a, b) => a.created - b.created); }
  async removeMember(groupId, id) { const m = this.membersById.get(id); if (m && m.groupId === groupId) this.membersById.delete(id); }
  async setToken(id, token) { const m = this.membersById.get(id); if (m) m.fcmToken = token; }
  async clearToken(token) { for (const m of this.membersById.values()) if (m.fcmToken === token) m.fcmToken = null; }
  async touchMember(id, now) { const m = this.membersById.get(id); if (m) m.lastSeen = now; }
  async addInvite(i) { this.invites.set(i.codeHash, i); }
  async invite(codeHash, now) { const i = this.invites.get(codeHash); return i && i.expires >= now ? i : null; }
  async dropInvites(groupId, keep = []) { for (const [k, v] of this.invites) if (v.groupId === groupId && !keep.includes(k)) this.invites.delete(k); }
  async dropInvite(codeHash) { this.invites.delete(codeHash); }
  async makeOwner(id) { const m = this.membersById.get(id); if (m) m.owner = true; }
  async dropGroup(groupId) {
    for (const [k, v] of this.invites) if (v.groupId === groupId) this.invites.delete(k);
    for (const [k, v] of this.requests) if (v.groupId === groupId) this.requests.delete(k);
    this.parks.delete(groupId); this.carsByGroup.delete(groupId); this.parks2ByGroup.delete(groupId); this.groups.delete(groupId);
  }
  async cars(groupId) { return [...(this.carsByGroup.get(groupId)?.values() || [])].sort((a,b) => a.order - b.order || a.id.localeCompare(b.id)); }
  async car(groupId, id) { return this.carsByGroup.get(groupId)?.get(id) || null; }
  async setCar(groupId, car) { if (!this.carsByGroup.has(groupId)) this.carsByGroup.set(groupId, new Map()); this.carsByGroup.get(groupId).set(car.id, structuredClone(car)); }
  async removeCar(groupId, id) { this.carsByGroup.get(groupId)?.delete(id); await this.removePark2(groupId, id); }
  async park2(groupId, carId) { return this.parks2ByGroup.get(groupId)?.get(carId) || null; }
  async parks2(groupId) { return Object.fromEntries(this.parks2ByGroup.get(groupId) || []); }
  async setPark2(groupId, carId, v) { if (!this.parks2ByGroup.has(groupId)) this.parks2ByGroup.set(groupId, new Map()); this.parks2ByGroup.get(groupId).set(carId, structuredClone(v)); }
  async removePark2(groupId, carId) { this.parks2ByGroup.get(groupId)?.delete(carId); }
  async casPark2(groupId, carId, cur, v) {
    const now = await this.park2(groupId, carId);
    if (cur ? !now || now.park.id !== cur.park.id || now.updated !== cur.updated : now) return false;
    await this.setPark2(groupId, carId, v); return true;
  }
  async casRemovePark2(groupId, carId, cur) {
    const now = await this.park2(groupId, carId);
    if (!now || now.park.id !== cur.park.id || now.updated !== cur.updated) return false;
    await this.removePark2(groupId, carId); return true;
  }
  async expiredPhotos(cutoff) {
    const out = [];
    for (const [groupId, group] of this.parks2ByGroup) for (const [carId, v] of group) out.push(...photoKeys(groupId, carId, v.park, v.prev, cutoff));
    return out;
  }
  async expirePhotos(cutoff) {
    for (const group of this.parks2ByGroup.values()) for (const v of group.values()) {
      if (v.park.atMs < cutoff) v.park.photo = false;
      if (v.prev && v.prev.park.atMs < cutoff) v.prev.park.photo = false;
    }
  }
  async takeInvite(codeHash, now) {
    for (const [k, v] of this.invites) if (v.expires < now) this.invites.delete(k);
    const i = this.invites.get(codeHash);
    if (!i) return null;
    this.invites.delete(codeHash);
    return i;
  }
  async addRequest(r, now) {
    const cur = this.requests.get(r.reqHash);
    if (cur && cur.expires >= now) return false;
    this.requests.set(r.reqHash, { ...r }); return true;
  }
  async request(reqHash, now) { const r = this.requests.get(reqHash); return r && r.expires >= now ? { groupId: r.groupId, code: r.code, byName: r.byName } : null; }
  async memberRequests(groupId, memberId) { return [...this.requests.values()].filter(r => r.groupId === groupId && r.byId === memberId).map(r => ({ reqHash: r.reqHash, code: r.code })); }
  async dropRequests(code) { for (const [k, v] of this.requests) if (v.code === code) this.requests.delete(k); }
  async dropRequest(reqHash) { this.requests.delete(reqHash); }
  async groupRequests(groupId, now) { return [...this.requests.values()].filter(r => r.groupId === groupId && r.expires >= now).map(r => ({ code: r.code, expires: r.expires })); }
  async expireRequests(now) { for (const [k, v] of this.requests) if (v.expires < now) this.requests.delete(k); }
  async park(groupId) { return this.parks.get(groupId) || null; }
  async setPark(groupId, v) { this.parks.set(groupId, structuredClone(v)); }
}
