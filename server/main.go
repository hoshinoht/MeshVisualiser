package main

import (
	"encoding/json"
	"log"
	"net/http"
	"strings"
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

func main() {
	store := NewStore()

	// Expire rooms after 1 hour of inactivity.
	go func() {
		for range time.Tick(10 * time.Minute) {
			if n := store.Cleanup(1 * time.Hour); n > 0 {
				log.Printf("Cleaned up %d expired rooms", n)
			}
		}
	}()

	mux := http.NewServeMux()

	// Health check
	mux.HandleFunc("GET /health", func(w http.ResponseWriter, r *http.Request) {
		writeJSON(w, http.StatusOK, map[string]string{"status": "ok"})
	})

	// List presets
	mux.HandleFunc("GET /presets", func(w http.ResponseWriter, r *http.Request) {
		writeJSON(w, http.StatusOK, presets)
	})

	// List all rooms
	mux.HandleFunc("GET /rooms", func(w http.ResponseWriter, r *http.Request) {
		writeJSON(w, http.StatusOK, store.ListAll())
	})

	// Get full room state (anchor + leader + conditions) — used by late joiners
	mux.HandleFunc("GET /rooms/{code}", func(w http.ResponseWriter, r *http.Request) {
		code := r.PathValue("code")
		room, ok := store.Get(code)
		if !ok {
			writeJSON(w, http.StatusNotFound, map[string]string{"error": "room not found"})
			return
		}
		writeJSON(w, http.StatusOK, room)
	})

	// Delete a room
	mux.HandleFunc("DELETE /rooms/{code}", func(w http.ResponseWriter, r *http.Request) {
		code := r.PathValue("code")
		store.Delete(code)
		writeJSON(w, http.StatusOK, map[string]string{"status": "deleted"})
	})

	// --- Anchor ---

	mux.HandleFunc("GET /rooms/{code}/anchor", func(w http.ResponseWriter, r *http.Request) {
		code := r.PathValue("code")
		room, ok := store.Get(code)
		if !ok {
			writeJSON(w, http.StatusNotFound, map[string]string{"error": "room not found"})
			return
		}
		writeJSON(w, http.StatusOK, map[string]string{"anchor_id": room.AnchorID})
	})

	mux.HandleFunc("PUT /rooms/{code}/anchor", func(w http.ResponseWriter, r *http.Request) {
		code := r.PathValue("code")
		var body struct {
			AnchorID string `json:"anchor_id"`
		}
		if err := json.NewDecoder(r.Body).Decode(&body); err != nil || body.AnchorID == "" {
			writeJSON(w, http.StatusBadRequest, map[string]string{"error": "anchor_id required"})
			return
		}
		room := store.GetOrCreate(code)
		store.mu.Lock()
		room.AnchorID = body.AnchorID
		room.UpdatedAt = time.Now()
		store.mu.Unlock()
		writeJSON(w, http.StatusOK, map[string]string{"anchor_id": room.AnchorID})
	})

	// --- Leader ---

	mux.HandleFunc("GET /rooms/{code}/leader", func(w http.ResponseWriter, r *http.Request) {
		code := r.PathValue("code")
		room, ok := store.Get(code)
		if !ok {
			writeJSON(w, http.StatusNotFound, map[string]string{"error": "room not found"})
			return
		}
		writeJSON(w, http.StatusOK, map[string]string{"leader_id": room.LeaderID})
	})

	mux.HandleFunc("PUT /rooms/{code}/leader", func(w http.ResponseWriter, r *http.Request) {
		code := r.PathValue("code")
		var body struct {
			LeaderID string `json:"leader_id"`
		}
		if err := json.NewDecoder(r.Body).Decode(&body); err != nil || body.LeaderID == "" {
			writeJSON(w, http.StatusBadRequest, map[string]string{"error": "leader_id required"})
			return
		}
		room := store.GetOrCreate(code)
		store.mu.Lock()
		room.LeaderID = body.LeaderID
		room.UpdatedAt = time.Now()
		store.mu.Unlock()
		writeJSON(w, http.StatusOK, map[string]string{"leader_id": room.LeaderID})
	})

	// --- Network Conditions ---

	mux.HandleFunc("GET /rooms/{code}/conditions", func(w http.ResponseWriter, r *http.Request) {
		code := r.PathValue("code")
		room, ok := store.Get(code)
		if !ok {
			writeJSON(w, http.StatusNotFound, map[string]string{"error": "room not found"})
			return
		}
		writeJSON(w, http.StatusOK, room.Conditions)
	})

	mux.HandleFunc("PUT /rooms/{code}/conditions", func(w http.ResponseWriter, r *http.Request) {
		code := r.PathValue("code")
		var cond NetworkConditions
		if err := json.NewDecoder(r.Body).Decode(&cond); err != nil {
			writeJSON(w, http.StatusBadRequest, map[string]string{"error": "invalid JSON"})
			return
		}
		// If a preset name is given, use its values as base.
		if cond.ConditionPreset != "" {
			if p, ok := presets[strings.ToLower(cond.ConditionPreset)]; ok {
				cond = p
			}
		}
		// Clamp values.
		if cond.PacketLossPct < 0 {
			cond.PacketLossPct = 0
		}
		if cond.PacketLossPct > 100 {
			cond.PacketLossPct = 100
		}
		if cond.LatencyMs < 0 {
			cond.LatencyMs = 0
		}
		if cond.JitterMs < 0 {
			cond.JitterMs = 0
		}
		room := store.GetOrCreate(code)
		store.mu.Lock()
		room.Conditions = cond
		room.UpdatedAt = time.Now()
		store.mu.Unlock()
		writeJSON(w, http.StatusOK, room.Conditions)
	})

	// CORS + logging middleware
	handler := corsMiddleware(logMiddleware(mux))

	addr := ":8080"
	log.Printf("Mesh server starting on %s", addr)
	log.Fatal(http.ListenAndServe(addr, handler))
}

func writeJSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	json.NewEncoder(w).Encode(v)
}

func corsMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Access-Control-Allow-Origin", "*")
		w.Header().Set("Access-Control-Allow-Methods", "GET, PUT, DELETE, OPTIONS")
		w.Header().Set("Access-Control-Allow-Headers", "Content-Type")
		if r.Method == http.MethodOptions {
			w.WriteHeader(http.StatusNoContent)
			return
		}
		next.ServeHTTP(w, r)
	})
}

func logMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		start := time.Now()
		next.ServeHTTP(w, r)
		log.Printf("%s %s %s", r.Method, r.URL.Path, time.Since(start).Round(time.Microsecond))
	})
}
