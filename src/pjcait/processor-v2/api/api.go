package api

import (
	"cait-processor/conf"
	"cait-processor/processor"
	"cait-processor/recorder"
	"fmt"
	"github.com/gin-gonic/gin"
	"log/slog"
	"net/http"
)

var (
	historyRecorder *recorder.History
)

func NewEngine(config *conf.Config, worker *processor.ProcessWorker, history *recorder.History) *gin.Engine {
	engine := gin.New()
	engine.Use(gin.Recovery())
	engine.NoRoute(noRouteHandlerFunc)

	setupRouter(engine.Group(config.Server.BasePath), worker)
	historyRecorder = history

	return engine
}

func setupRouter(root *gin.RouterGroup, worker *processor.ProcessWorker) {
	root.GET("/health", healthHandler)

	root.POST("/run", demoProcessHandler(worker))

	root.GET("/history/list", listHistory)
	root.GET("/history/detail", getHistory)
}

func healthHandler(c *gin.Context) {
	c.Status(http.StatusNoContent)
}

func noRouteHandlerFunc(c *gin.Context) {
	slog.Info(fmt.Sprintf("no route for '%s'", c.Request.URL))
	respCode(c, -1, fmt.Sprintf("no route for: %s", c.Request.URL))
}

func respData(c *gin.Context, data any) {
	c.JSON(http.StatusOK, gin.H{
		"code": 0,
		"data": data,
	})
}

func respCode(c *gin.Context, code int, msg string) {
	c.JSON(http.StatusOK, gin.H{
		"code": code,
		"msg":  msg,
	})
	c.Abort()
}
