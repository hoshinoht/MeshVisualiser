package room

import (
	"fmt"
	"strings"
	"sync"
	"testing"
	"time"
)

func TestStoreGetOrCreate(t *testing.T) {
	s := NewStore()

	room1, err := s.GetOrCreate("ABC")
	if err != nil {
		t.Fatalf("GetOrCreate returned error: %v", err)
	}
	if room1.Code != "ABC" {
		t.Fatalf("Code = %q, want %q", room1.Code, "ABC")
	}
	if room1.Conditions != Presets["perfect"] {
		t.Fatalf("Conditions = %+v, want %+v", room1.Conditions, Presets["perfect"])
	}
	if room1.CreatedAt.IsZero() {
		t.Fatal("CreatedAt is zero")
	}

	room2, err := s.GetOrCreate("ABC")
	if err != nil {
		t.Fatalf("second GetOrCreate returned error: %v", err)
	}
	if room2.Code != room1.Code {
		t.Fatalf("Code mismatch: got %q, want %q", room2.Code, room1.Code)
	}
	if !room2.CreatedAt.Equal(room1.CreatedAt) {
		t.Fatalf("CreatedAt mismatch: got %v, want %v", room2.CreatedAt, room1.CreatedAt)
	}
}

func TestStoreGetOrCreate_MaxRooms(t *testing.T) {
	s := NewStore()

	for i := 0; i < maxRooms; i++ {
		if _, err := s.GetOrCreate(fmt.Sprintf("room-%d", i)); err != nil {
			t.Fatalf("GetOrCreate failed at %d: %v", i, err)
		}
	}

	if _, err := s.GetOrCreate("overflow"); err == nil || !strings.Contains(err.Error(), "room limit") {
		t.Fatalf("expected room limit error, got %v", err)
	}
}

func TestStoreGet_NotFound(t *testing.T) {
	s := NewStore()

	if _, ok := s.Get("nonexistent"); ok {
		t.Fatal("Get returned ok=true for nonexistent room")
	}
}

func TestStoreDelete(t *testing.T) {
	s := NewStore()
	if _, err := s.GetOrCreate("ABC"); err != nil {
		t.Fatalf("GetOrCreate returned error: %v", err)
	}

	if ok := s.Delete("ABC"); !ok {
		t.Fatal("first Delete returned false, want true")
	}
	if ok := s.Delete("ABC"); ok {
		t.Fatal("second Delete returned true, want false")
	}
	if _, ok := s.Get("ABC"); ok {
		t.Fatal("Get returned deleted room")
	}
}

func TestStoreListAll(t *testing.T) {
	s := NewStore()
	for _, code := range []string{"A", "B", "C"} {
		if _, err := s.GetOrCreate(code); err != nil {
			t.Fatalf("GetOrCreate(%q) returned error: %v", code, err)
		}
	}

	rooms := s.ListAll()
	if len(rooms) != 3 {
		t.Fatalf("ListAll length = %d, want 3", len(rooms))
	}

	seen := make(map[string]bool, len(rooms))
	for _, room := range rooms {
		seen[room.Code] = true
	}
	for _, code := range []string{"A", "B", "C"} {
		if !seen[code] {
			t.Fatalf("missing room code %q in ListAll", code)
		}
	}
}

func TestStoreSetAnchor(t *testing.T) {
	s := NewStore()
	if _, err := s.GetOrCreate("ABC"); err != nil {
		t.Fatalf("GetOrCreate returned error: %v", err)
	}

	room, ok := s.SetAnchor("ABC", "anchor-1")
	if !ok {
		t.Fatal("SetAnchor returned ok=false")
	}
	if room.AnchorID != "anchor-1" {
		t.Fatalf("AnchorID = %q, want %q", room.AnchorID, "anchor-1")
	}

	stored, ok := s.Get("ABC")
	if !ok {
		t.Fatal("Get returned ok=false")
	}
	if stored.AnchorID != "anchor-1" {
		t.Fatalf("persisted AnchorID = %q, want %q", stored.AnchorID, "anchor-1")
	}

	if _, ok := s.SetAnchor("missing", "anchor-2"); ok {
		t.Fatal("SetAnchor returned ok=true for nonexistent room")
	}
}

func TestStoreSetLeader(t *testing.T) {
	s := NewStore()
	if _, err := s.GetOrCreate("ABC"); err != nil {
		t.Fatalf("GetOrCreate returned error: %v", err)
	}

	room, ok := s.SetLeader("ABC", "leader-1")
	if !ok {
		t.Fatal("SetLeader returned ok=false")
	}
	if room.LeaderID != "leader-1" {
		t.Fatalf("LeaderID = %q, want %q", room.LeaderID, "leader-1")
	}

	stored, ok := s.Get("ABC")
	if !ok {
		t.Fatal("Get returned ok=false")
	}
	if stored.LeaderID != "leader-1" {
		t.Fatalf("persisted LeaderID = %q, want %q", stored.LeaderID, "leader-1")
	}

	if _, ok := s.SetLeader("missing", "leader-2"); ok {
		t.Fatal("SetLeader returned ok=true for nonexistent room")
	}
}

