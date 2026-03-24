package main

import (
	"encoding/json"
	"net/http"
	"strings"
	"unicode"
)

func isValidRoomCode(code string) bool {
	if len(code) == 0 || len(code) > 32 {
		return false
	}
	for _, c := range code {
		if !unicode.IsLetter(c) && !unicode.IsDigit(c) && c != '-' && c != '_' {
			return false
		}
	}
	return true
}

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
		if !isValidRoomCode(code) {
			writeJSON(w, http.StatusBadRequest, map[string]string{"error": "invalid room code"})
			return
		}
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
		if !isValidRoomCode(code) {
			writeJSON(w, http.StatusBadRequest, map[string]string{"error": "invalid room code"})
			return
		}
		if !store.Delete(code) {
			writeJSON(w, http.StatusNotFound, map[string]string{"error": "room not found"})
			return
		}
		writeJSON(w, http.StatusOK, map[string]string{"status": "deleted"})
	})

	// --- Anchor ---

	mux.HandleFunc("GET /rooms/{code}/anchor", func(w http.ResponseWriter, r *http.Request) {
		code := r.PathValue("code")
		if !isValidRoomCode(code) {
			writeJSON(w, http.StatusBadRequest, map[string]string{"error": "invalid room code"})
			return
		}
		room, ok := store.Get(code)
		if !ok {
			writeJSON(w, http.StatusNotFound, map[string]string{"error": "room not found"})
			return
		}
		writeJSON(w, http.StatusOK, map[string]string{"anchor_id": room.AnchorID})
	})

	mux.HandleFunc("PUT /rooms/{code}/anchor", func(w http.ResponseWriter, r *http.Request) {
		code := r.PathValue("code")
		if !isValidRoomCode(code) {
			writeJSON(w, http.StatusBadRequest, map[string]string{"error": "invalid room code"})
			return
		}
		r.Body = http.MaxBytesReader(w, r.Body, 512*1024)
		var body struct {
			AnchorID string `json:"anchor_id"`
		}
		if err := json.NewDecoder(r.Body).Decode(&body); err != nil || body.AnchorID == "" {
			writeJSON(w, http.StatusBadRequest, map[string]string{"error": "anchor_id required"})
			return
		}
		// Ensure room exists (create if needed)
		if _, err := store.GetOrCreate(code); err != nil {
			writeJSON(w, http.StatusServiceUnavailable, map[string]string{"error": err.Error()})
			return
		}
		room, ok := store.SetAnchor(code, body.AnchorID)
		if !ok {
			writeJSON(w, http.StatusNotFound, map[string]string{"error": "room not found"})
			return
		}
		writeJSON(w, http.StatusOK, map[string]string{"anchor_id": room.AnchorID})
	})

	// --- Leader ---

	mux.HandleFunc("GET /rooms/{code}/leader", func(w http.ResponseWriter, r *http.Request) {
		code := r.PathValue("code")
		if !isValidRoomCode(code) {
			writeJSON(w, http.StatusBadRequest, map[string]string{"error": "invalid room code"})
			return
		}
		room, ok := store.Get(code)
		if !ok {
			writeJSON(w, http.StatusNotFound, map[string]string{"error": "room not found"})
			return
		}
		writeJSON(w, http.StatusOK, map[string]string{"leader_id": room.LeaderID})
	})

	mux.HandleFunc("PUT /rooms/{code}/leader", func(w http.ResponseWriter, r *http.Request) {
		code := r.PathValue("code")
		if !isValidRoomCode(code) {
			writeJSON(w, http.StatusBadRequest, map[string]string{"error": "invalid room code"})
			return
		}
		r.Body = http.MaxBytesReader(w, r.Body, 512*1024)
		var body struct {
			LeaderID string `json:"leader_id"`
		}
		if err := json.NewDecoder(r.Body).Decode(&body); err != nil || body.LeaderID == "" {
			writeJSON(w, http.StatusBadRequest, map[string]string{"error": "leader_id required"})
			return
		}
		// Ensure room exists (create if needed)
		if _, err := store.GetOrCreate(code); err != nil {
			writeJSON(w, http.StatusServiceUnavailable, map[string]string{"error": err.Error()})
			return
		}
		room, ok := store.SetLeader(code, body.LeaderID)
		if !ok {
			writeJSON(w, http.StatusNotFound, map[string]string{"error": "room not found"})
			return
		}
		writeJSON(w, http.StatusOK, map[string]string{"leader_id": room.LeaderID})
	})

	// --- Network Conditions ---

	mux.HandleFunc("GET /rooms/{code}/conditions", func(w http.ResponseWriter, r *http.Request) {
		code := r.PathValue("code")
		if !isValidRoomCode(code) {
			writeJSON(w, http.StatusBadRequest, map[string]string{"error": "invalid room code"})
			return
		}
		room, ok := store.Get(code)
		if !ok {
			writeJSON(w, http.StatusNotFound, map[string]string{"error": "room not found"})
			return
		}
		writeJSON(w, http.StatusOK, room.Conditions)
	})

	mux.HandleFunc("PUT /rooms/{code}/conditions", func(w http.ResponseWriter, r *http.Request) {
		code := r.PathValue("code")
		if !isValidRoomCode(code) {
			writeJSON(w, http.StatusBadRequest, map[string]string{"error": "invalid room code"})
			return
		}
		r.Body = http.MaxBytesReader(w, r.Body, 512*1024)
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
		if cond.BandwidthKbps < 0 {
			cond.BandwidthKbps = 0
		}
		// Ensure room exists (create if needed)
		if _, err := store.GetOrCreate(code); err != nil {
			writeJSON(w, http.StatusServiceUnavailable, map[string]string{"error": err.Error()})
			return
		}
		room, ok := store.SetConditions(code, cond)
		if !ok {
			writeJSON(w, http.StatusNotFound, map[string]string{"error": "room not found"})
			return
		}
		writeJSON(w, http.StatusOK, room.Conditions)
	})
}
