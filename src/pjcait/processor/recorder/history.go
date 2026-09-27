package recorder

import (
	"cait-processor/conf"
	"cait-processor/model"
	"context"
	"github.com/jmoiron/sqlx"
	"github.com/yitter/idgenerator-go/idgen"
	"time"
)

type History struct {
	db *sqlx.DB
}

const (
	historyInsertSql = `
		INSERT INTO history (id, task_id, part, content, result, reason_result, create_time, status)
    VALUES (:id, :task_id, :part, :content, :result, :reason_result, :create_time, :status)
	`

	historySelectByTaskIdSql = `SELECT * from history where task_id = ? ORDER BY create_time DESC`

	historyListSql = `SELECT * from history where part = 0 ORDER BY create_time DESC`
)

func NewHistory(config *conf.Config) (*History, error) {
	db, err := sqlx.Connect("sqlite3", config.Sqlite.DSN)
	if err != nil {
		return nil, err
	}

	return &History{db: db}, nil
}

func (h *History) Record(ctx context.Context, taskId string, part int, content, result, reasonResult string) (int64, error) {
	id := idgen.NextId()
	history := &model.History{
		ID:           id,
		TaskID:       taskId,
		Part:         part,
		Content:      content,
		Result:       result,
		ReasonResult: reasonResult,
		CreateTime:   time.Now(),
		Status:       0,
	}

	execResult, err := h.db.NamedExecContext(ctx, historyInsertSql, history)
	if err != nil {
		return 0, err
	}

	return execResult.RowsAffected()
}

func (h *History) RecordHistory(ctx context.Context, history *model.History) (int64, error) {
	execResult, err := h.db.NamedExecContext(ctx, historyInsertSql, history)
	if err != nil {
		return 0, err
	}

	return execResult.RowsAffected()
}

func (h *History) GetTaskHistory(ctx context.Context, taskId string) ([]model.History, error) {
	histories := make([]model.History, 0)
	err := h.db.SelectContext(ctx, &histories, historySelectByTaskIdSql, taskId)
	if err != nil {
		return []model.History{}, err
	}

	return histories, nil
}

func (h *History) ListHistory(ctx context.Context) ([]model.History, error) {
	histories := make([]model.History, 0)
	err := h.db.SelectContext(ctx, &histories, historyListSql)
	if err != nil {
		return []model.History{}, err
	}

	return histories, nil
}
