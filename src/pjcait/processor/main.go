package main

import (
	"cait-processor/api"
	"cait-processor/conf"
	"cait-processor/messaging"
	"cait-processor/pb"
	"cait-processor/processor"
	"cait-processor/recorder"
	"cait-processor/syncs"
	"context"
	"errors"
	"fmt"
	"github.com/segmentio/kafka-go"
	"github.com/yitter/idgenerator-go/idgen"
	"log"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"
)

func init() {
	options := idgen.NewIdGeneratorOptions(1)
	idgen.SetIdGenerator(options)
}

func main() {
	wg := syncs.WaitGroup{}

	ctx, cancel := context.WithCancel(context.Background())

	config := conf.Conf
	if config.LLM.LocalBackend.Enable {
		runLLMServer(ctx)
	}

	processMsgChan := make(chan *pb.NewsProcessMessage, 100)

	wg.Go(startProcessMsgKafkaConsumer(ctx, config, processMsgChan))

	for i := 1; i <= config.Processor.WorkerNum; i++ {
		wg.Go(startProcessWorker(ctx, config, i, processMsgChan))
	}

	processorWorker, err := processor.NewProcessWorker(config, 0, true, processMsgChan)
	if err != nil {
		panic(err)
	}

	history, err := recorder.NewHistory(config)
	if err != nil {
		slog.Error("error when NewHistory()")
	}

	engine := api.NewEngine(config, processorWorker, history)
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

var (
	kafkaConsumer *messaging.ProcessingMessageConsumer
)

func startProcessMsgKafkaConsumer(ctx context.Context, config *conf.Config, processMsgChan chan *pb.NewsProcessMessage) func() {
	kafkaConfig := config.Kafka
	kafkaReader := kafka.NewReader(kafka.ReaderConfig{
		Brokers:   kafkaConfig.Brokers,
		Topic:     KafkaTopicProcessJob,
		Partition: kafkaConfig.Partition,
		GroupID:   kafkaConfig.GroupId,
		MaxBytes:  10e6, // 10MB
		MaxWait:   time.Second * 1,
	})

	kafkaConsumer = messaging.NewProcessingMessageConsumer(kafkaReader)
	return func() {
		kafkaConsumer.ConsumeMessages(ctx, processMsgChan)
	}
}

func startProcessWorker(ctx context.Context, config *conf.Config, workerIdx int, processMsgChan chan *pb.NewsProcessMessage) func() {
	return func() {
		processorWorker, err := processor.NewProcessWorker(config, workerIdx, false, processMsgChan)
		if err != nil {
			panic(err)
		}

		slog.Info("starting process worker", "id", workerIdx)
		processorWorker.ReceiveAndProcess(ctx)
	}
}
