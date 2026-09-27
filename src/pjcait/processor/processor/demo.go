package processor

import (
	"cait-processor/model"
	"context"
	"errors"
	"github.com/sashabaranov/go-openai"
	"github.com/yitter/idgenerator-go/idgen"
	"io"
	"strings"
	"time"
)

func (p *ProcessWorker) ProcessForDemo(ctx context.Context,
		content, id string, storage bool, thinkingParams ThinkingParams,
		resultCb func(response *ProcessResult),
) error {
	if !p.useForDemo {
		return nil
	}

	err := p.statusMgr.SetTaskStatus(ctx, id, model.TaskStatusProcessing, "task processing")
	if err != nil {
		p.logger.Warn("error when set task status", "id", id, "err", err)
	}

	contentParts := strings.Split(content, "\n")

	partPrompts, err := p.generatePromptPart(content, "")
	if err != nil {
		p.logger.Error("[ProcessForDemo] err when generate prompt", "err", err)
		return err
	}

	textResult := map[int]string{}
	thinkResult := map[int]string{}

	timeStart := time.Now()
	partRequests := p.generateLLMRequest(partPrompts, thinkingParams)
	for contentPartIdx, request := range partRequests {
		err := p.statusMgr.SetStatus(ctx, id, id, contentPartIdx, model.StatusProcessing)
		if err != nil {
			p.logger.Warn("error when set status", "id", id, "part", contentPartIdx, "err", err)
		}

		partTimeStart := time.Now()

		p.logger.Info("[ProcessForDemo] sending request to llm",
			"id", id, "part", contentPartIdx,
		)

		responseCb := func(response *openai.ChatCompletionStreamResponse) {
			textResult[contentPartIdx] += response.Choices[0].Delta.Content
			thinkResult[contentPartIdx] += response.Choices[0].Delta.ReasoningContent

			result := &ProcessResult{Part: contentPartIdx, Result: response}
			resultCb(result)
		}

		err = p.requestLLM2(ctx, *request, responseCb)
		if err != nil {
			p.logger.Error("[ProcessForDemo] err when sending request to llm",
				"id", id, "part", contentPartIdx,
			)

			textResult[contentPartIdx] = err.Error()

			err = p.statusMgr.SetStatus(ctx, id, id, contentPartIdx, model.StatusFailed)
			if err != nil {
				p.logger.Warn("error when set status", "id", id, "part", contentPartIdx, "err", err)
			}

			resultCb(&ProcessResult{Part: contentPartIdx, Err: err})
			continue
		}

		err = p.statusMgr.SetStatus(ctx, id, id, contentPartIdx, model.StatusDone)
		if err != nil {
			p.logger.Warn("error when set status", "id", id, "part", contentPartIdx, "err", err)
		}

		err = p.statusMgr.SetTaskStatus(ctx, id, model.TaskStatusEnd, "task done")
		if err != nil {
			p.logger.Warn("error when set task status", "id", id, "err", err)
		}

		history := &model.History{
			ID:           idgen.NextId(),
			TaskID:       id,
			Part:         contentPartIdx,
			Content:      strings.TrimSpace(contentParts[contentPartIdx]),
			Result:       textResult[contentPartIdx],
			ReasonResult: thinkResult[contentPartIdx],
			CreateTime:   time.Now(),
			Status:       0,
		}

		_, err = p.history.RecordHistory(ctx, history)
		if err != nil {
			p.logger.Warn("[ProcessForDemo] err when recording history", "id", id, "err", err)
		}

		if storage {
			err = p.storageResult(ctx, id, id, contentPartIdx, textResult[contentPartIdx])
			if err != nil {
				p.logger.Error("[ProcessForDemo] err when storing result", "id", id, "err", err)
			}
		}

		p.logger.Info("[ProcessForDemo] content processed finish",
			"id", id,
			"part", contentPartIdx,
			"cost", time.Since(partTimeStart),
		)
	}

	p.logger.Info("[ProcessForDemo] all parts process finish",
		"id", id,
		"cost", time.Since(timeStart),
	)

	return nil
}

func (p *ProcessWorker) requestLLM2(
		ctx context.Context,
		req openai.ChatCompletionRequest,
		responseCb func(response *openai.ChatCompletionStreamResponse),
) error {
	stream, err := p.llm.CreateChatCompletionStream(ctx, req)
	if err != nil {
		p.logger.Error("err when create completion stream ", "err", err)
		return err
	}

	defer stream.Close()

	for {
		resp, err := stream.Recv()
		if err != nil {
			if errors.Is(err, io.EOF) {
				return nil
			}

			p.logger.Error("err when receive response", "err", err)
			return err
		}

		responseCb(&resp)
	}
}
