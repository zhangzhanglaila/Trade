package processor

import (
	"cait-processor/conf"
	"cait-processor/logger"
	"cait-processor/model"
	"cait-processor/pb"
	"cait-processor/recorder"
	"cait-processor/status"
	"cait-processor/storage"
	"context"
	"crypto/md5"
	"encoding/json"
	"errors"
	"fmt"
	"github.com/elastic/go-elasticsearch/v8"
	_ "github.com/mattn/go-sqlite3"
	"github.com/sashabaranov/go-openai"
	"io"
	"log/slog"
	"os"
	"path"
	"strconv"
	"strings"
	"text/template"
	"time"
)

type ProcessWorker struct {
	llm *openai.Client
	es  *elasticsearch.TypedClient

	taskChan chan *pb.NewsProcessMessage

	config *conf.Config

	promptTemplate *template.Template

	n4j *storage.Neo4jStorage

	workerIdx int

	logger *slog.Logger

	history    *recorder.History
	statusMgr  *status.ProcessorStatusMgr
	useForDemo bool
}

func NewProcessWorker(config *conf.Config, workerIdx int, useForDemo bool, taskChan chan *pb.NewsProcessMessage) (*ProcessWorker, error) {
	tmpl, err := template.New("promptTemplate").Parse(config.Extract.PromptTemplate)
	if err != nil {
		return nil, err
	}

	llmApiConfig := config.LLM.Api
	oaiConfig := openai.DefaultConfig(llmApiConfig.SecretKey)
	oaiConfig.BaseURL = llmApiConfig.BaseUrl

	llm := openai.NewClientWithConfig(oaiConfig)

	es, err := elasticsearch.NewTypedClient(*config.Elasticsearch.ClientConfig)
	if err != nil {
		return nil, err
	}

	if config.Elasticsearch.MustAvailable {
		success, err := es.Ping().IsSuccess(context.Background())
		if err != nil {
			return nil, err
		} else if !success {
			return nil, fmt.Errorf("elasticsearch ping not success: %s", err)
		}
	}

	n4j, err := storage.NewNeo4jStorage(config)
	if err != nil {
		return nil, err
	}

	var history *recorder.History
	if useForDemo {
		history, err = recorder.NewHistory(config)
		if err != nil {
			return nil, err
		}
	}

	statusMgr, err := status.NewProcessorStatusMgr(config)
	if err != nil {
		return nil, err
	}
	processor := &ProcessWorker{
		config:         conf.Conf,
		llm:            llm,
		promptTemplate: tmpl,
		es:             es,
		taskChan:       taskChan,
		n4j:            n4j,
		workerIdx:      workerIdx,
		history:        history,
		statusMgr:      statusMgr,
		useForDemo:     useForDemo,
		logger:         logger.GetLogger(),
	}

	return processor, nil
}

func (p *ProcessWorker) ReceiveAndProcess(ctx context.Context) {
	for {
		select {
		case <-ctx.Done():
			p.logger.Info("ProcessWorker worker shutting down", "id", p.workerIdx)
			return
		case msg, ok := <-p.taskChan:
			if !ok {
				p.logger.Info("ProcessWorker task channel closed", "id", p.workerIdx)
				return
			}

			p.logger.Info("Receive NewsProcessMessage", "id", msg.Id, "worker", p.workerIdx)

			err := p.statusMgr.SetTaskStatus(ctx, msg.Id, model.TaskStatusReceived, "task received")
			if err != nil {
				p.logger.Warn("ProcessWorker set task status err", "id", p.workerIdx, "err", err)
			}
			err = p.Process(ctx, msg)
			if err != nil {
				p.logger.Error("error when processing news content",
					"msg", msg, "id", p.workerIdx, "err", err,
				)
			}
		}
	}
}

type ProcessResult struct {
	Part   int
	Result *openai.ChatCompletionStreamResponse
	Err    error
}

