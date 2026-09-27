package conf

import (
	"fmt"
	"github.com/elastic/go-elasticsearch/v8"
	consul "github.com/hashicorp/consul/api"
	"github.com/spf13/viper"
	"os"
	"path/filepath"
)

type Config struct {
	Register struct {
		Consul      *consul.Config
		ServiceName string
		InstanceId  string
		Report      struct {
			Address        string
			Port           int
			HealthCheckUrl string
		}
	}
	Server struct {
		Listen   string
		Port     int
		BasePath string
	}
	Sqlite struct {
		DSN string
	}
	LLM struct {
		LocalBackend struct {
			Enable   bool
			Wait     bool
			RunExec  string
			ExecArgs []string
			Workdir  string
		}
		Api struct {
			BaseUrl         string
			SecretKey       string
			Model           string
			GenerateConfigs map[string]string
		}
	}
	Kafka struct {
		Brokers   []string
		Partition int
		MaxBytes  int
		GroupId   string
	}
	Elasticsearch struct {
		ClientConfig  *elasticsearch.Config
		MustAvailable bool
	}
	Neo4j struct {
		Uri      string
		Username string
		Password string
		Database string
	}
	Redis struct {
		Addr         string
		ClientName   string
		Username     string
		Password     string
		Db           int
		MaxRetries   int
		MinIdleConns int
		MaxIdleConns int
	}
	Processor struct {
		WorkerNum int
	}
	Extract struct {
		SystemPrompt          string
		PromptTemplate        string
		EnableThinking        bool
		ThinkingBudget        int
		ResultStorageLocation string
		ResultStorageType     string
		NamedEntity           struct {
			Scheme []string
		}
		Relation struct {
			Scheme []string
		}
		Event struct {
			Scheme []struct {
				EventType string
				Trigger   bool
				Arguments []string
			}
		}
	}
}

var Conf = &Config{}

func init() {
	ex, err := os.Executable()
	if err != nil {
		panic(err)
	}

	exPath := filepath.Dir(ex)

	viper.AddConfigPath(exPath)
	viper.AddConfigPath(".")
	viper.AddConfigPath("./conf")
	viper.AddConfigPath("./config")
	viper.AddConfigPath("/etc/easyspider-wrapper")
	viper.AddConfigPath("$HOME/.easyspider-wrapper")

	if err = viper.ReadInConfig(); err != nil {
		panic(err)
	}

	if err = viper.Unmarshal(Conf); err != nil {
		panic(err)
	}

	fmt.Printf("Configuration: %#v", Conf)
}
