/**
 * Offline apply queue (ADR 0009). Applications made without a network are stored in IndexedDB and
 * replayed when the browser comes back online. Apply is idempotent on the server (UNIQUE job+candidate),
 * so replaying a request that actually got through before the connection dropped is safe: a 409 on
 * replay counts as success. No dependency — the IndexedDB API is small enough to use directly.
 */

export interface QueuedApply {
  jobId: number;
  coverNote?: string;
  queuedAt: number;
}

const DB_NAME = "naukrinearby";
const STORE = "apply-queue";
const VERSION = 1;

function open(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, VERSION);
    req.onupgradeneeded = () => {
      if (!req.result.objectStoreNames.contains(STORE)) {
        req.result.createObjectStore(STORE, { keyPath: "jobId" });
      }
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}

async function withStore<T>(
  mode: IDBTransactionMode,
  fn: (store: IDBObjectStore) => IDBRequest<T>,
): Promise<T> {
  const db = await open();
  try {
    return await new Promise<T>((resolve, reject) => {
      const tx = db.transaction(STORE, mode);
      const req = fn(tx.objectStore(STORE));
      req.onsuccess = () => resolve(req.result);
      req.onerror = () => reject(req.error);
    });
  } finally {
    db.close();
  }
}

export function isOfflineQueueSupported(): boolean {
  return typeof window !== "undefined" && "indexedDB" in window;
}

/** Keyed by jobId, so queuing the same job twice keeps one entry. */
export async function enqueueApply(item: QueuedApply): Promise<void> {
  await withStore("readwrite", (s) => s.put(item));
}

export async function queuedApplies(): Promise<QueuedApply[]> {
  return withStore<QueuedApply[]>("readonly", (s) => s.getAll() as IDBRequest<QueuedApply[]>);
}

export async function removeQueued(jobId: number): Promise<void> {
  await withStore("readwrite", (s) => s.delete(jobId));
}

/** True when an error from fetch means "no network" rather than an HTTP error. */
export function isNetworkError(err: unknown): boolean {
  return err instanceof TypeError || (typeof navigator !== "undefined" && !navigator.onLine);
}
