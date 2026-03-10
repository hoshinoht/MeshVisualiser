package main

import (
	"encoding/json"
	"net/http"
	"strings"
	"time"
)

func registerRoomRoutes(mux *http.ServeMux, store *Store) {
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

	// Get full room state
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
		if cond.ConditionPreset != "" {
			if p, ok := presets[strings.ToLower(cond.ConditionPreset)]; ok {
				cond = p
			}
		}
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
}
