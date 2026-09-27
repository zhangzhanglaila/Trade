package syncs

import (
	"sync"
)

// WaitGroup is identical to [sync.WaitGroup],
// but provides a Go method to start a goroutine.
type WaitGroup struct{ sync.WaitGroup }

// Go calls the given function in a new goroutine.
// It automatically increments the counter before execution and
// automatically decrements the counter after execution.
// It must not be called concurrently with Wait.
func (wg *WaitGroup) Go(f func()) {
	wg.Add(1)
	go func() {
		defer wg.Done()

		f()
	}()
}
