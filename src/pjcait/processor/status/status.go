package status

import (
	"cait-processor/conf"
	"cait-processor/model"
	"context"
	"database/sql"
	"errors"
	"github.com/jmoiron/sqlx"
	"github.com/yitter/idgenerator-go/idgen"
	"time"
)

type ProcessorStatusMgr struct {
	db *sqlx.DB
}

func NewProcessorStatusMgr(config *conf.Config) (*ProcessorStatusMgr, error) {
	db, err := sqlx.Connect("sqlite3", config.Sqlite.DSN)
	if err != nil {
		return nil, err
	}

	mgr := &ProcessorStatusMgr{
		db: db,
	}

	return mgr, nil
}

const (
	statusExistsSql = `SELECT count(1) FROM status WHERE task_id = ? and content_key = ? and part = ?`
	statusInsertSql = `		
		INSERT INTO status (id, task_id, content_key, storage_type, part, create_time, update_time, status) 
		VALUES (:id, :task_id, :content_key, :storage_type, :part, :create_time, :update_time, :status)`
	statusUpdateSql = `		
		UPDATE status SET "update_time" = ?, "status" = ? 
		              WHERE "task_id" = ? and "content_key" = ? and part = ?`
	statusSql = `SELECT * from status where task_id = ?`

	taskStatusExistsSql = `Select count(1) from task_status where task_id = ?`
	taskStatusInsertSql = `
		INSERT INTO task_status (id, task_id, status, message, create_time, update_time) 
		VALUES (:id, :task_id, :status, :message, :create_time, :update_time)`
	taskStatusListSql   = `Select * from task_status limit ? offset ?`
	taskStatusUpdateSql = `UPDATE task_status SET "status" = ?, message = ?, "update_time" = ? WHERE task_id = ?`
)

func (m *ProcessorStatusMgr) SetTaskStatus(ctx context.Context, taskId string, statusVal int, msg string) error {
	result, err := m.db.QueryContext(ctx, taskStatusExistsSql, taskId)
	if err != nil {
		return err
	}

	if !result.Next() {
		return m.setTaskStatus(ctx, taskId, statusVal, msg)
	} else {
		return m.updateTaskStatus(ctx, taskId, statusVal, msg)
	}
}

func (m *ProcessorStatusMgr) SetStatus(ctx context.Context, taskId, contentKey string, part, statusVal int) error {
	result, err := m.db.Query(statusExistsSql, taskId, contentKey, part)
	if err != nil {
		return err
	}

	if !result.Next() {
		return m.setStatus(ctx, taskId, contentKey, part, statusVal)
	} else {
		return m.updateStatus(ctx, taskId, contentKey, part, statusVal)
	}
}

func (m *ProcessorStatusMgr) setTaskStatus(ctx context.Context, taskId string, statusVal int, msg string) error {
	status := &model.TaskStatus{
		ID:         idgen.NextId(),
		TaskId:     taskId,
		Status:     statusVal,
		Message:    msg,
		CreateTime: time.Now(),
		UpdateTime: time.Now(),
	}

	_, err := m.db.NamedExecContext(ctx, taskStatusInsertSql, status)
	return err
}

func (m *ProcessorStatusMgr) updateTaskStatus(ctx context.Context, taskId string, statusVal int, msg string) error {
	_, err := m.db.ExecContext(ctx, taskStatusUpdateSql, statusVal, msg, time.Now(), taskId)
	return err
}

func (m *ProcessorStatusMgr) setStatus(ctx context.Context, taskId, contentKey string, part, statusVal int) error {
	status := &model.Status{
		ID:         idgen.NextId(),
		TaskId:     taskId,
		ContentKey: contentKey,
		Part:       part,
		Status:     statusVal,
		CreateTime: time.Time{},
		UpdateTime: time.Time{},
	}

	_, err := m.db.NamedExecContext(ctx, statusInsertSql, status)
	return err
}

func (m *ProcessorStatusMgr) updateStatus(ctx context.Context, taskId, contentKey string, part, statusVal int) error {
	_, err := m.db.ExecContext(ctx, statusUpdateSql, time.Now(), statusVal, taskId, contentKey, part)
	return err
}

func getPageOffset(page, pageSize int) int {
	return (page - 1) * pageSize
}

func (m *ProcessorStatusMgr) GetTaskList(ctx context.Context, page, pageSize int) ([]string, error) {
	offset := getPageOffset(page, pageSize)
	results := make([]string, 0)
	err := m.db.QueryRowContext(ctx, taskStatusListSql, pageSize, offset).Scan(&results)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return results, nil
		} else {
			return nil, err
		}
	}

	return results, nil
}

func (m *ProcessorStatusMgr) GetStatusList(ctx context.Context, taskId string) ([]*model.Status, error) {
	results := make([]*model.Status, 0)
	err := m.db.QueryRowContext(ctx, statusSql, taskId).Scan(&results)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return results, nil
		} else {
			return nil, err
		}
	}

	return results, nil
}
