package api

import "github.com/gin-gonic/gin"

func listHistory(c *gin.Context) {
	list, err := historyRecorder.ListHistory(c)
	if err != nil {
		respCode(c, -1, err.Error())
		return
	}

	respData(c, list)
}

func getHistory(c *gin.Context) {
	id := c.Query("id")
	if id == "" {
		respCode(c, -1, "Missing query param 'id'")
		return
	}

	history, err := historyRecorder.GetTaskHistory(c, id)
	if err != nil {
		respCode(c, -1, err.Error())
		return
	}

	respData(c, history)
}
