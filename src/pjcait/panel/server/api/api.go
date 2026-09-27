package api

import (
	"cait-panel/conf"
	"cait-panel/service"
	"context"
	"fmt"
	"github.com/gin-gonic/gin"
	"log/slog"
	"net/http"
)

var (
	srv *service.Service
)

func init() {
	var err error
	srv, err = service.NewService(conf.Conf)
	if err != nil {
		panic(err)
	}
}

func StartServiceInstanceUpdater(ctx context.Context) {
	srv.UpdateInstanceList(ctx)
}

func NewEngine(config *conf.Config) *gin.Engine {
	engine := gin.New()
	//engine.Use(gin.Recovery())
	//engine.NoRoute(noRouteHandlerFunc)

	setupRouter(engine.Group(config.Server.Prefix))

	return engine
}

func setupRouter(root *gin.RouterGroup) {
	root.GET("/health", healthHandler)

	root.GET("/instances", getInstanceListHandler)
	root.GET("/all-instances", getAllInstanceListHandler)

	root.Any("/agent/*proxyPath", reverseProxyHandlerFunc)
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
