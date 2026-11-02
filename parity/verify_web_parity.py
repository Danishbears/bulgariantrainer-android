"""Verify goldens with pinned actual web scheduler/business methods using the hosted JDK.
Only Spring wiring/annotations are removed; scheduling and entity record/getter methods
are compiled verbatim. This is not a full Spring/Maven integration-test execution.
"""
import hashlib
import json
from pathlib import Path
import re
import subprocess
import shutil
import tempfile

ROOT = Path(__file__).resolve().parent
reference = ROOT / 'reference'
provenance = json.loads((reference / 'PROVENANCE.json').read_text())
for name, expected in provenance['sha256'].items():
    assert hashlib.sha256((reference / name).read_bytes()).hexdigest() == expected, name

def method(source, signature):
    start = source.index(signature)
    brace = source.index('{', start)
    depth = 1
    end = brace + 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]

with tempfile.TemporaryDirectory() as temporary:
    directory = Path(temporary)
    service = directory / 'src/com/example/bulgariantrainer/service'
    entity = directory / 'src/com/example/bulgariantrainer/entity'
    service.mkdir(parents=True)
    entity.mkdir(parents=True)
    source = (reference / 'SpacedRepetitionScheduler.java').read_text()
    source = re.sub(r'^import org\.springframework\..*;\n', '', source, flags=re.M)
    source = re.sub(r'@(Configuration|Bean|Service)\b', '', source)
    source = re.sub(r'@Value\("[^"\n]*"\)\s*', '', source)
    (service / 'SpacedRepetitionScheduler.java').write_text(source)
    for name in ['LearningCategory', 'StudyScope', 'AttemptResult']:
        (entity / f'{name}.java').write_text((reference / f'{name}.java').read_text())
    for name, record in [('StudyProgress', 'public void recordAnswer('), ('SentenceProgress', 'public void record(')]:
        original = (reference / f'{name}.java').read_text()
        methods = [method(original, record)] + [method(original, signature) for signature in [
            'public int getTotalAttempts(', 'public Instant getNextReviewAt(',
            'public int getConsecutiveCorrect(', 'public int getRepetitionInterval(']]
        stub = '''package com.example.bulgariantrainer.entity;
import java.time.Instant;
public class %s {
 public int totalAttempts, correctAttempts, partialAttempts, incorrectAttempts;
 public int successfulReviewCount, consecutiveCorrect, repetitionInterval;
 public Instant lastReviewedAt, nextReviewAt;
 %s
}
''' % (name, '\n'.join(methods))
        (entity / f'{name}.java').write_text(stub)
    harness = '''import java.time.*;
import com.example.bulgariantrainer.service.SpacedRepetitionScheduler;
import com.example.bulgariantrainer.entity.*;
public class ParityHarness {
 public static void main(String[] args) {
  Instant at = Instant.parse(args[0]);
  var scheduler = new SpacedRepetitionScheduler(Clock.fixed(at, ZoneOffset.UTC), 5, 28);
  for (int i = 1; i < args.length; i++) {
   String kind = args[i].substring(0,1), history = args[i].substring(2);
   int attempts, successes, streak, interval; Instant next; LearningCategory category, before = null, deadline = null;
   if (kind.equals("W")) {
    var p = new StudyProgress();
    for (char result : history.toCharArray()) scheduler.record(p, result == 'T');
    attempts=p.totalAttempts; successes=p.successfulReviewCount; streak=p.consecutiveCorrect; interval=p.repetitionInterval; next=p.nextReviewAt;
    category=scheduler.category(p,at);
    if (next!=null) { before=scheduler.category(p,next.minusNanos(1)); deadline=scheduler.category(p,next); }
   } else {
    var p = new SentenceProgress();
    for (char result : history.toCharArray()) scheduler.record(p,result=='T'?AttemptResult.CORRECT:result=='P'?AttemptResult.PARTIAL:AttemptResult.INCORRECT);
    attempts=p.totalAttempts; successes=p.successfulReviewCount; streak=p.consecutiveCorrect; interval=p.repetitionInterval; next=p.nextReviewAt;
    category=scheduler.category(p,at);
    if (next!=null) { before=scheduler.category(p,next.minusNanos(1)); deadline=scheduler.category(p,next); }
   }
   System.out.printf("{\\"kind\\":\\"%s\\",\\"history\\":\\"%s\\",\\"attempts\\":%d,\\"successes\\":%d,\\"streak\\":%d,\\"intervalDays\\":%d,\\"nextReviewAt\\":%s,\\"category\\":\\"%s\\",\\"beforeDeadline\\":%s,\\"atDeadline\\":%s}%n",
    kind,history,attempts,successes,streak,interval,next==null?"null":"\\""+next+"\\"",category,before==null?"null":"\\""+before+"\\"",deadline==null?"null":"\\""+deadline+"\\"");
  }
 }
}
'''
    (directory / 'ParityHarness.java').write_text(harness)
    files = [str(p) for p in (directory / 'src').rglob('*.java')] + [str(directory / 'ParityHarness.java')]
    compiler = ['javac'] if shutil.which('javac') else ['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main']
    subprocess.run([*compiler, '-encoding', 'UTF-8', '-d', str(directory / 'classes'), *files], check=True)
    expected = json.loads((ROOT / 'fixtures/web_scheduler.json').read_text())
    requests = [row['kind'] + ':' + row['history'] for row in expected['cases']]
    result = subprocess.run(['java', '-cp', str(directory / 'classes'), 'ParityHarness', expected['anchor'], *requests], check=True, capture_output=True, text=True)
    actual = [json.loads(line) for line in result.stdout.splitlines()]
    assert actual == expected['cases'], (actual, expected['cases'])
    print('PINNED_WEB_SCHEDULER_PARITY:', len(actual), 'identical histories verified')

# Verify the statistics integration goldens against literal assertions in actual pinned tests.
source = (reference / 'StatisticsIntegrationTests.java').read_text().split('void calculatesRealRetentionAccuracySourcesActivityAndMistakeReview()')[1].split('@Test')[0]
assertions = dict((path, float(value) if '.' in value else int(value)) for path, value in re.findall(
    r'assertThat\(stats\.at\("([^"\n]+)"\)\.as(?:Long|Double)\(\)\)\.isEqualTo\(([\d.]+)\);', source))
expected = json.loads((ROOT / 'fixtures/web_statistics.json').read_text())
assert expected['metrics'] == assertions, (expected['metrics'], assertions)
print('PINNED_WEB_STATISTICS_ASSERTIONS:', len(assertions), 'verified against actual integration-test source')
