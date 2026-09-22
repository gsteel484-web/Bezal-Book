import sqlite3, pathlib, unittest
SCHEMA=(pathlib.Path(__file__).parents[1]/'app/src/main/assets/schema.sql').read_text()
class LedgerTests(unittest.TestCase):
 def setUp(self):
  self.d=sqlite3.connect(':memory:'); self.d.executescript(SCHEMA)
  self.d.execute("INSERT INTO party VALUES('p','Party','','',1,0,'now','now')")
 def add(self,id='a',amount=100,type='BAAKI',kind='ENTRY',reverses=None,party='p'):
  self.d.execute('INSERT INTO entry VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)',(id,party,'2026-09-21',amount,type,kind,'','','','','Owner','now','now',reverses))
 def test_balanced_journal(self):
  self.add();self.add('b',30,'JAMA');self.assertEqual(self.d.execute('SELECT SUM(signed_paise) FROM posting').fetchone()[0],0)
  self.assertEqual(self.d.execute("SELECT SUM(signed_paise) FROM posting WHERE account='PARTY:p'").fetchone()[0],70)
 def test_immutable_financial_records(self):
  self.add()
  for sql in ["UPDATE entry SET amount=1",'DELETE FROM entry']:
   with self.assertRaises(sqlite3.IntegrityError):self.d.execute(sql)
 def test_reversal_once_only(self):
  self.add();self.add('b',100,'JAMA','REVERSAL','a')
  with self.assertRaises(sqlite3.IntegrityError):self.add('c',100,'JAMA','REVERSAL','a')
  self.assertEqual(self.d.execute("SELECT SUM(signed_paise) FROM posting WHERE account='PARTY:p'").fetchone()[0],0)
 def test_invalid_reversal(self):
  self.add()
  for amount,type,kind in [(101,'JAMA','REVERSAL'),(100,'BAAKI','REVERSAL'),(100,'JAMA','ENTRY')]:
   with self.assertRaises(sqlite3.IntegrityError):self.add('b',amount,type,kind,'a')
 def test_no_orphan_or_invalid_amount(self):
  for kwargs in [{'party':'missing'},{'amount':0},{'amount':-1},{'amount':100000000001}]:
   with self.assertRaises(sqlite3.IntegrityError):self.add(**kwargs)
 def test_audit_immutable(self):
  self.d.execute("INSERT INTO audit VALUES('z','ENTRY_CREATED','a','detail','Owner','now')")
  for sql in ['DELETE FROM audit',"UPDATE audit SET detail='altered'"]:
   with self.assertRaises(sqlite3.IntegrityError):self.d.execute(sql)
 def test_atomic_failure_rolls_back(self):
  self.d.commit()
  try:
   with self.d:self.add();self.add('b',party='missing')
  except sqlite3.IntegrityError:pass
  self.assertEqual(self.d.execute('SELECT COUNT(*) FROM entry').fetchone()[0],0)
if __name__=='__main__':unittest.main()