func (p *ProcessWorker) Process(ctx context.Context, msg *pb.NewsProcessMessage) error {
	taskStart := time.Now()
	for i, dataKey := range msg.ContentFileKeys {
		dataPartStart := time.Now()
		p.logger.Info("processing", "idx", i, "dataKey", dataKey)

		var (
			content       string
			err           error
			esNewsContent *model.NewsContent
		)

		switch msg.StorageType {
		case pb.ContentFileStorageType_StorageTypeEs:
			esNewsContent, err = p.readContentFromEs(ctx, dataKey)
			if err != nil {
				p.logger.Error("readContentFromEs error, skip", "dataKey", dataKey, "err", err)
				continue
			} else if esNewsContent == nil {
				p.logger.Info("es document id not found", "dataKey", dataKey)
				continue
			}
			content = esNewsContent.Content
		case pb.ContentFileStorageType_StorageTypeFs:
			content, err = p.readContentFromFs(dataKey)
			if err != nil {
				p.logger.Error("readContentFromFs error, skip", dataKey, err)
				continue
			}
		}

		partPrompts, err := p.generatePromptPart(content, "")
		if err != nil {
			p.logger.Error("err when generate prompt %s", err)
			continue
		}

		thinkingParams := ThinkingParams{
			EnableThinking: p.config.Extract.EnableThinking,
			ThinkingBudget: p.config.Extract.ThinkingBudget,
		}

		err = p.statusMgr.SetTaskStatus(ctx, msg.Id, model.TaskStatusProcessing, "processing")
		if err != nil {
			p.logger.Warn("ProcessWorker set task status err", "id", p.workerIdx, "err", err)
		}

		partRequests := p.generateLLMRequest(partPrompts, thinkingParams)
		id := fmt.Sprintf("%x", md5.Sum([]byte(dataKey)))
		for contentPartIdx, request := range partRequests {
			partTimeStart := time.Now()

			err = p.statusMgr.SetStatus(ctx, msg.Id, dataKey, contentPartIdx, model.StatusProcessing)
			if err != nil {
				p.logger.Warn("ProcessWorker set status err", "id", p.workerIdx, "err", err)
			}

			p.logger.Info("sending request to llm", "id", msg.Id, "dataKey", dataKey, "part", contentPartIdx)

			llmResult, err := p.requestLLM(ctx, *request)
			if err != nil {
				p.logger.Error("err when process %s", err)

				err = p.statusMgr.SetStatus(ctx, msg.Id, dataKey, contentPartIdx, model.StatusFailed)
				if err != nil {
					p.logger.Warn("ProcessWorker set status err", "id", p.workerIdx, "err", err)
				}

				continue
			}

			p.logger.Info("storaging extracted information",
				"id", msg.Id, "dataKey", dataKey,
				"part", contentPartIdx, "cost", time.Since(partTimeStart).Seconds(),
			)
			err = p.storageResult(ctx, msg.Id, id, contentPartIdx, llmResult)
			if err != nil {
				p.logger.Error("err when storage result", "id", msg.Id, "dataKey", dataKey, "err", err)
				err = p.statusMgr.SetStatus(ctx, msg.Id, dataKey, contentPartIdx, model.StatusFailed)
				if err != nil {
					p.logger.Warn("ProcessWorker set status err", "id", p.workerIdx, "err", err)
				}
			} else {
				err = p.statusMgr.SetStatus(ctx, msg.Id, dataKey, contentPartIdx, model.StatusDone)
				if err != nil {
					p.logger.Warn("ProcessWorker set status err", "id", p.workerIdx, "err", err)
				}
			}
		}

		p.logger.Info("content process finish",
			"id", msg.Id, "dataKey", dataKey,
			"cost", time.Since(dataPartStart).Seconds(),
		)
	}

	err := p.statusMgr.SetTaskStatus(ctx, msg.Id, model.TaskStatusEnd, "done")
	if err != nil {
		p.logger.Warn("ProcessWorker set status err", "id", p.workerIdx, "err", err)
	}

	p.logger.Info("task process finish", "id", msg.Id, "cost", time.Since(taskStart).Seconds())

	return nil
}

func (p *ProcessWorker) readContentFromEs(ctx context.Context, contentKey string) (*model.NewsContent, error) {
	response, err := p.es.Get("news_content", contentKey).Do(ctx)
	if err != nil {
		return nil, err
	}
	if !response.Found {
		return nil, nil
	}

	newsContent := &model.NewsContent{}
	err = json.Unmarshal(response.Source_, newsContent)
	if err != nil {
		return nil, err
	}

	return newsContent, nil
}

func (p *ProcessWorker) readContentFromFs(contentKey string) (string, error) {
	contentBytes, err := os.ReadFile(contentKey)
	if err != nil {
		p.logger.Error("err when read content data from filesystem: %s", contentKey)
		return "", err
	}

	return string(contentBytes), nil
}

