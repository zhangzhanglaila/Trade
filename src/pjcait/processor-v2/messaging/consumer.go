package messaging

import (
	"cait-processor/pb"
	"context"
	"github.com/golang/protobuf/proto"
	"github.com/segmentio/kafka-go"
	"log/slog"
	"time"
)

type ProcessingMessageConsumer struct {
	kafkaReader *kafka.Reader
}

func NewProcessingMessageConsumer(kafkaReader *kafka.Reader) *ProcessingMessageConsumer {
	return &ProcessingMessageConsumer{kafkaReader: kafkaReader}
}

func (c *ProcessingMessageConsumer) ConsumeMessages(ctx context.Context, processMsgChan chan *pb.NewsProcessMessage) {
	readMsgFailCnt := 0
	for {
		select {
		case <-ctx.Done():
			slog.Info("kafka consumer shutting down")
			return
		default:
			kMsg, err := c.kafkaReader.ReadMessage(ctx)
			if err != nil {
				slog.Error("error reading message from kafka", "err", err)
				if ctx.Err() != nil {
					return
				}

				readMsgFailCnt++
				if readMsgFailCnt <= 10 {
					slog.Info("sleep 1 sec for next kafka msg, cnt ", "cnt", readMsgFailCnt)
					time.Sleep(1 * time.Second)
					continue
				} else {
					break
				}
			}

			msg := &pb.NewsProcessMessage{}
			err = proto.Unmarshal(kMsg.Value, msg)
			if err != nil {
				slog.Error("Failed to unmarshal news processing message", err)
				continue
			}

			processMsgChan <- msg
		}
	}
}
