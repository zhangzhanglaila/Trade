package storage

import (
	"cait-processor/conf"
	"cait-processor/logger"
	"cait-processor/model"
	"context"
	"fmt"
	"github.com/neo4j/neo4j-go-driver/v5/neo4j"
	"log/slog"
)

type Neo4jStorage struct {
	driver neo4j.DriverWithContext
	config *conf.Config
	logger *slog.Logger
}

func NewNeo4jStorage(config *conf.Config) (*Neo4jStorage, error) {
	n4jConfig := config.Neo4j
	auth := neo4j.BasicAuth(n4jConfig.Username, n4jConfig.Password, "")
	driver, err := neo4j.NewDriverWithContext(n4jConfig.Uri, auth)
	if err != nil {
		return nil, err
	}

	n := &Neo4jStorage{
		driver: driver,
		config: config,
		logger: logger.GetLogger(),
	}

	return n, nil
}

func (n *Neo4jStorage) Insert(ctx context.Context, result *model.LLMResult) error {
	session := n.driver.NewSession(ctx, neo4j.SessionConfig{
		AccessMode:   neo4j.AccessModeWrite,
		DatabaseName: n.config.Neo4j.Database,
	})
	defer session.Close(ctx)

	// 将数据写入 Neo4j
	_, err := session.ExecuteWrite(context.Background(), func(tx neo4j.ManagedTransaction) (any, error) {
		// 插入节点
		for _, node := range result.Nodes {
			// 使用 MERGE 保证同名节点只创建一次
			stmt := "MERGE (n:`Entity` {name: $name, label: $name})"
			params := map[string]interface{}{"name": node.Label}
			if _, err := tx.Run(context.Background(), stmt, params); err != nil {
				return nil, err
			}
		}

		// 插入关系
		for _, edge := range result.Edges {
			// 匹配源和目标节点并创建关系
			stmt := fmt.Sprintf(
				"MATCH (a:`Entity` {name: $source, label: $source}), (b:`Entity` {name: $target, label: $target}) MERGE (a)-[:`%s`]->(b)", edge.Label,
			)
			params := map[string]interface{}{"source": edge.Source, "target": edge.Target}
			if _, err := tx.Run(context.Background(), stmt, params); err != nil {
				return nil, err
			}
		}

		return nil, nil
	})

	if err != nil {
		slog.Error("Transaction failed: ", "err", err)
	}

	return nil
}
