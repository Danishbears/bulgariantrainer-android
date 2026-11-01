"""Execute original pinned Java planner/normalizer methods without Spring wiring.
This is not a Spring/Maven integration-test run. --write updates explicit goldens.
"""
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile

ROOT=Path(__file__).resolve().parent
SOURCE=ROOT/'aspect-reference'
FIXTURE=ROOT/'fixtures/web_aspect.json'

def run():
    provenance=json.loads((SOURCE/'PROVENANCE.json').read_text())
    for name,digest in provenance['sha256'].items():
        assert hashlib.sha256((SOURCE/name).read_bytes()).hexdigest()==digest,name
    java=str(Path(os.environ['JAVA_HOME_21_X64'])/'bin/java') if os.environ.get('JAVA_HOME_21_X64') else 'java'
    with tempfile.TemporaryDirectory() as temporary:
        directory=Path(temporary)
        for name in provenance['sha256']:
            source=(SOURCE/name).read_text()
            source=re.sub(r'^import org\.springframework\..*;\n','',source,flags=re.M)
            source=re.sub(r'@(Component|org\.springframework\.beans\.factory\.annotation\.Autowired)\b','',source)
            source=re.sub(r'@Value\("[^"\n]*"\)\s*','',source)
            (directory/name).write_text(source)
        harness='''package com.example.bulgariantrainer.service;
import java.util.*;
import com.example.bulgariantrainer.entity.AspectDirection;
public class AspectHarness {
 public static void main(String[] args) {
  if(args[0].equals("normalize")) {
   for(int i=1;i<args.length;i++)System.out.println(Base64.getEncoder().encodeToString(AspectAnswerNormalizer.key(new String(Base64.getDecoder().decode(args[i]),java.nio.charset.StandardCharsets.UTF_8)).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
  } else {
   var candidates=new ArrayList<AspectQuestionPlanner.Candidate>();
   for(int i=2;i<args.length;i++) {var parts=args[i].split(":");candidates.add(new AspectQuestionPlanner.Candidate(i-1,parts[0].equals("x")?null:Integer.valueOf(parts[0]),parts[1].equals("x")?null:Integer.valueOf(parts[1])));}
   var plan=new AspectQuestionPlanner(new Random(42)).plan(candidates,Integer.parseInt(args[1]),AspectDirection.MIXED);
   int f=0,cost=0;var used=new HashSet<Long>();
   for(var p:plan) {if(!used.add(p.pairId()))throw new AssertionError("Repeated pair");var c=candidates.get((int)p.pairId()-1);boolean forward=p.direction()==AspectDirection.IMPERFECTIVE_TO_PERFECTIVE;if(forward)f++;cost+=forward?c.forwardPriority():c.reversePriority();}
   System.out.println(plan.size()+":"+Math.abs(2*f-plan.size())+":"+cost);
  }
 }
}'''
        (directory/'AspectHarness.java').write_text(harness)
        subprocess.run([java,'-m','jdk.compiler/com.sun.tools.javac.Main','-encoding','UTF-8','-d',str(directory/'classes'),*[str(p) for p in directory.glob('*.java')]],check=True)
        prefix=[java,'-cp',str(directory/'classes'),'com.example.bulgariantrainer.service.AspectHarness']
        inputs=[' \tКУ\u0301ПЯ\u00a0\n','ѝ','и\u0300','й','и\u0306','и','ѐ','е\u0300','опитам се','\u00a0СПОМНЯ\u2003СИ','купя!','kупя','İ','\u0301\u0300']
        output=subprocess.check_output([*prefix,'normalize',*[base64.b64encode(s.encode()).decode() for s in inputs]],text=True).splitlines()
        normalized=[dict(input=value,key=base64.b64decode(key).decode()) for value,key in zip(inputs,output)]
        pools=[[(0,None)]*4,[(None,0)]*4,[(0,1),(1,0),(2,2),(3,3)],[(0,None),(None,0),(1,2),(2,1),(0,3)],[(0,1)],[(0,0)]*7]
        cases=[]
        for pool in pools:
            for size in sorted(set([1,2,3,len(pool),len(pool)+2])):
                result=subprocess.check_output([*prefix,'plan',str(size),*[('x' if f is None else str(f))+':'+('x' if r is None else str(r)) for f,r in pool]],text=True).strip()
                actual,gap,cost=map(int,result.split(':'))
                cases.append(dict(size=size,pool=[dict(id=i+1,forward=f,reverse=r) for i,(f,r) in enumerate(pool)],expected=dict(size=actual,gap=gap,cost=cost)))
        result=dict(reference=provenance['commit'],normalization=normalized,planner=cases)
        if '--write' in sys.argv:FIXTURE.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
        else:assert result==json.loads(FIXTURE.read_text())
        print('PINNED_ASPECT_PARITY:',len(normalized),'normalizations and',len(cases),'planner objectives verified')

if __name__=='__main__':run()
