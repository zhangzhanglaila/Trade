package logger

import (
	"log/slog"
	"os"
)

var logger *slog.Logger

func init() {
	logger = slog.New(slog.NewTextHandler(os.Stdout, &slog.HandlerOptions{
		AddSource: false,
		Level:     slog.LevelDebug,
	}))
}

func GetLogger() *slog.Logger {
	return logger
}
