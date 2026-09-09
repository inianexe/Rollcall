(function(root){
  'use strict';
  function parse(text){
    if(typeof text!=='string'||text.length>4000)return null;
    const match=text.trim().match(/^([\w.-]+)\s*[-–—]\s*(.+?)\s*:\s*(\d+(?:\.\d+)?)\s*%\s*[-–—:]?\s*\(\s*(\d+)\s*\/\s*(\d+)\s*\)\s*$/);
    if(!match)return null;
    const attended=Number(match[4]),total=Number(match[5]),displayed=Number(match[3]);
    if(!Number.isSafeInteger(attended)||!Number.isSafeInteger(total)||attended>total||total<0)return null;
    if(total&&Math.abs(100*attended/total-displayed)>1.01)return null;
    return {code:match[1],name:match[2].trim(),attended,total};
  }
  function calculate(attended,total){
    if(![attended,total].every(Number.isSafeInteger)||attended<0||total<attended)throw new RangeError('Invalid attendance counts');
    if(!total)return {percentage:null,margin:null,recover:null,belowAfter:null};
    const delta=4*attended-3*total;
    const margin=Math.max(0,Math.floor(delta/3));
    const recover=delta>=0?0:-delta;
    return {percentage:100*attended/total,margin,recover,belowAfter:delta<0?0:margin+1};
  }
  function strings(value,emit){
    const seen=new WeakSet();let budget=12000;
    function walk(item,depth){
      if(--budget<0||depth>12)return;
      if(typeof item==='string'){const row=parse(item);if(row)emit(row);return;}
      if(!item||typeof item!=='object'||seen.has(item))return;
      seen.add(item);
      for(const descriptor of Object.values(Object.getOwnPropertyDescriptors(item)))if('value' in descriptor)walk(descriptor.value,depth+1);
    }
    walk(value,0);
  }
  const api={parse,calculate,strings};
  if(typeof module!=='undefined'&&module.exports)module.exports=api;
  else Object.defineProperty(root,'CamuAttendanceCore',{value:api,configurable:true});
})(globalThis);
