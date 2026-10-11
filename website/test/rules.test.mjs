// Firestore rules for the download counter. Run with `npm run test:rules`.
import { readFileSync } from 'node:fs';
import { after, before, beforeEach, test } from 'node:test';
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing';
import { deleteDoc, doc, getDoc, increment, setDoc, updateDoc } from 'firebase/firestore';

let env;
let db;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-centium',
    firestore: { rules: readFileSync('firestore.rules', 'utf8'), host: '127.0.0.1', port: 8085 },
  });
});

after(() => env.cleanup());

beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled((ctx) =>
    setDoc(doc(ctx.firestore(), 'stats/downloads'), { count: 5 }),
  );
  db = env.unauthenticatedContext().firestore();
});

const counter = () => doc(db, 'stats/downloads');

test('adding one is allowed', async () => {
  await assertSucceeds(updateDoc(counter(), { count: increment(1) }));
});

test('setting the next number explicitly is allowed', async () => {
  await assertSucceeds(updateDoc(counter(), { count: 6 }));
});

test('adding more than one is refused', async () => {
  await assertFails(updateDoc(counter(), { count: 7 }));
  await assertFails(updateDoc(counter(), { count: increment(2) }));
});

test('lowering or resetting is refused', async () => {
  await assertFails(updateDoc(counter(), { count: 4 }));
  await assertFails(updateDoc(counter(), { count: 0 }));
  await assertFails(updateDoc(counter(), { count: increment(-1) }));
});

test('non-numbers are refused', async () => {
  await assertFails(updateDoc(counter(), { count: '6' }));
});

test('extra fields are refused', async () => {
  await assertFails(updateDoc(counter(), { count: 6, note: 'x' }));
});

test('overwriting with set is refused', async () => {
  await assertFails(setDoc(counter(), { count: 0 }));
});

test('deleting is refused', async () => {
  await assertFails(deleteDoc(counter()));
});

test('reading the count is refused', async () => {
  await assertFails(getDoc(counter()));
});

test('creating the counter is refused', async () => {
  await env.clearFirestore();
  await assertFails(setDoc(counter(), { count: 1 }));
  await assertFails(updateDoc(counter(), { count: increment(1) }));
});

test('every other document is closed', async () => {
  await assertFails(setDoc(doc(db, 'stats/other'), { count: 1 }));
  await assertFails(setDoc(doc(db, 'anything/else'), { a: 1 }));
  await assertFails(getDoc(doc(db, 'anything/else')));
});
