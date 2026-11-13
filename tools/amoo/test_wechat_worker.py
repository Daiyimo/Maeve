"""Offline contract tests; never contact Amoo or send a WeChat message."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import subprocess

SOURCE = Path(__file__).resolve().parents[2] / "app/src/main/assets/amoo/wechat_worker.py"
spec = importlib.util.spec_from_file_location("wechat_worker", SOURCE)
worker = importlib.util.module_from_spec(spec)
spec.loader.exec_module(worker)


class WorkerTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name) / "wechat"
        self.ident = "a" * 64
        self.request = self.root / "requests" / (self.ident + ".json")
        self.reply = self.root / "replies" / (self.ident + ".json")
        worker.write(self.root / "control.json", {"enabled": True, "session": "private-session", "allowMaeve": False})
        worker.write(self.request, {"id": self.ident, "session": "private-session", "text": "你好; $(echo secret)"})

    @patch.object(worker, "run_cli")
    def test_round_trip_keeps_session_and_passes_text_without_a_shell(self, run):
        run.return_value = subprocess.CompletedProcess([], 0, json.dumps({"result": {"payloads": [{"text": "你好，主人"}]}}), "")
        worker.process(self.root, self.request, "openclaw")
        value = worker.read(self.reply)
        self.assertEqual(value["text"], "你好，主人")
        args, kwargs = run.call_args
        command = args[0]
        self.assertIn("--session-id", command)
        self.assertIn("用户已关闭", command[command.index("--message") + 1])
        self.assertIn("$(echo secret)", command[command.index("--message") + 1])
        self.assertNotIn("shell", kwargs)
        self.assertFalse(self.request.exists())

    @patch.object(worker, "run_cli")
    def test_disabled_or_wrong_account_never_invokes_amoo(self, run):
        worker.write(self.root / "control.json", {"enabled": False, "session": "private-session"})
        worker.process(self.root, self.request, "openclaw")
        run.assert_not_called()
        worker.write(self.root / "control.json", {"enabled": True, "session": "new-session"})
        worker.process(self.root, self.request, "openclaw")
        run.assert_not_called()
        self.assertFalse(self.request.exists())

    @patch.object(worker, "run_cli")
    def test_restart_does_not_repeat_a_possibly_executed_turn(self, run):
        claimed = self.request.with_suffix(".working")
        self.request.rename(claimed)
        worker.process(self.root, claimed, "openclaw", recover=True)
        run.assert_not_called()
        self.assertIn("意外中断", worker.read(self.reply)["text"])

    @patch.object(worker, "run_cli")
    def test_normal_poll_never_mislabels_an_active_claim_as_interrupted(self, run):
        claimed = self.request.with_suffix(".working")
        self.request.rename(claimed)
        worker.process(self.root, claimed, "openclaw")
        self.assertNotIn(claimed, worker.queued(self.root))
        self.assertFalse(self.reply.exists())
        run.assert_not_called()

    def test_directory_owner_excludes_another_worker_without_flock(self):
        owner = worker.acquire_owner(self.root)
        self.assertIsNotNone(owner)
        with patch.object(worker, 'alive', return_value=True):
            self.assertIsNone(worker.acquire_owner(self.root))
        worker.release_owner(self.root, owner)
        next_owner = worker.acquire_owner(self.root)
        self.assertNotEqual(owner['nonce'], next_owner['nonce'])
        worker.release_owner(self.root, next_owner)

    def test_dead_owner_is_replaced_without_replaying_a_turn(self):
        owner = worker.acquire_owner(self.root)
        with patch.object(worker, 'alive', return_value=False):
            replacement = worker.acquire_owner(self.root)
        self.assertNotEqual(owner['nonce'], replacement['nonce'])
        worker.release_owner(self.root, owner)
        self.assertTrue((self.root / 'worker-owner').exists())
        worker.release_owner(self.root, replacement)

    @patch.object(worker.os, 'killpg', create=True)
    @patch.object(worker.subprocess, 'Popen')
    def test_cli_timeout_terminates_the_process_group_and_retains_diagnostics(self, popen, killpg):
        child = popen.return_value
        child.pid = 4321
        child.poll.return_value = None
        def start(*args, **kwargs):
            kwargs['stdout'].write('partial'); kwargs['stdout'].flush()
            kwargs['stderr'].write('diagnostic'); kwargs['stderr'].flush()
            return child
        popen.side_effect = start
        with self.assertRaises(subprocess.TimeoutExpired) as raised:
            worker.run_cli(['openclaw'], timeout=0)
        killpg.assert_called_once_with(4321, worker.signal.SIGTERM)
        self.assertEqual(raised.exception.output, 'partial')
        self.assertEqual(raised.exception.stderr, 'diagnostic')
        self.assertTrue(popen.call_args.kwargs['start_new_session'])

    @patch.object(worker.os, 'killpg', create=True)
    @patch.object(worker.subprocess, 'Popen')
    def test_final_json_is_returned_even_when_stepos_cli_does_not_exit(self, popen, killpg):
        child = popen.return_value
        child.pid = 4321
        child.poll.return_value = None
        def start(*args, **kwargs):
            json.dump({'status': 'ok', 'result': {'payloads': [{'text': 'Amoo 已回复'}]}}, kwargs['stdout'])
            kwargs['stdout'].flush()
            return child
        popen.side_effect = start
        result = worker.run_cli(['openclaw', 'agent', '--json'], timeout=1)
        self.assertEqual(result.returncode, 0)
        self.assertEqual(worker.answer_text(json.loads(result.stdout)), 'Amoo 已回复')
        killpg.assert_called_once_with(4321, worker.signal.SIGTERM)

    @patch.object(worker, "run_cli")
    def test_cached_reply_does_not_invoke_a_second_turn(self, run):
        worker.write(self.reply, {"id": self.ident, "session": "private-session", "text": "已完成"})
        worker.process(self.root, self.request, "openclaw")
        run.assert_not_called()

    @patch.object(worker, "run_cli")
    def test_failure_has_explicit_message_instead_of_success(self, run):
        run.return_value = subprocess.CompletedProcess([], 1, "secret diagnostic", "secret")
        worker.process(self.root, self.request, "openclaw")
        text = worker.read(self.reply)["text"]
        self.assertIn("未能完成", text)
        self.assertNotIn("secret", text)

    def test_output_shapes_and_failures(self):
        self.assertEqual(worker.answer_text({"payloads": [{"text": "a"}, {"text": "b"}]}), "a\n\nb")
        self.assertEqual(worker.answer_text({"ok": True, "final": "ok"}), "ok")
        with self.assertRaises(ValueError):
            worker.answer_text({"ok": False, "payloads": [{"text": "wrong"}]})
        with self.assertRaises(ValueError):
            worker.answer_text({"result": {"payloads": []}})
        with self.assertRaises(ValueError):
            worker.answer_text({"status": "running", "payloads": [{"text": "进度"}]})

    def test_queue_uses_receive_order_instead_of_hash_order(self):
        worker.write(self.request, {"id": self.ident, "sequence": 2})
        older = self.request.with_name('z' * 64 + '.json')
        worker.write(older, {"sequence": 1})
        self.assertEqual(worker.queued(self.root), [older, self.request])

    @patch.object(worker, "run_cli")
    def test_unbind_during_a_turn_discards_the_old_reply(self, run):
        def unbind(*args, **kwargs):
            worker.write(self.root / "control.json", {"enabled": False, "session": ""})
            return subprocess.CompletedProcess([], 0, json.dumps({"payloads": [{"text": "旧消息"}]}), "")
        run.side_effect = unbind
        worker.process(self.root, self.request, "openclaw")
        self.assertFalse(self.reply.exists())
        self.assertFalse(self.request.with_suffix('.working').exists())


if __name__ == "__main__":
    unittest.main()
