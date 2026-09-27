package api

import (
	"github.com/gin-gonic/gin"
	consul "github.com/hashicorp/consul/api"
)

func getInstanceListHandler(c *gin.Context) {
	serviceName, ok := c.GetQuery("serviceName")
	resp := map[string]interface{}{}
	if !ok || serviceName == "" {
		instances, err := srv.GetAllInstances()
		if err != nil {
			respCode(c, -1, err.Error())
			return
		}

		resp["instances"] = instances
	} else {
		instances, err := srv.GetServiceInstances(serviceName)
		if err != nil {
			respCode(c, -1, err.Error())
			return
		}

		resp["instances"] = map[string][]*consul.AgentService{
			serviceName: instances,
		}
	}

	respData(c, resp)
}

func getAllInstanceListHandler(c *gin.Context) {
	instances, err := srv.GetAllInstances()
	if err != nil {
		respCode(c, -1, err.Error())
		return
	}

	respData(c, instances)
}
