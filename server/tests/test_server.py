import io
import json
import tempfile
import unittest
import wave
from pathlib import Path
from unittest.mock import patch
from server.escriba_server import decode_request, transcribe


def wav():
    b=io.BytesIO()
    with wave.open(b,'wb') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(16000); w.writeframes(b'\0\0'*160)
    return b.getvalue()

class Contracts(unittest.TestCase):
    def test_multipart_audio(self):
        body=b'--test\r\nContent-Disposition: form-data; name="file"; filename="part.wav"\r\nContent-Type: audio/wav\r\n\r\n'+wav()+b'\r\n--test--\r\n'
        self.assertEqual(decode_request('multipart/form-data; boundary=test',body),wav())
    def test_reject_empty_and_non_pcm(self):
        for b in [b'',b'not audio']:
            with self.assertRaises(ValueError): decode_request('audio/wav',b)
    def test_reject_missing_file(self):
        with self.assertRaises(ValueError): decode_request('multipart/form-data; boundary=x',b'--x--\r\n')
    def test_command_is_argv_and_temp_audio_removed(self):
        with patch('server.escriba_server.subprocess.run') as run:
            run.return_value.stdout='Olá mundo.\n'; run.return_value.returncode=0
            self.assertEqual(transcribe(wav(),'parakeet-v3-int8','cpu','/a path/fscript'),'Olá mundo.')
            argv=run.call_args.args[0]
            self.assertEqual(argv[0],'/a path/fscript')
            self.assertIn('--no-diarization',argv)
            self.assertFalse(Path(argv[1]).exists())
    def test_invalid_model_rejected(self):
        with self.assertRaises(ValueError): transcribe(wav(),'../../bad','cpu','fscript')
    def test_backend_failure_not_a_transcript(self):
        import subprocess
        with patch('server.escriba_server.subprocess.run',side_effect=subprocess.CalledProcessError(1,['fscript'])):
            with self.assertRaises(RuntimeError): transcribe(wav(),'parakeet-ultra','mps','fscript')

if __name__=='__main__': unittest.main()
