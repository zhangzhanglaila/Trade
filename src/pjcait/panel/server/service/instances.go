package service

import (
	"context"
	"fmt"
	consul "github.com/hashicorp/consul/api"
	"time"
)

func (s *Service) GetInstancesAndHealth(serviceName string) (string, []consul.AgentServiceChecksInfo, error) {
	status, instances, err := s.consul.Agent().AgentHealthServiceByName(serviceName)
	if err != nil {
		return status, nil, err
	}

	return status, instances, nil
}

func (s *Service) GetServiceInstances(serviceName string) ([]*consul.AgentService, error) {
	filter := fmt.Sprintf("Service == \"%s\"", serviceName)
	instances, err := s.consul.Agent().ServicesWithFilter(filter)
	if err != nil {
		return nil, err
	}

	result := make([]*consul.AgentService, 0, len(instances))
	for _, instance := range instances {
		result = append(result, instance)
	}

	return result, nil
}

func (s *Service) GetAllInstances() (map[string][]*consul.AgentService, error) {
	instances, err := s.consul.Agent().Services()
	if err != nil {
		return nil, err
	}

	result := map[string][]*consul.AgentService{}
	for _, instance := range instances {
		if _, ok := result[instance.Service]; !ok {
			result[instance.Service] = []*consul.AgentService{instance}
		} else {
			result[instance.Service] = append(result[instance.Service], instance)
		}
	}

	return result, nil
}

func (s *Service) UpdateInstanceList(ctx context.Context) {
	duration, err := time.ParseDuration(s.config.Register.InstanceUpdateDuration)
	if err != nil {
		duration = time.Second * 5
	}

	ticker := time.NewTicker(duration)
	defer ticker.Stop()

	// do once
	err = s.updateInstanceList()
	if err != nil {
		s.logger.Error("Error getting service list", "err", err)
		return
	}

	for {
		select {
		case <-ctx.Done():
			s.logger.Info("context canceled, stop updating instance list")
			return
		case <-ticker.C:
			err = s.updateInstanceList()
			if err != nil {
				s.logger.Error("Error updating service list", "err", err)
			}
		}
	}
}

func (s *Service) updateInstanceList() error {
	instances, err := s.consul.Agent().Services()
	if err != nil {
		return err
	}

	s.instancesMutex.Lock()
	s.instances = instances
	s.instancesMutex.Unlock()

	return nil
}

func (s *Service) GetInstance(instanceId string) *consul.AgentService {
	s.instancesMutex.Lock()
	defer s.instancesMutex.Unlock()

	pInstance, ok := s.instances[instanceId]
	if !ok {
		return nil
	}

	instance := *pInstance
	return &instance
}
