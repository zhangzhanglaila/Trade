package model

import "time"

type History struct {
	ID           int64     `db:"id" json:"id"`
	TaskID       string    `db:"task_id" json:"task_id"`
	Part         int       `db:"part" json:"part"`
	Content      string    `db:"content" json:"content"`
	Result       string    `db:"result" json:"result"`
	ReasonResult string    `db:"reason_result" json:"reason_result"`
	CreateTime   time.Time `db:"create_time" json:"create_time"`
	Status       int       `db:"status" json:"status"`
}

const (
	StatusReady = iota
	StatusProcessing
	StatusDone
	StatusFailed
)

type Status struct {
	ID          int64     `db:"id"`
	TaskId      string    `db:"task_id"`
	ContentKey  string    `db:"content_key"`
	StorageType int       `db:"storage_type"`
	Part        int       `db:"part"`
	CreateTime  time.Time `db:"create_time"`
	UpdateTime  time.Time `db:"update_time"`
	Status      int       `db:"status"`
}

const (
	TaskStatusReceived = iota
	TaskStatusProcessing
	TaskStatusEnd
)

type TaskStatus struct {
	ID         int64     `db:"id"`
	TaskId     string    `db:"task_id"`
	Status     int       `db:"status"`
	Message    string    `db:"message"`
	CreateTime time.Time `db:"create_time"`
	UpdateTime time.Time `db:"update_time"`
}
