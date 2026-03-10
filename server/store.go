package main

import (
	"sync"
	"time"
)

// Room holds all state for a mesh session.
type Room struct {
	Code       string            `json:"code"`
	AnchorID   string            `json:"anchor_id,omitempty"`
	LeaderID   string            `json:"leader_id,omitempty"`
	Conditions NetworkConditions `json:"conditions"`
	CreatedAt  time.Time         `json:"created_at"`
	UpdatedAt  time.Time         `json:"updated_at"`
}

// NetworkConditions are the tunable simulation parameters.
type NetworkConditions struct {
	LatencyMs       int     `json:"latency_ms"`
	JitterMs        int     `json:"jitter_ms"`
	PacketLossPct   float64 `json:"packet_loss_pct"`
	BandwidthKbps   int     `json:"bandwidth_limit_kbps"`
	ConditionPreset string  `json:"condition_preset"`
}

// Presets for common network conditions.
var presets = map[string]NetworkConditions{
	"perfect":   {LatencyMs: 0, JitterMs: 0, PacketLossPct: 0, BandwidthKbps: 0, ConditionPreset: "perfect"},
	"wifi":      {LatencyMs: 5, JitterMs: 2, PacketLossPct: 0.5, BandwidthKbps: 0, ConditionPreset: "wifi"},
	"4g":        {LatencyMs: 50, JitterMs: 20, PacketLossPct: 1, BandwidthKbps: 5000, ConditionPreset: "4g"},
	"4g_poor":   {LatencyMs: 200, JitterMs: 80, PacketLossPct: 5, BandwidthKbps: 1000, ConditionPreset: "4g_poor"},
	"3g":        {LatencyMs: 300, JitterMs: 100, PacketLossPct: 8, BandwidthKbps: 384, ConditionPreset: "3g"},
	"satellite": {LatencyMs: 600, JitterMs: 50, PacketLossPct: 3, BandwidthKbps: 512, ConditionPreset: "satellite"},
	"congested": {LatencyMs: 500, JitterMs: 200, PacketLossPct: 15, BandwidthKbps: 128, ConditionPreset: "congested"},
}

// Store is the in-memory room store.
type Store struct {
	mu    sync.RWMutex
	rooms map[string]*Room
}

func NewStore() *Store {
	return &Store{rooms: make(map[string]*Room)}
}

func (s *Store) GetOrCreate(code string) *Room {
	s.mu.Lock()
	defer s.mu.Unlock()
	if r, ok := s.rooms[code]; ok {
		return r
	}
	r := &Room{
		Code:       code,
		Conditions: presets["perfect"],
		CreatedAt:  time.Now(),
		UpdatedAt:  time.Now(),
	}
	s.rooms[code] = r
	return r
}

func (s *Store) Get(code string) (*Room, bool) {
	s.mu.RLock()
	defer s.mu.RUnlock()
	r, ok := s.rooms[code]
	return r, ok
}

func (s *Store) Delete(code string) {
	s.mu.Lock()
	defer s.mu.Unlock()
	delete(s.rooms, code)
}

func (s *Store) ListAll() []*Room {
	s.mu.RLock()
	defer s.mu.RUnlock()
	rooms := make([]*Room, 0, len(s.rooms))
	for _, r := range s.rooms {
		rooms = append(rooms, r)
	}
	return rooms
}

// Cleanup expired rooms (older than ttl).
func (s *Store) Cleanup(ttl time.Duration) int {
	s.mu.Lock()
	defer s.mu.Unlock()
	cutoff := time.Now().Add(-ttl)
	count := 0
	for code, r := range s.rooms {
		if r.UpdatedAt.Before(cutoff) {
			delete(s.rooms, code)
			count++
		}
	}
	return count
}
