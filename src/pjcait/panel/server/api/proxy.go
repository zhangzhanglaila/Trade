package api

import (
	"fmt"
	"github.com/gin-gonic/gin"
	"net/http"
	"net/http/httputil"
	"net/url"
)

func reverseProxyHandlerFunc(c *gin.Context) {
	targetInstanceId := c.GetHeader("x-target-instance")
	if targetInstanceId == "" {
		respCode(c, -1, "Missing Header 'X-Target-Instance'")
		return
	}

	targetInstance := srv.GetInstance(targetInstanceId)
	if targetInstance == nil {
		respCode(c, -1, fmt.Sprintf("Target instance '%s' not found", targetInstanceId))
		return
	}

	targetURL := &url.URL{
		Scheme: "http",
		Host:   fmt.Sprintf("%s:%d", targetInstance.Address, targetInstance.Port),
	}

	proxy := httputil.NewSingleHostReverseProxy(targetURL)

	proxy.Director = func(req *http.Request) {
		req.URL.Scheme = targetURL.Scheme
		req.URL.Host = targetURL.Host

		req.URL.Path = c.Param("proxyPath")

		req.URL.RawQuery = c.Request.URL.RawQuery
		req.Header = c.Request.Header.Clone()

		req.Host = targetURL.Host
	}

	proxy.ServeHTTP(c.Writer, c.Request)
}
