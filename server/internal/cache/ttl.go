package cache

import (
	"crypto/sha256"
	"encoding/hex"
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

func NewTTLCache[T any](ttl time.Duration, stop <-chan struct{}) *TTLCache[T] {
	c := &TTLCache[T]{entries: make(map[string]cacheEntry[T]), ttl: ttl}
	go func() {
		ticker := time.NewTicker(10 * time.Minute)
		defer ticker.Stop()
		for {
			select {
			case <-ticker.C:
				c.evict()
			case <-stop:
				return
			}
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

func HashKey(parts ...string) string {
	h := sha256.New()
	for _, p := range parts {
		h.Write([]byte(p))
		h.Write([]byte("|"))
	}
	return hex.EncodeToString(h.Sum(nil))
}
