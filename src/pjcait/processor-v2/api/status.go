package api

import (
	"fmt"
	"github.com/gin-gonic/gin"
)

type PagedRequest struct {
	Page     int `json:"page"`
	PageSize int `json:"pageSize"`
}

func getStatus(c *gin.Context) {
	pagedRequest := PagedRequest{}
	if err := c.ShouldBindJSON(&pagedRequest); err != nil {
		respCode(c, -1, fmt.Sprintf("参数不正确：%s", err.Error()))
		return
	}

}