func TestStoreSetConditions(t *testing.T) {
	s := NewStore()
	if _, err := s.GetOrCreate("ABC"); err != nil {
		t.Fatalf("GetOrCreate returned error: %v", err)
	}

	cond := NetworkConditions{LatencyMs: 100, JitterMs: 5, PacketLossPct: 1.5, BandwidthKbps: 1024, ConditionPreset: "custom"}
	room, ok := s.SetConditions("ABC", cond)
	if !ok {
		t.Fatal("SetConditions returned ok=false")
	}
	if room.Conditions.LatencyMs != 100 {
		t.Fatalf("LatencyMs = %d, want 100", room.Conditions.LatencyMs)
	}

	if _, ok := s.SetConditions("missing", cond); ok {
		t.Fatal("SetConditions returned ok=true for nonexistent room")
	}
}

func TestStoreSetSnapshot(t *testing.T) {
	s := NewStore()
	if _, err := s.GetOrCreate("ABC"); err != nil {
		t.Fatalf("GetOrCreate returned error: %v", err)
	}

	room, ok := s.SetSnapshot("ABC", "peer1", map[string]any{"rtt": 42})
	if !ok {
		t.Fatal("SetSnapshot returned ok=false")
	}
	if room.Snapshots == nil || room.Snapshots["peer1"] == nil {
		t.Fatal("returned room missing snapshot for peer1")
	}

	snaps, ok := s.GetSnapshots("ABC")
	if !ok {
		t.Fatal("GetSnapshots returned ok=false")
	}
	if len(snaps) != 1 {
		t.Fatalf("GetSnapshots length = %d, want 1", len(snaps))
	}
	if snaps[0].PeerId != "peer1" {
		t.Fatalf("PeerId = %q, want %q", snaps[0].PeerId, "peer1")
	}
	data, ok := snaps[0].Data.(map[string]any)
	if !ok {
		t.Fatalf("snapshot Data type = %T, want map[string]any", snaps[0].Data)
	}
	if data["rtt"] != 42 {
		t.Fatalf("snapshot Data[rtt] = %v, want 42", data["rtt"])
	}
}

func TestStoreGetSnapshots_NotFound(t *testing.T) {
	s := NewStore()

	snaps, ok := s.GetSnapshots("missing")
	if ok {
		t.Fatal("GetSnapshots returned ok=true for nonexistent room")
	}
	if snaps != nil {
		t.Fatalf("GetSnapshots returned %v, want nil", snaps)
	}
}

func TestStoreCleanup(t *testing.T) {
	s := NewStore()
	if _, err := s.GetOrCreate("old-room"); err != nil {
		t.Fatalf("GetOrCreate old-room returned error: %v", err)
	}
	if _, err := s.GetOrCreate("fresh-room"); err != nil {
		t.Fatalf("GetOrCreate fresh-room returned error: %v", err)
	}

	s.mu.Lock()
	s.rooms["old-room"].UpdatedAt = time.Now().Add(-2 * time.Hour)
	s.mu.Unlock()

	deleted := s.Cleanup(1 * time.Hour)
	if deleted != 1 {
		t.Fatalf("Cleanup deleted %d rooms, want 1", deleted)
	}
	if _, ok := s.Get("old-room"); ok {
		t.Fatal("old-room still present after Cleanup")
	}
	if _, ok := s.Get("fresh-room"); !ok {
		t.Fatal("fresh-room missing after Cleanup")
	}
}

func TestStoreDeepCopy_SnapshotsIsolation(t *testing.T) {
	s := NewStore()
	if _, err := s.GetOrCreate("code"); err != nil {
		t.Fatalf("GetOrCreate returned error: %v", err)
	}
	if _, ok := s.SetSnapshot("code", "peer1", "original"); !ok {
		t.Fatal("SetSnapshot returned ok=false")
	}

	room1, ok := s.Get("code")
	if !ok {
		t.Fatal("Get returned ok=false")
	}
	room1.Snapshots["peer1"].Data = "mutated"

	room2, ok := s.Get("code")
	if !ok {
		t.Fatal("second Get returned ok=false")
	}
	if got := room2.Snapshots["peer1"].Data; got == "mutated" {
		t.Fatalf("snapshot Data was mutated through copy: %v", got)
	}
	if got := room2.Snapshots["peer1"].Data; got != "original" {
		t.Fatalf("snapshot Data = %v, want %q", got, "original")
	}
}

func TestStoreConcurrent_RaceDetector(t *testing.T) {
	s := NewStore()
	if _, err := s.GetOrCreate("room1"); err != nil {
		t.Fatalf("GetOrCreate returned error: %v", err)
	}

	var wg sync.WaitGroup
	for g := 0; g < 10; g++ {
		g := g
		wg.Add(1)
		go func() {
			defer wg.Done()
			for i := 0; i < 100; i++ {
				if g%2 == 1 {
					peerID := fmt.Sprintf("peer-%d-%d", g, i)
					if _, ok := s.SetSnapshot("room1", peerID, map[string]any{"iter": i, "goroutine": g}); !ok {
						t.Errorf("SetSnapshot returned ok=false")
						return
					}
					continue
				}

				room, ok := s.Get("room1")
				if !ok {
					t.Errorf("Get returned ok=false")
					return
				}
				for peerID, snap := range room.Snapshots {
					if peerID == "" {
						t.Errorf("empty peerID encountered")
						return
					}
					if snap == nil {
						t.Errorf("nil snapshot encountered")
						return
					}
					_ = snap.PeerId
					_ = snap.Data
				}
			}
		}()
	}
	wg.Wait()
}
