import test from 'node:test';
import assert from 'node:assert/strict';
import { AiError, medicineInfo, resetAiForTests } from '../src/ai.js';

const env = { GEMINI_API_KEY: 'test-key' };
const reply = (obj) => async () => ({
  ok: true,
  status: 200,
  json: async () => ({ candidates: [{ content: { parts: [{ text: JSON.stringify(obj) }] } }] }),
});

test.beforeEach(() => resetAiForTests());

test('returns a cleaned summary for a medicine the model knows', async () => {
  const r = await medicineInfo({ name: 'Cetirizine 10 mg', brand: 'Zyrtec' }, {
    env,
    fetchImpl: reply({ known: true, uses: 'Relieves allergy symptoms.', directions: '', warnings: 'May cause drowsiness.' }),
  });
  assert.equal(r.known, true);
  assert.equal(r.uses, 'Relieves allergy symptoms.');
  assert.equal(r.warnings, 'May cause drowsiness.');
});

test('sends the key in a header, never in the URL, and never includes it in the answer', async () => {
  let seen;
  const r = await medicineInfo({ name: 'Foo' }, {
    env,
    fetchImpl: async (url, init) => { seen = { url, init }; return reply({ known: true, uses: 'x' })(); },
  });
  assert.ok(!seen.url.includes('test-key'));
  assert.equal(seen.init.headers['x-goog-api-key'], 'test-key');
  assert.ok(!JSON.stringify(r).includes('test-key'));
});

test('says "not known" instead of guessing', async () => {
  const r = await medicineInfo({ name: 'Zzxq 5' }, { env, fetchImpl: reply({ known: false, uses: 'Maybe something?' }) });
  assert.deepEqual({ known: r.known }, { known: false });
});

test('rejects a missing name and a missing server key', async () => {
  await assert.rejects(medicineInfo({}, { env }), (e) => e instanceof AiError && e.status === 400);
  await assert.rejects(medicineInfo({ name: 'x' }, { env: {} }), (e) => e instanceof AiError && e.status === 503);
});

test('limits how often one caller can ask', async () => {
  const opts = { env, ip: '1.2.3.4', fetchImpl: reply({ known: true, uses: 'x' }) };
  for (let i = 0; i < 20; i++) await medicineInfo({ name: `drug ${i}` }, opts);
  await assert.rejects(medicineInfo({ name: 'one more' }, opts), (e) => e instanceof AiError && e.status === 429);
});

test('reports an AI outage as a 502', async () => {
  await assert.rejects(
    medicineInfo({ name: 'x' }, { env, fetchImpl: async () => ({ ok: false, status: 500, json: async () => ({}) }) }),
    (e) => e instanceof AiError && e.status === 502,
  );
});
