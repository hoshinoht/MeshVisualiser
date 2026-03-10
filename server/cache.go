package main

import (
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"sync"
	"time"
)

type summaryEntry struct {
	response SummaryResponse
	expiry   time.Time
}

// SummaryCache is an in-memory cache for LLM-generated session summaries.
type SummaryCache struct {
	mu      sync.RWMutex
	entries map[string]summaryEntry
	ttl     time.Duration
}

func NewSummaryCache(ttl time.Duration) *SummaryCache {
	c := &SummaryCache{entries: make(map[string]summaryEntry), ttl: ttl}
	go func() {
		for range time.Tick(10 * time.Minute) {
			c.evict()
		}
	}()
	return c
}

func (c *SummaryCache) key(req SummaryRequest) string {
	h := sha256.New()
	h.Write([]byte(req.MeshState))
	if req.QuizScore != nil {
		fmt.Fprintf(h, "|%d/%d", *req.QuizScore, *req.QuizTotal)
	}
	return hex.EncodeToString(h.Sum(nil))
}

func (c *SummaryCache) Get(req SummaryRequest) (SummaryResponse, bool) {
	c.mu.RLock()
	defer c.mu.RUnlock()
	e, ok := c.entries[c.key(req)]
	if !ok || time.Now().After(e.expiry) {
		return SummaryResponse{}, false
	}
	return e.response, true
}

func (c *SummaryCache) Set(req SummaryRequest, resp SummaryResponse) {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.entries[c.key(req)] = summaryEntry{response: resp, expiry: time.Now().Add(c.ttl)}
}

func (c *SummaryCache) evict() {
	c.mu.Lock()
	defer c.mu.Unlock()
	now := time.Now()
	for k, e := range c.entries {
		if now.After(e.expiry) {
			delete(c.entries, k)
		}
	}
}
