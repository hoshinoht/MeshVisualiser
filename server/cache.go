package main

import (
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"sync"
	"time"
)

// ── Generic TTL cache ──

type cacheEntry[T any] struct {
	value  T
	expiry time.Time
}

// TTLCache is a generic in-memory cache with per-entry expiry.
type TTLCache[T any] struct {
	mu      sync.RWMutex
	entries map[string]cacheEntry[T]
	ttl     time.Duration
}

func NewTTLCache[T any](ttl time.Duration) *TTLCache[T] {
	c := &TTLCache[T]{entries: make(map[string]cacheEntry[T]), ttl: ttl}
	go func() {
		for range time.Tick(10 * time.Minute) {
			c.evict()
		}
	}()
	return c
}

func (c *TTLCache[T]) Get(key string) (T, bool) {
	c.mu.RLock()
	defer c.mu.RUnlock()
	e, ok := c.entries[key]
	if !ok || time.Now().After(e.expiry) {
		var zero T
		return zero, false
	}
	return e.value, true
}

func (c *TTLCache[T]) Set(key string, value T) {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.entries[key] = cacheEntry[T]{value: value, expiry: time.Now().Add(c.ttl)}
}

func (c *TTLCache[T]) evict() {
	c.mu.Lock()
	defer c.mu.Unlock()
	now := time.Now()
	for k, e := range c.entries {
		if now.After(e.expiry) {
			delete(c.entries, k)
		}
	}
}

func (c *TTLCache[T]) Len() int {
	c.mu.RLock()
	defer c.mu.RUnlock()
	return len(c.entries)
}

// ── Key helpers ──

func hashKey(parts ...string) string {
	h := sha256.New()
	for _, p := range parts {
		h.Write([]byte(p))
		h.Write([]byte("|"))
	}
	return hex.EncodeToString(h.Sum(nil))
}

// ── Typed cache constructors ──

// SummaryCache caches LLM-generated session summaries.
type SummaryCache = TTLCache[SummaryResponse]

func NewSummaryCache(ttl time.Duration) *SummaryCache {
	return NewTTLCache[SummaryResponse](ttl)
}

func SummaryCacheKey(req SummaryRequest) string {
	extra := ""
	if req.QuizScore != nil && req.QuizTotal != nil {
		extra = fmt.Sprintf("%d/%d", *req.QuizScore, *req.QuizTotal)
	}
	return hashKey(req.MeshState, extra)
}

// QuizCache caches LLM-generated quiz responses.
type QuizCache = TTLCache[QuizResponse]

func NewQuizCache(ttl time.Duration) *QuizCache {
	return NewTTLCache[QuizResponse](ttl)
}

func QuizCacheKey(meshState string) string {
	return hashKey(meshState)
}
