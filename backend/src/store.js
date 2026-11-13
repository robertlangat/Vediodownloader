// Tiny JSON-file session store. Each session holds the provider tokens for one phone.
// For production use a real database and encrypt tokens at rest.

import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';

export class SessionStore {
  constructor(file) {
    this.file = file;
    this.data = {};
    if (file) {
      try {
        this.data = JSON.parse(fs.readFileSync(file, 'utf8'));
      } catch {
        this.data = {};
      }
    }
  }

  get(id) {
    return id && Object.prototype.hasOwnProperty.call(this.data, id) ? this.data[id] : null;
  }

  /** Merges `patch` into session `id` (or a new session) and returns the session id. */
  upsert(id, patch) {
    const sid = id && this.get(id) ? id : crypto.randomBytes(24).toString('base64url');
    this.data[sid] = { ...(this.data[sid] || {}), ...patch, updatedAt: Date.now() };
    this.save();
    return sid;
  }

  save() {
    if (!this.file) return;
    fs.mkdirSync(path.dirname(this.file), { recursive: true });
    fs.writeFileSync(this.file, JSON.stringify(this.data), { mode: 0o600 });
  }
}
