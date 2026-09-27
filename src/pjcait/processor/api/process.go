package api

import (
	"cait-processor/processor"
	"fmt"
	"github.com/gin-gonic/gin"
	"github.com/sashabaranov/go-openai"
	"github.com/yitter/idgenerator-go/idgen"
)

type ProcessRequest struct {
	Content  string `json:"content"`
	Storage  bool   `json:"storage"`
	Thinking struct {
		Enable bool `json:"enable"`
		Budget int  `json:"budget"`
	} `json:"thinking"`
}

type ProcessResponse struct {
	Part   int                                  `json:"part"`
	Err    string                               `json:"err,omitempty"`
	Result *openai.ChatCompletionStreamResponse `json:"result,omitempty"`
}

func demoProcessHandler(worker *processor.ProcessWorker) gin.HandlerFunc {
	return func(c *gin.Context) {
		req := new(ProcessRequest)
		if err := c.ShouldBindJSON(req); err != nil {
			respCode(c, -1, "请求错误")
			return
		}

		c.Header("Content-Type", "text/event-stream; charset=utf-8")
		c.Header("Cache-Control", "no-cache")
		c.Header("Connection", "keep-alive")
		c.Header("Access-Control-Allow-Origin", "*")
		c.Header("Transfer-Encoding", "chunked")

		id := fmt.Sprintf("%d", idgen.NextId())
		thinking := processor.ThinkingParams{}
		if req.Thinking.Enable {
			thinking.EnableThinking = req.Thinking.Enable
		}

		if req.Thinking.Budget != 0 {
			thinking.ThinkingBudget = req.Thinking.Budget
		} else {
			thinking.ThinkingBudget = 4200
		}

		err := worker.ProcessForDemo(c, req.Content, id, req.Storage, thinking, func(result *processor.ProcessResult) {
			if result.Err != nil {
				c.SSEvent("err", ProcessResponse{Part: result.Part, Err: result.Err.Error()})
			} else {
				c.SSEvent("data", ProcessResponse{Part: result.Part, Result: result.Result})
			}
		})

		if err != nil {
			c.SSEvent("err", ProcessResponse{Part: -1, Err: err.Error()})
		} else {
			c.SSEvent("done", "[DONE]")
		}

	}
}

func autoLabelingProcessHandler(c *gin.Context) {

}