func (p *ProcessWorker) requestLLM(ctx context.Context, req openai.ChatCompletionRequest) (string, error) {
	stream, err := p.llm.CreateChatCompletionStream(ctx, req)
	if err != nil {
		p.logger.Error("err when create completion stream ", "err", err)
		return "", err
	}

	defer stream.Close()

	result := ""
	for {
		resp, err := stream.Recv()
		if err != nil {
			if errors.Is(err, io.EOF) {
				return result, nil
			}

			p.logger.Error("err when receive response", "err", err)
			return "", err
		}

		content := resp.Choices[0].Delta.Content
		if content == "" {
			continue
		}

		result += content
	}
}

func (p *ProcessWorker) storageResult(ctx context.Context, taskId, fileId string, idx int, content string) error {
	switch p.config.Extract.ResultStorageType {
	case "all":
		err1 := p.storageResultNeo4j(ctx, taskId, fileId, idx, content)
		err2 := p.storageResultFs(taskId, fileId, idx, content)
		if err1 != nil {
			slog.Error("error save to neo4j", "err", err1)
		}

		if err2 != nil {
			slog.Error("error save to fs", "err", err2)
		}
	case "neo4j":
		return p.storageResultNeo4j(ctx, taskId, fileId, idx, content)
	case "fs":
		return p.storageResultFs(taskId, fileId, idx, content)
	}

	return nil
}

func (p *ProcessWorker) storageResultNeo4j(ctx context.Context, taskId, fileId string, idx int, content string) error {
	result := &model.LLMResult{}
	err := json.Unmarshal([]byte(content), result)
	if err != nil {
		p.logger.Error("err when unmarshal result, fallback to fs storage", "err", err)
		return p.storageResultFs(taskId, fileId, idx, content)
	}

	err = p.n4j.Insert(ctx, result)
	if err != nil {
		p.logger.Error("err when insert result, fallback to fs storage", "err", err)
		return p.storageResultFs(taskId, fileId, idx, content)
	}

	return nil
}

func (p *ProcessWorker) storageResultFs(taskId, fileId string, idx int, content string) error {
	config := p.config.Extract
	fileName := fmt.Sprintf("part-%d.json", idx)
	fileDir := path.Join(config.ResultStorageLocation, taskId, fileId)
	if err := os.MkdirAll(fileDir, 0775); err != nil {
		return err
	}

	filePath := path.Join(fileDir, fileName)

	err := os.WriteFile(filePath, []byte(content), 0644)
	if err != nil {
		return err
	}

	return nil
}

func (p *ProcessWorker) generatePromptPart(content string, date string) ([]string, error) {
	contentSplits := strings.Split(content, "\n")

	results := make([]string, 0, len(contentSplits))
	for _, split := range contentSplits {
		tmplResult := &strings.Builder{}
		err := p.promptTemplate.Execute(tmplResult, map[string]string{
			"content": split,
		})

		if err != nil {
			p.logger.Error("err when generate prompt %s", err)
			continue
		}

		results = append(results, tmplResult.String())
	}

	return results, nil
}

type chatCompletionRequest struct {
	openai.ChatCompletionRequest
	EnableThinking bool
	ThinkingBudget int
}

var systemPrompt = openai.ChatCompletionMessage{
	Role:    "system",
	Content: "你将帮助用户完成语言信息的处理，按照用户给定的规则进行处理，并严格按照用户要求的格式输出答案",
}

type ThinkingParams struct {
	EnableThinking bool
	ThinkingBudget int
}

func (p *ProcessWorker) generateLLMRequest(inputs []string, thinkingParams ThinkingParams) []*openai.ChatCompletionRequest {
	requests := make([]*openai.ChatCompletionRequest, 0, len(inputs))
	for _, input := range inputs {
		msg := openai.ChatCompletionMessage{
			Role:    "user",
			Content: input,
		}

		temperature := 0.3
		tmpStr, ok := p.config.LLM.Api.GenerateConfigs["Temperature"]
		if ok {
			_t, err := strconv.ParseFloat(tmpStr, 32)
			if err == nil {
				temperature = _t
			}
		}

		requests = append(requests, &openai.ChatCompletionRequest{
			Model:               p.config.LLM.Api.Model,
			Messages:            []openai.ChatCompletionMessage{systemPrompt, msg},
			MaxTokens:           6000,
			MaxCompletionTokens: 4000,
			Temperature:         float32(temperature),
			TopP:                0.95,
			Stream:              true,
			EnableThinking:      thinkingParams.EnableThinking,
			ThinkingBudget:      thinkingParams.ThinkingBudget,
		})
	}

	return requests
}
