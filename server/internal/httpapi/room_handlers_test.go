package httpapi

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/inf2007/inf2007-team07-2026/server/internal/room"
)

func testRoomMux() *http.ServeMux {
	store := room.NewStore()
	mux := http.NewServeMux()
	RegisterRoomRoutes(mux, store)
	return mux
}

func TestHealthCheck(t *testing.T) {
	req := httptest.NewRequest(http.MethodGet, "/health", nil)
	rec := httptest.NewRecorder()

	testRoomMux().ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d", rec.Code, http.StatusOK)
	}
	if !bytes.Contains(rec.Body.Bytes(), []byte(`"status":"ok"`)) {
		t.Fatalf("body = %q, want to contain %q", rec.Body.String(), `"status":"ok"`)
	}
}

func TestGetPresets(t *testing.T) {
	req := httptest.NewRequest(http.MethodGet, "/presets", nil)
	rec := httptest.NewRecorder()

	testRoomMux().ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d", rec.Code, http.StatusOK)
	}
	var got map[string]room.NetworkConditions
	if err := json.NewDecoder(rec.Body).Decode(&got); err != nil {
		t.Fatalf("failed to decode presets: %v", err)
	}
	if len(got) != 7 {
		t.Fatalf("len(presets) = %d, want 7", len(got))
	}
	if got["wifi"].LatencyMs != 5 {
		t.Fatalf("wifi latency = %d, want 5", got["wifi"].LatencyMs)
	}
}

func TestRoomLifecycle(t *testing.T) {
	store := room.NewStore()
	mux := http.NewServeMux()
	RegisterRoomRoutes(mux, store)

	anchorBody := bytes.NewBufferString(`{"anchor_id":"anc1"}`)
	putReq := httptest.NewRequest(http.MethodPut, "/rooms/test1/anchor", anchorBody)
	putRec := httptest.NewRecorder()
	mux.ServeHTTP(putRec, putReq)
	if putRec.Code != http.StatusOK {
		t.Fatalf("PUT anchor status = %d, want %d", putRec.Code, http.StatusOK)
	}

	getReq := httptest.NewRequest(http.MethodGet, "/rooms/test1", nil)
	getRec := httptest.NewRecorder()
	mux.ServeHTTP(getRec, getReq)
	if getRec.Code != http.StatusOK {
		t.Fatalf("GET room status = %d, want %d", getRec.Code, http.StatusOK)
	}
	if !bytes.Contains(getRec.Body.Bytes(), []byte(`"anchor_id":"anc1"`)) {
		t.Fatalf("GET room body = %q, want anchor_id anc1", getRec.Body.String())
	}

	deleteReq := httptest.NewRequest(http.MethodDelete, "/rooms/test1", nil)
	deleteRec := httptest.NewRecorder()
	mux.ServeHTTP(deleteRec, deleteReq)
	if deleteRec.Code != http.StatusOK {
		t.Fatalf("DELETE room status = %d, want %d", deleteRec.Code, http.StatusOK)
	}

	missingReq := httptest.NewRequest(http.MethodGet, "/rooms/test1", nil)
	missingRec := httptest.NewRecorder()
	mux.ServeHTTP(missingRec, missingReq)
	if missingRec.Code != http.StatusNotFound {
		t.Fatalf("GET deleted room status = %d, want %d", missingRec.Code, http.StatusNotFound)
	}
}

func TestRoomValidation(t *testing.T) {
	req := httptest.NewRequest(http.MethodGet, "/rooms/bad!code", nil)
	rec := httptest.NewRecorder()

	testRoomMux().ServeHTTP(rec, req)

	if rec.Code != http.StatusBadRequest {
		t.Fatalf("status = %d, want %d", rec.Code, http.StatusBadRequest)
	}
	if !bytes.Contains(rec.Body.Bytes(), []byte("invalid room code")) {
		t.Fatalf("body = %q, want to contain %q", rec.Body.String(), "invalid room code")
	}
}

func TestSetGetAnchor(t *testing.T) {
	mux := testRoomMux()

	putReq := httptest.NewRequest(http.MethodPut, "/rooms/r1/anchor", bytes.NewBufferString(`{"anchor_id":"anc1"}`))
	putRec := httptest.NewRecorder()
	mux.ServeHTTP(putRec, putReq)
	if putRec.Code != http.StatusOK {
		t.Fatalf("PUT anchor status = %d, want %d", putRec.Code, http.StatusOK)
	}

	getReq := httptest.NewRequest(http.MethodGet, "/rooms/r1/anchor", nil)
	getRec := httptest.NewRecorder()
	mux.ServeHTTP(getRec, getReq)
	if getRec.Code != http.StatusOK {
		t.Fatalf("GET anchor status = %d, want %d", getRec.Code, http.StatusOK)
	}
	var body map[string]string
	if err := json.NewDecoder(getRec.Body).Decode(&body); err != nil {
		t.Fatalf("failed to decode anchor response: %v", err)
	}
	if body["anchor_id"] != "anc1" {
		t.Fatalf("anchor_id = %q, want %q", body["anchor_id"], "anc1")
	}

	missingReq := httptest.NewRequest(http.MethodGet, "/rooms/nonexistent/anchor", nil)
	missingRec := httptest.NewRecorder()
	mux.ServeHTTP(missingRec, missingReq)
	if missingRec.Code != http.StatusNotFound {
		t.Fatalf("GET missing anchor status = %d, want %d", missingRec.Code, http.StatusNotFound)
	}
}

