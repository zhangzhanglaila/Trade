package main

import (
	"cait-panel/api"
	"cait-panel/conf"
	"cait-panel/syncs"
	"context"
	"errors"
	"fmt"
	"log"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"
)

func main() {
	wg := syncs.WaitGroup{}

	ctx, cancel := context.WithCancel(context.Background())

	config := conf.Conf

	engine := api.NewEngine(config)
	httpServer := &http.Server{
		Addr:    fmt.Sprintf("%s:%d", config.Server.Listen, config.Server.Port),
		Handler: engine,
	}

	wg.Go(func() {
		if err := httpServer.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			slog.Error("http server err", "err", err)
		} else {
			slog.Info("http server stopped")
		}
	})

	registerService()

	wg.Go(func() {
		api.StartServiceInstanceUpdater(ctx)
	})

	go func() {
		// 监听终止信号
		sigChan := make(chan os.Signal, 1)
		signal.Notify(sigChan, syscall.SIGINT, syscall.SIGTERM, syscall.SIGKILL)

		<-sigChan
		log.Println("接收到终止信号，退出主程序")
		err := httpServer.Shutdown(ctx)
		if err != nil {
			slog.Error("http server shutdown err", "err", err)
		}
		cancel()
	}()

	wg.Wait()
}
