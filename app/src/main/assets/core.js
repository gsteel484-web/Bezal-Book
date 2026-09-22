(function(root){
 const iso=d=>`${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;
 const today=()=>iso(new Date());
 function paise(text,allowZero=false){if(!/^\d{1,10}(\.\d{1,2})?$/.test(String(text).trim()))throw Error('सही रकम लिखें, अधिकतम दो दशमलव');let [a,b='']=String(text).trim().split('.');let n=Number(a)*100+Number(b.padEnd(2,'0'));if(n>100000000000||(!allowZero&&n===0))throw Error('रकम ₹0.01 से ₹1,00,00,00,000 तक रखें');return n;}
 function validDate(s){if(!/^\d{4}-\d{2}-\d{2}$/.test(s))return false;let d=new Date(s+'T12:00:00');return !isNaN(d)&&iso(d)===s;}
 const signed=e=>e.type==='BAAKI'?e.amount:-e.amount;
 const ordered=entries=>[...entries].sort((a,b)=>a.date.localeCompare(b.date)||a.created.localeCompare(b.created)||a.id.localeCompare(b.id));
 function ledger(entries,party,from='0001-01-01',to='9999-12-31'){
  let opening=0,jama=0,baaki=0,rows=[];let list=ordered(entries.filter(e=>!party||e.party===party));
  for(let e of list)if(e.date<from)opening+=signed(e);
  let balance=opening;for(let e of list){if(e.date<from||e.date>to)continue;balance+=signed(e);if(e.type==='JAMA')jama+=e.amount;else baaki+=e.amount;rows.push({...e,balance});}
  return {opening,jama,baaki,closing:balance,rows};
 }
 function range(label){let d=new Date(),from,to=today();switch(label){case 'today':from=to;break;case 'yesterday':d.setDate(d.getDate()-1);from=to=iso(d);break;case 'week':d.setDate(d.getDate()-6);from=iso(d);break;case 'month':d.setDate(1);from=iso(d);break;case 'previous':d.setDate(0);to=iso(d);d.setDate(1);from=iso(d);break;default:from='0001-01-01';to='9999-12-31';}return {from,to};}
 const api={paise,validDate,signed,ordered,ledger,range,today};if(typeof module!=='undefined')module.exports=api;else root.Core=api;
})(typeof window==='undefined'?globalThis:window);
