package main

import (
	"cait-processor/conf"
	"context"
	"log/slog"
	"os"
	"os/exec"
	"syscall"
	"time"
)

func runLLMServer(ctx context.Context) {
	config := conf.Conf.LLM.LocalBackend
	args := append([]string{config.RunExec}, config.ExecArgs...)

	cmd := &exec.Cmd{
		Path:   config.RunExec,
		Args:   args,
		Dir:    config.Workdir,
		Stdin:  nil,
		Stdout: os.Stdout,
		Stderr: os.Stderr,
	}

	slog.Info("启动子进程...")
	err := cmd.Start()
	if err != nil {
		slog.Warn("子进程启动失败: ", "err", err)
		return
	}

	if !config.Wait {
		return
	}

	exitedChan := make(chan any, 1)

	go func() {
		for {
			select {
			case <-ctx.Done():
				err := cmd.Process.Signal(syscall.SIGTERM)
				if err != nil {
					slog.Info("sending SIGTERM to PID", "pid", cmd.Process.Pid, "error", err.Error())
				}
				return
			case <-exitedChan:
				slog.Info("process exited")
				return
			}
		}
	}()

	go func() {
		err = cmd.Run()
		close(exitedChan)
		if err != nil {
			slog.Warn("子进程异常退出", "err", err.Error())
			return
		}

		slog.Info("子进程正常退出", "time", time.Now().Unix())
	}()
}
