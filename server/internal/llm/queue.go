package llm

import (
	"context"
	"errors"
	"sync/atomic"
	"time"

	"golang.org/x/sync/singleflight"
)

// ErrQueueFull is returned when the LLM queue has no room for more requests.
var ErrQueueFull = errors.New("llm queue full")

// LLMQueue serialises access to a local LLM with bounded concurrency,
// request coalescing (singleflight), and backpressure.
type LLMQueue struct {
	cfg     *Config
	sem     chan struct{} // buffered to maxActive
	sf      singleflight.Group
	maxWait int32
	waiting atomic.Int32 // total admitted calls (active + queued)
	timeout time.Duration
}

// NewLLMQueue creates a queue that allows maxActive concurrent LLM calls
// and up to maxWait additional requests waiting in line.
// Requests beyond maxActive+maxWait are rejected immediately with ErrQueueFull.
func NewQueue(cfg *Config, maxActive, maxWait int, timeout time.Duration) *LLMQueue {
	return &LLMQueue{
		cfg:     cfg,
		sem:     make(chan struct{}, maxActive),
		maxWait: int32(maxWait),
		timeout: timeout,
	}
}

// Call enqueues an LLM request. Identical concurrent requests (same sfKey)
// are coalesced via singleflight so the LLM is only called once.
func (q *LLMQueue) Call(ctx context.Context, sfKey string, messages []ChatMessage, maxTokens int, caller string) (string, error) {
	// Back-pressure: reject immediately if active slots plus queue are full.
	if q.waiting.Add(1) > int32(cap(q.sem))+q.maxWait {
		q.waiting.Add(-1)
		return "", ErrQueueFull
	}
	defer q.waiting.Add(-1)

	// Singleflight deduplicates concurrent calls with the same key.
	ch := q.sf.DoChan(sfKey, func() (any, error) {
		// Acquire semaphore slot (bounded concurrency).
		select {
		case q.sem <- struct{}{}:
			// got slot
		case <-ctx.Done():
			return nil, ctx.Err()
		}
		defer func() { <-q.sem }()

		// Apply per-request timeout.
		ctx2, cancel := context.WithTimeout(ctx, q.timeout)
		defer cancel()

		content, err := callLLM(ctx2, q.cfg, messages, maxTokens, caller)
		return content, err
	})

	// Wait for result or context cancellation.
	select {
	case res := <-ch:
		if res.Err != nil {
			return "", res.Err
		}
		return res.Val.(string), nil
	case <-ctx.Done():
		return "", ctx.Err()
	}
}

// Stats returns the current active and waiting counts (for logging/testing).
func (q *LLMQueue) Stats() (active int, waiting int32) {
	active = len(q.sem)
	waiting = q.waiting.Load() - int32(active)
	if waiting < 0 {
		waiting = 0
	}
	return active, waiting
}
