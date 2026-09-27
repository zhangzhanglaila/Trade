package main

import (
	"cait-panel/conf"
	"github.com/hashicorp/consul/api"
	"log/slog"
)

var consulClient *api.Client

func init() {
	config := conf.Conf
	var err error
	consulClient, err = api.NewClient(config.Register.Consul)
	if err != nil {
		slog.Warn("consul client init err:", err)
	}
}

func registerService() {
	if consulClient == nil {
		slog.Warn("consul client == nil, skip register")
		return
	}

	config := conf.Conf
	registerConfig := config.Register

	reg := &api.AgentServiceRegistration{
		ID:      registerConfig.InstanceId,
		Name:    registerConfig.ServiceName,
		Port:    registerConfig.Report.Port,
		Address: registerConfig.Report.Address,
		Check: &api.AgentServiceCheck{
			HTTP:     registerConfig.Report.HealthCheckUrl,
			Interval: "10s",
			Timeout:  "1s",
		},
	}

	err := consulClient.Agent().ServiceRegister(reg)
	if err != nil {
		slog.Warn("Failed to register service to Consul: %v", err)
		return
	}

	slog.Info("Service registered to Consul")
}
