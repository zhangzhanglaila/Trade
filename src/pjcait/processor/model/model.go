package model

type LLMResult struct {
	Nodes []Node `json:"nodes"`
	Edges []Edge `json:"edges"`
}

type Node struct {
	Label string `json:"label"`
}

type Edge struct {
	Label  string `json:"label"`
	Source string `json:"source"`
	Target string `json:"target"`
}
