const {test}=require('node:test');
const assert=require('node:assert/strict');
const {parse,calculate,strings}=require('../core');

test('reads dynamic course codes, names and exact counts',()=>{
  assert.deepEqual(parse('24ECI203 - LINEAR INTEGRATED CIRCUITS : 88% - (23/26)'),{code:'24ECI203',name:'LINEAR INTEGRATED CIRCUITS',attended:23,total:26});
  assert.equal(parse('NEW-LAB - Different subject : 100% - (18/18)').code,'NEW-LAB');
});
test('rejects incomplete, unrelated or inconsistent text',()=>{
  for(const text of ['85% (190/224)','Course - Name : 10% - (23/26)','Course - Name : 100% - (30/26)','Course - Name : 88%','23/26',null])assert.equal(parse(text),null);
});
test('calculates safe absences and recovery at 75 percent',()=>{
  assert.deepEqual(calculate(23,26),{percentage:88.46153846153847,margin:4,recover:0,belowAfter:5});
  assert.equal(calculate(10,13).margin,0);
  assert.equal(calculate(11,15).recover,1);
  assert.equal(calculate(19,26).recover,2);
  assert.equal(calculate(0,0).percentage,null);
  assert.throws(()=>calculate(4,3));
});
test('threshold calculation is correct for every count up to 150 periods',()=>{
  for(let total=1;total<=150;total++)for(let attended=0;attended<=total;attended++){
    const result=calculate(attended,total);
    if(4*attended>=3*total){assert.ok(4*attended>=3*(total+result.margin));assert.ok(4*attended<3*(total+result.margin+1));}
    else{assert.ok(4*(attended+result.recover)>=3*(total+result.recover));assert.ok(4*(attended+result.recover-1)<3*(total+result.recover-1));}
  }
});
test('recursive chart-data extraction handles cycles and ignores getters',()=>{
  const data={nested:['X - Subject : 75% - (3/4)'],get bad(){throw Error('getter accessed');}};data.self=data;
  const found=[];strings(data,row=>found.push(row));assert.equal(found.length,1);
});
