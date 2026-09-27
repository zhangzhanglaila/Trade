package model

type NewsContent struct {
	Id         string `json:"id"`
	ContentId  string `json:"contentId"`
	TaskId     string `json:"taskId"`
	Title      string `json:"title"`
	Content    string `json:"content"`
	CreateTime uint64 `json:"createTime"`
}
