package room

import (
	"fmt"
	"sync"
	"time"
)

var sgt = time.FixedZone("SGT", 8*60*60)

// PeerSnapshot is a device's view of the session, uploaded periodically.
type PeerSnapshot struct {
	PeerId    string `json:"peer_id"`
	Data      any    `json:"data"` // opaque JSON from the client's MeshStateSnapshot
	UpdatedAt string `json:"updated_at"`
}

// Room holds all state for a mesh session.
type Room struct {
	Code       string                   `json:"code"`
	AnchorID   string                   `json:"anchor_id,omitempty"`
	LeaderID   string                   `json:"leader_id,omitempty"`
	Conditions NetworkConditions        `json:"conditions"`
	Snapshots  map[string]*PeerSnapshot `json:"snapshots,omitempty"`
	CreatedAt  time.Time                `json:"created_at"`
	UpdatedAt  time.Time                `json:"updated_at"`
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
var Presets = map[string]NetworkConditions{
	"perfect":   {LatencyMs: 0, JitterMs: 0, PacketLossPct: 0, BandwidthKbps: 0, ConditionPreset: "perfect"},
	"wifi":      {LatencyMs: 5, JitterMs: 2, PacketLossPct: 0.5, BandwidthKbps: 0, ConditionPreset: "wifi"},
	"4g":        {LatencyMs: 50, JitterMs: 20, PacketLossPct: 1, BandwidthKbps: 5000, ConditionPreset: "4g"},
	"4g_poor":   {LatencyMs: 200, JitterMs: 80, PacketLossPct: 5, BandwidthKbps: 1000, ConditionPreset: "4g_poor"},
	"3g":        {LatencyMs: 300, JitterMs: 100, PacketLossPct: 8, BandwidthKbps: 384, ConditionPreset: "3g"},
	"satellite": {LatencyMs: 600, JitterMs: 50, PacketLossPct: 3, BandwidthKbps: 512, ConditionPreset: "satellite"},
	"congested": {LatencyMs: 500, JitterMs: 200, PacketLossPct: 15, BandwidthKbps: 128, ConditionPreset: "congested"},
}

const maxRooms = 500

func (r *Room) deepCopy() Room {
	cp := *r
	if r.Snapshots != nil {
		cp.Snapshots = make(map[string]*PeerSnapshot, len(r.Snapshots))
		for k, v := range r.Snapshots {
			snap := *v
			cp.Snapshots[k] = &snap
		}
	}
	return cp
}

// Store is the in-memory room store.
type Store struct {
	mu    sync.RWMutex
	rooms map[string]*Room
}

func NewStore() *Store {
	return &Store{rooms: make(map[string]*Room)}
}

func (s *Store) GetOrCreate(code string) (Room, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if r, ok := s.rooms[code]; ok {
		return r.deepCopy(), nil
	}
	if len(s.rooms) >= maxRooms {
		return Room{}, fmt.Errorf("room limit reached (%d)", maxRooms)
	}
	r := &Room{
		Code:       code,
		Conditions: Presets["perfect"],
		CreatedAt:  time.Now(),
		UpdatedAt:  time.Now(),
	}
	s.rooms[code] = r
	return r.deepCopy(), nil
}

func (s *Store) Get(code string) (Room, bool) {
	s.mu.RLock()
	defer s.mu.RUnlock()
	if r, ok := s.rooms[code]; ok {
		return r.deepCopy(), true
	}
	return Room{}, false
}

func (s *Store) Delete(code string) bool {
	s.mu.Lock()
	defer s.mu.Unlock()
	if _, ok := s.rooms[code]; !ok {
		return false
	}
	delete(s.rooms, code)
	return true
}

func (s *Store) ListAll() []Room {
	s.mu.RLock()
	defer s.mu.RUnlock()
	out := make([]Room, 0, len(s.rooms))
	for _, r := range s.rooms {
		out = append(out, r.deepCopy())
	}
	return out
}

func (s *Store) SetAnchor(code, anchorID string) (Room, bool) {
	s.mu.Lock()
	defer s.mu.Unlock()
	r, ok := s.rooms[code]
	if !ok {
		return Room{}, false
	}
	r.AnchorID = anchorID
	r.UpdatedAt = time.Now()
	return r.deepCopy(), true
}

func (s *Store) SetLeader(code, leaderID string) (Room, bool) {
	s.mu.Lock()
	defer s.mu.Unlock()
	r, ok := s.rooms[code]
	if !ok {
		return Room{}, false
	}
	r.LeaderID = leaderID
	r.UpdatedAt = time.Now()
	return r.deepCopy(), true
}

func (s *Store) SetConditions(code string, cond NetworkConditions) (Room, bool) {
	s.mu.Lock()
	defer s.mu.Unlock()
	r, ok := s.rooms[code]
	if !ok {
		return Room{}, false
	}
	r.Conditions = cond
	r.UpdatedAt = time.Now()
	return r.deepCopy(), true
}

func (s *Store) SetSnapshot(code, peerId string, data any) (Room, bool) {
	s.mu.Lock()
	defer s.mu.Unlock()
	r, ok := s.rooms[code]
	if !ok {
		return Room{}, false
	}
	if r.Snapshots == nil {
		r.Snapshots = make(map[string]*PeerSnapshot)
	}
	r.Snapshots[peerId] = &PeerSnapshot{
		PeerId:    peerId,
		Data:      data,
		UpdatedAt: time.Now().In(sgt).Format("2006-01-02T15:04:05+08:00"),
	}
	r.UpdatedAt = time.Now()
	return r.deepCopy(), true
}

func (s *Store) GetSnapshots(code string) ([]PeerSnapshot, bool) {
	s.mu.RLock()
	defer s.mu.RUnlock()
	r, ok := s.rooms[code]
	if !ok {
		return nil, false
	}
	out := make([]PeerSnapshot, 0, len(r.Snapshots))
	for _, snap := range r.Snapshots {
		out = append(out, *snap)
	}
	return out, true
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
