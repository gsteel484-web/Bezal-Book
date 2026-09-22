const assert=require('node:assert/strict');
const C=require('../app/src/main/assets/core.js');
let passed=0;function test(name,fn){fn();console.log('PASS',name);passed++;}
test('exact paise conversion',()=>{assert.equal(C.paise('0.29'),29);assert.equal(C.paise('1234567.89'),123456789);assert.equal(C.paise('0',true),0);});
test('invalid and overflowing amounts rejected',()=>{for(let x of ['-1','1e3','NaN','0','1.001','1000000001','1,000',''])assert.throws(()=>C.paise(x));});
test('calendar dates validated',()=>{assert(C.validDate('2024-02-29'));assert(!C.validDate('2025-02-29'));assert(!C.validDate('2026-13-01'));});
const e=(id,date,amount,type,party='p')=>({id,date,amount,type,party,created:'2026-01-01T00:00:00Z'});
const entries=[e('a','2026-01-01',10000,'BAAKI'),e('b','2026-01-02',3500,'JAMA'),e('c','2026-01-03',2000,'BAAKI'),e('d','2026-01-04',2000,'JAMA'),e('x','2026-01-02',99999,'BAAKI','other')];
test('party isolation and closing balance',()=>assert.equal(C.ledger(entries,'p').closing,6500));
test('date range carries prior opening balance',()=>{let l=C.ledger(entries,'p','2026-01-02','2026-01-03');assert.deepEqual([l.opening,l.jama,l.baaki,l.closing],[10000,3500,2000,8500]);assert.deepEqual(l.rows.map(x=>x.balance),[6500,8500]);});
test('reversal preserves historical running balances',()=>assert.deepEqual(C.ledger(entries,'p').rows.map(e=>e.balance),[10000,6500,8500,6500]));
test('empty date range preserves carried balance',()=>{let l=C.ledger(entries,'p','2026-02-01','2026-02-02');assert.equal(l.rows.length,0);assert.equal(l.closing,6500);});
test('stable tie breaker independent of insertion order',()=>assert.deepEqual(C.ordered([e('b','2026-01-01',1,'JAMA'),e('a','2026-01-01',1,'BAAKI')]).map(x=>x.id),['a','b']));
test('range dates and ordering',()=>{for(let k of ['today','yesterday','week','month','previous']){let r=C.range(k);assert(C.validDate(r.from)&&C.validDate(r.to)&&r.from<=r.to);}});
console.log(`${passed} core tests passed`);
