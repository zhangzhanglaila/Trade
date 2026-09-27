package service

import (
	"cait-panel/conf"
	"cait-panel/logger"
	consul "github.com/hashicorp/consul/api"
	"log/slog"
	"sync"
)

type Service struct {
	logger *slog.Logger
	config *conf.Config
	consul *consul.Client

	instancesMutex sync.Mutex
	instances      map[string]*consul.AgentService
}

func NewService(config *conf.Config) (*Service, error) {
	consulClient, err := consul.NewClient(config.Register.Consul)
	if err != nil {
		return nil, err
	}

	service := &Service{
		logger:    logger.GetLogger(),
		config:    config,
		consul:    consulClient,
		instances: map[string]*consul.AgentService{},
	}

	return service, nil
}