func TestSetGetLeader(t *testing.T) {
	mux := testRoomMux()

	putReq := httptest.NewRequest(http.MethodPut, "/rooms/r1/leader", bytes.NewBufferString(`{"leader_id":"L1"}`))
	putRec := httptest.NewRecorder()
	mux.ServeHTTP(putRec, putReq)
	if putRec.Code != http.StatusOK {
		t.Fatalf("PUT leader status = %d, want %d", putRec.Code, http.StatusOK)
	}

	getReq := httptest.NewRequest(http.MethodGet, "/rooms/r1/leader", nil)
	getRec := httptest.NewRecorder()
	mux.ServeHTTP(getRec, getReq)
	if getRec.Code != http.StatusOK {
		t.Fatalf("GET leader status = %d, want %d", getRec.Code, http.StatusOK)
	}
	var body map[string]string
	if err := json.NewDecoder(getRec.Body).Decode(&body); err != nil {
		t.Fatalf("failed to decode leader response: %v", err)
	}
	if body["leader_id"] != "L1" {
		t.Fatalf("leader_id = %q, want %q", body["leader_id"], "L1")
	}
}

func TestSetGetConditions(t *testing.T) {
	mux := testRoomMux()

	putReq := httptest.NewRequest(http.MethodPut, "/rooms/r1/conditions", bytes.NewBufferString(`{"condition_preset":"wifi"}`))
	putRec := httptest.NewRecorder()
	mux.ServeHTTP(putRec, putReq)
	if putRec.Code != http.StatusOK {
		t.Fatalf("PUT conditions status = %d, want %d", putRec.Code, http.StatusOK)
	}

	getReq := httptest.NewRequest(http.MethodGet, "/rooms/r1/conditions", nil)
	getRec := httptest.NewRecorder()
	mux.ServeHTTP(getRec, getReq)
	if getRec.Code != http.StatusOK {
		t.Fatalf("GET conditions status = %d, want %d", getRec.Code, http.StatusOK)
	}
	var body room.NetworkConditions
	if err := json.NewDecoder(getRec.Body).Decode(&body); err != nil {
		t.Fatalf("failed to decode conditions response: %v", err)
	}
	if body.LatencyMs != 5 {
		t.Fatalf("latency_ms = %d, want 5", body.LatencyMs)
	}
}

func TestSetGetSnapshots(t *testing.T) {
	mux := testRoomMux()

	putReq := httptest.NewRequest(http.MethodPut, "/rooms/r1/snapshots/peer1", bytes.NewBufferString(`{"rtt":42}`))
	putRec := httptest.NewRecorder()
	mux.ServeHTTP(putRec, putReq)
	if putRec.Code != http.StatusOK {
		t.Fatalf("PUT snapshot status = %d, want %d", putRec.Code, http.StatusOK)
	}

	getReq := httptest.NewRequest(http.MethodGet, "/rooms/r1/snapshots", nil)
	getRec := httptest.NewRecorder()
	mux.ServeHTTP(getRec, getReq)
	if getRec.Code != http.StatusOK {
		t.Fatalf("GET snapshots status = %d, want %d", getRec.Code, http.StatusOK)
	}
	var snapshots []room.PeerSnapshot
	if err := json.NewDecoder(getRec.Body).Decode(&snapshots); err != nil {
		t.Fatalf("failed to decode snapshots response: %v", err)
	}
	if len(snapshots) != 1 {
		t.Fatalf("len(snapshots) = %d, want 1", len(snapshots))
	}
	if snapshots[0].PeerId != "peer1" {
		t.Fatalf("peer_id = %q, want %q", snapshots[0].PeerId, "peer1")
	}
}

func TestConditionsValidation(t *testing.T) {
	mux := testRoomMux()

	putReq := httptest.NewRequest(http.MethodPut, "/rooms/r1/conditions", bytes.NewBufferString(`{"latency_ms":-10,"packet_loss_pct":200}`))
	putRec := httptest.NewRecorder()
	mux.ServeHTTP(putRec, putReq)
	if putRec.Code != http.StatusOK {
		t.Fatalf("PUT conditions status = %d, want %d", putRec.Code, http.StatusOK)
	}

	getReq := httptest.NewRequest(http.MethodGet, "/rooms/r1/conditions", nil)
	getRec := httptest.NewRecorder()
	mux.ServeHTTP(getRec, getReq)
	if getRec.Code != http.StatusOK {
		t.Fatalf("GET conditions status = %d, want %d", getRec.Code, http.StatusOK)
	}
	var body room.NetworkConditions
	if err := json.NewDecoder(getRec.Body).Decode(&body); err != nil {
		t.Fatalf("failed to decode conditions response: %v", err)
	}
	if body.LatencyMs != 0 {
		t.Fatalf("latency_ms = %d, want 0", body.LatencyMs)
	}
	if body.PacketLossPct != 100 {
		t.Fatalf("packet_loss_pct = %v, want 100", body.PacketLossPct)
	}
}
