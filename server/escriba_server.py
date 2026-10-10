#!/usr/bin/env python3
"""Dependency-free, explicit self-hosted adapter to fast-transcript.

Never expose without TLS reverse proxy and ESCRIBA_TOKEN. No transcript logging.
"""
import argparse
import hmac
import io
import json
import os
import signal
import subprocess
import tempfile
import threading
import wave
from email.parser import BytesParser
from email.policy import default
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

MODELS={'parakeet-v3-int8','parakeet-redux','parakeet-ultra','phonon-2'}
MAX_BODY=2*1024*1024
LOCK=threading.Lock()


def decode_request(content_type, body):
    if len(body)>MAX_BODY: raise ValueError('Audio exceeds size limit')
    if content_type.startswith('multipart/form-data'):
        message=BytesParser(policy=default).parsebytes(('Content-Type: '+content_type+'\r\nMIME-Version: 1.0\r\n\r\n').encode()+body)
        files=[p.get_payload(decode=True) for p in message.iter_parts() if p.get_param('name',header='content-disposition')=='file'] if message.is_multipart() else []
        if len(files)!=1: raise ValueError('Exactly one audio file required')
        body=files[0]
    elif content_type != 'audio/wav': raise ValueError('WAV or multipart required')
    try:
        with wave.open(io.BytesIO(body),'rb') as w:
            if w.getnchannels()!=1 or w.getsampwidth()!=2 or w.getframerate()!=16000 or w.getnframes()>16000*31 or w.getnframes()==0:
                raise ValueError('Expected nonempty 16-kHz mono PCM16, at most 31 seconds')
            if len(w.readframes(w.getnframes())) != w.getnframes()*2:
                raise ValueError('Truncated WAV')
    except (wave.Error, EOFError) as e: raise ValueError('Invalid WAV') from e
    return body


def request_options(content_type, body, default_model):
    model=default_model
    if content_type.startswith('multipart/form-data'):
        message=BytesParser(policy=default).parsebytes(('Content-Type: '+content_type+'\r\nMIME-Version: 1.0\r\n\r\n').encode()+body)
        if message.is_multipart():
            values=[part.get_payload(decode=True).decode('utf-8') for part in message.iter_parts() if part.get_param('name',header='content-disposition')=='model']
            if len(values)>1: raise ValueError('Duplicate model field')
            if values: model=values[0]
    if model not in MODELS: raise ValueError('Unsupported model')
    return model


def transcribe(audio,model,device,binary):
    if model not in MODELS or device not in {'auto','cpu','mps','mlx','cuda'}: raise ValueError('Unsupported model/device')
    # Codec delay can leave a few milliseconds after a full mobile window.
    # Some neural frontends require a minimum input length: retain every source
    # sample and append silence, rather than dropping the last part of speech.
    with wave.open(io.BytesIO(audio),'rb') as source:
        if source.getnframes() < 16000:
            frames=source.readframes(source.getnframes())
            output=io.BytesIO()
            with wave.open(output,'wb') as padded:
                padded.setparams(source.getparams())
                padded.writeframes(frames+b'\0'*(16000-source.getnframes())*source.getnchannels()*source.getsampwidth())
            audio=output.getvalue()
    with tempfile.TemporaryDirectory(prefix='escriba-') as directory:
        path=os.path.join(directory,'part.wav')
        with open(path,'wb') as f: f.write(audio)
        try:
            process=subprocess.Popen([binary,path,'--text=plain','--stdout','--no-diarization','--raw','--chunk','0','--model',model,'--device',device],stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True,start_new_session=os.name=='posix')
            try:
                # First use may provision a runtime and download model weights.
                stdout,_=process.communicate(timeout=600)
            except subprocess.TimeoutExpired:
                if os.name=='posix':
                    try: os.killpg(process.pid,signal.SIGKILL)
                    except ProcessLookupError: pass
                else:
                    # taskkill also terminates the installer's descendants on Windows.
                    subprocess.run(['taskkill','/PID',str(process.pid),'/T','/F'],capture_output=True,timeout=10)
                    process.kill()
                process.communicate()
                raise
            if process.returncode:
                raise subprocess.CalledProcessError(process.returncode,[binary])
        except (OSError,subprocess.SubprocessError) as e: raise RuntimeError('ASR backend failed; check server installation/model') from e
        return stdout.strip()


def handler_factory(model,device,binary,token):
    class Handler(BaseHTTPRequestHandler):
        def log_message(self,*args): pass
        def reply(self,code,value):
            data=json.dumps(value,ensure_ascii=False).encode()
            self.send_response(code); self.send_header('Content-Type','application/json; charset=utf-8'); self.send_header('Content-Length',str(len(data))); self.end_headers(); self.wfile.write(data)
        def do_POST(self):
            self.connection.settimeout(30)
            if self.path!='/transcribe': return self.reply(404,{'error':'Not found'})
            if token and not hmac.compare_digest(self.headers.get('Authorization',''),'Bearer '+token): return self.reply(401,{'error':'Unauthorized'})
            try:
                size=int(self.headers.get('Content-Length','0'))
                if size<=0 or size>MAX_BODY: return self.reply(413,{'error':'Invalid size'})
                body=self.rfile.read(size)
                if len(body)!=size: raise ValueError('Truncated request')
                audio=decode_request(self.headers.get('Content-Type',''),body)
                selected=request_options(self.headers.get('Content-Type',''),body,model)
                if not LOCK.acquire(blocking=False): return self.reply(503,{'error':'Backend busy; try again'})
                try: text=transcribe(audio,selected,device,binary)
                finally: LOCK.release()
                self.reply(200,{'text':text})
            except ValueError as e: self.reply(400,{'error':str(e)})
            except RuntimeError as e: self.reply(502,{'error':str(e)})
            except (TimeoutError,ConnectionError): pass
    return Handler


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--bind',default='127.0.0.1'); p.add_argument('--port',type=int,default=8765)
    p.add_argument('--model',choices=sorted(MODELS),default='parakeet-redux'); p.add_argument('--device',default='auto',choices=['auto','cpu','mps','mlx','cuda'])
    p.add_argument('--binary',default='fscript')
    a=p.parse_args(); token=os.environ.get('ESCRIBA_TOKEN','')
    if a.bind not in {'127.0.0.1','localhost','::1'} and not token: p.error('Set ESCRIBA_TOKEN when listening beyond loopback')
    print(f'Escriba listening on {a.bind}:{a.port}; model={a.model}',flush=True)
    ThreadingHTTPServer((a.bind,a.port),handler_factory(a.model,a.device,a.binary,token)).serve_forever()

if __name__=='__main__': main()
