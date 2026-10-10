#!/usr/bin/env python3
"""Real ASR smoke test, API33 + debug APK + explicitly configured local backend.

No voice fixtures or model responses are bundled. Supply your own audio and an
expected phrase. This seeds debug-only test settings and sends ACTION_SEND; it
never substitutes an ASR response. Use adb reverse to reach your running backend.
"""
import argparse
import json
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from xml.sax.saxutils import escape

p=argparse.ArgumentParser(description=__doc__)
p.add_argument('audio',type=Path)
p.add_argument('--adb',default='adb')
p.add_argument('--serial',default='emulator-5556')
p.add_argument('--endpoint',default='http://127.0.0.1:8765/transcribe')
p.add_argument('--model',default='parakeet-redux')
p.add_argument('--expected',required=True,help='Case-insensitive regex expected in actual transcript')
p.add_argument('--output',type=Path,default=Path('/tmp/escriba-real-e2e'))
p.add_argument('--timeout',type=float,default=180)
p.add_argument('--require-progressive',action='store_true')
a=p.parse_args();a.output.mkdir(parents=True,exist_ok=True)
def adb(*args, data=None):
    return subprocess.run([a.adb,'-s',a.serial,*args],input=data,stdout=subprocess.PIPE,stderr=subprocess.PIPE,check=True,timeout=30).stdout
sdk=adb('shell','getprop','ro.build.version.sdk').decode().strip()
if sdk!='33': raise SystemExit('This compatibility gate requires Android 13/API33; got '+sdk)
name='real-share'+a.audio.suffix.lower()
if not re.fullmatch(r'[a-z0-9_-]+\.[a-z0-9]+',name): raise SystemExit('Invalid file extension')
adb('shell','am','force-stop','dev.brenorb.escriba')
adb('shell','run-as','dev.brenorb.escriba','mkdir','-p','cache','shared_prefs')
adb('shell','run-as','dev.brenorb.escriba','sh','-c',f"'cat > cache/{name}'",data=a.audio.read_bytes())
prefs='<?xml version="1.0" encoding="utf-8"?><map>'+''.join('<string name="'+k+'">'+escape(v)+'</string>' for k,v in {'engine':'remote','endpoint':a.endpoint,'model':a.model,'language':'pt-BR','token':''}.items())+'</map>'
adb('shell','run-as','dev.brenorb.escriba','sh','-c',"'cat > shared_prefs/settings.xml'",data=prefs.encode())
started=time.monotonic()
launch=adb('shell','am','start','-W','-n','dev.brenorb.escriba/.MainActivity','-a','android.intent.action.SEND','-t','application/octet-stream','--eu','android.intent.extra.STREAM','content://dev.brenorb.escriba.fixture/'+name).decode()
first=None;status='';transcript='';observations=[];last_snapshot=None
while time.monotonic()-started<a.timeout:
    adb('shell','uiautomator','dump','/sdcard/escriba-real-e2e.xml')
    raw=adb('exec-out','cat','/sdcard/escriba-real-e2e.xml')
    (a.output/'ui.xml').write_bytes(raw)
    root=ET.fromstring(raw)
    status=next((n.get('text','') for n in root.iter('node') if n.get('resource-id')=='dev.brenorb.escriba:id/1001'),'')
    transcript=next((n.get('text','') for n in root.iter('node') if n.get('resource-id')=='dev.brenorb.escriba:id/1002'),'')
    # Framework numeric IDs need not resolve to named resources.
    texts=[n.get('text','') for n in root.iter('node')]
    if not status: status=next((t for t in texts if t.startswith(('Concluído','Não foi possível','Transcrevendo'))),'')
    if not transcript: transcript=next((t for t in texts if re.match(r'^\d\d:\d\d – \d\d:\d\d\n',t)),'')
    snapshot=(status,transcript)
    if snapshot!=last_snapshot:
        observations.append({'seconds':time.monotonic()-started,'status':status,'transcript_characters':len(transcript)})
        last_snapshot=snapshot
    if transcript and first is None:first=time.monotonic()-started
    if status.startswith(('Concluído','Não foi possível')):break
    time.sleep(.2)
result={'android_api':int(sdk),'model':a.model,'status':status,'transcript':transcript,'first_visible_seconds_upper_bound':first,'complete_seconds_upper_bound':time.monotonic()-started,'launch':launch,'observations':observations}
(a.output/'result.json').write_text(json.dumps(result,ensure_ascii=False,indent=2))
(a.output/'screen.png').write_bytes(adb('exec-out','screencap','-p'))
print(json.dumps(result,ensure_ascii=False,indent=2))
if not status.startswith('Concluído'):raise SystemExit('Real ASR did not complete')
if not re.search(a.expected,transcript,re.I):raise SystemExit('Expected phrase absent from actual transcript')
if a.require_progressive and not any(o['transcript_characters']>0 and o['status'].startswith('Transcrevendo') for o in observations):raise SystemExit('No visible text observed before completion')
print('PASS real Android13 shared-file ASR')
