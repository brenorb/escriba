import subprocess
import unittest
from unittest.mock import patch
from server.escriba_server import main


class Startup(unittest.TestCase):
    def test_old_fast_transcript_stops_before_listening(self):
        with patch('sys.argv', ['escriba_server']), patch('server.escriba_server.subprocess.run', side_effect=subprocess.CalledProcessError(2, ['fscript', '--list-models'])), patch('server.escriba_server.ThreadingHTTPServer') as server:
            with self.assertRaises(SystemExit):
                main()
            server.assert_not_called()

    def test_supported_presets_start_server(self):
        output = 'parakeet-redux parakeet-v3-int8 parakeet-ultra phonon-2'
        with patch('sys.argv', ['escriba_server']), patch('server.escriba_server.subprocess.run', return_value=subprocess.CompletedProcess([], 0, stdout=output)), patch('server.escriba_server.ThreadingHTTPServer') as server:
            main()
            server.return_value.serve_forever.assert_called_once()
