import io
import os
import subprocess
import tempfile
import time
import unittest
import wave
from pathlib import Path
from unittest.mock import patch
from server.escriba_server import transcribe


@unittest.skipUnless(os.name == 'posix', 'POSIX process group cleanup')
class ProcessLifecycle(unittest.TestCase):
    def test_timeout_stops_descendant_installer_and_removes_audio(self):
        audio = io.BytesIO()
        with wave.open(audio, 'wb') as out:
            out.setparams((1, 2, 16000, 0, 'NONE', 'not compressed'))
            out.writeframes(b'\0\0' * 16000)
        with tempfile.TemporaryDirectory() as directory:
            marker = Path(directory) / 'child.pid'
            script = Path(directory) / 'fscript'
            script.write_text('#!/usr/bin/env python3\nimport subprocess, sys, time\nfrom pathlib import Path\nchild = subprocess.Popen([sys.executable, "-c", "import time; time.sleep(60)"])\nPath(' + repr(str(marker)) + ').write_text(str(child.pid) + "\\n" + sys.argv[1])\ntime.sleep(60)\n')
            script.chmod(0o700)
            real_run = subprocess.run
            real_communicate = subprocess.Popen.communicate
            def quick_run(*args, **kwargs):
                kwargs['timeout'] = 0.5
                return real_run(*args, **kwargs)
            def quick_communicate(process, *args, **kwargs):
                if kwargs.get('timeout') is not None:
                    kwargs['timeout'] = 0.5
                return real_communicate(process, *args, **kwargs)
            started = time.monotonic()
            try:
                with patch('server.escriba_server.subprocess.run', side_effect=quick_run), patch('subprocess.Popen.communicate', quick_communicate):
                    with self.assertRaises(RuntimeError):
                        transcribe(audio.getvalue(), 'parakeet-redux', 'cpu', str(script))
                self.assertLess(time.monotonic() - started, 3)
                pid, path = marker.read_text().splitlines()
                self.assertFalse(Path(path).exists(), 'Remove temporary audio after failure')
                time.sleep(0.1)
                state = real_run(['ps', '-o', 'stat=', '-p', pid], capture_output=True, text=True).stdout.strip()
                self.assertTrue(not state or state.startswith('Z'), 'Installer descendant survives request timeout: ' + state)
            finally:
                if marker.exists():
                    try:
                        os.kill(int(marker.read_text().splitlines()[0]), 9)
                    except ProcessLookupError:
                        pass
