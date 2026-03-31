package httpapi

import (
	"bytes"
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/inf2007/inf2007-team07-2026/server/internal/llm"
	"github.com/inf2007/inf2007-team07-2026/server/internal/quiz"
	"github.com/inf2007/inf2007-team07-2026/server/internal/room"
	"github.com/inf2007/inf2007-team07-2026/server/internal/testutil"
)

func newTestMux(fakeURL string) (*http.ServeMux, *llm.LLMQueue, *SummaryCache, *QuizCache, chan struct{}) {
	cfg := &llm.Config{BaseURL: fakeURL, Model: "test-model"}
	queue := llm.NewQueue(cfg, 1, 3, 30*time.Second)
	stop := make(chan struct{})
	sc := NewSummaryCache(1*time.Hour, stop)
	qc := NewQuizCache(30*time.Minute, stop)
	mux := http.NewServeMux()
	store := room.NewStore()
	RegisterRoomRoutes(mux, store)
	RegisterAIRoutes(mux, cfg, queue, sc, qc)
	return mux, queue, sc, qc, stop
}

func TestNarrate_HappyPath(t *testing.T) {
	srv, _ := testutil.FakeLLM(0, "explanation text")
	defer srv.Close()

	mux, _, _, _, stop := newTestMux(srv.URL)
	defer close(stop)
	rec := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodPost, "/ai/narrate", bytes.NewBufferString(`{"events":["peer joined"],"mesh_state":"3 peers"}`))
	mux.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("status = %d, want 200", rec.Code)
	}
	var resp NarrateResponse
	if err := json.NewDecoder(rec.Body).Decode(&resp); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if resp.Explanation == "" {
		t.Fatal("expected explanation field")
	}
}

func TestNarrate_NoEvents(t *testing.T) {
	srv, _ := testutil.FakeLLM(0, "unused")
	defer srv.Close()

	mux, _, _, _, stop := newTestMux(srv.URL)
	defer close(stop)
	rec := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodPost, "/ai/narrate", bytes.NewBufferString(`{"events":[],"mesh_state":"x"}`))
	mux.ServeHTTP(rec, req)

	if rec.Code != http.StatusBadRequest {
		t.Fatalf("status = %d, want 400", rec.Code)
	}
}

func TestNarrate_InvalidJSON(t *testing.T) {
	srv, _ := testutil.FakeLLM(0, "unused")
	defer srv.Close()

	mux, _, _, _, stop := newTestMux(srv.URL)
	defer close(stop)
	rec := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodPost, "/ai/narrate", bytes.NewBufferString(`not json`))
	mux.ServeHTTP(rec, req)

	if rec.Code != http.StatusBadRequest {
		t.Fatalf("status = %d, want 400", rec.Code)
	}
}

func TestWhatIf_HappyPath(t *testing.T) {
	srv, _ := testutil.FakeLLM(0, "answer text")
	defer srv.Close()

	mux, _, _, _, stop := newTestMux(srv.URL)
	defer close(stop)
	rec := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodPost, "/ai/what-if", bytes.NewBufferString(`{"question":"what if latency doubles?","mesh_state":"3 peers"}`))
	mux.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("status = %d, want 200", rec.Code)
	}
	var resp WhatIfResponse
	if err := json.NewDecoder(rec.Body).Decode(&resp); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if resp.Answer == "" {
		t.Fatal("expected answer field")
	}
}

func TestWhatIf_NoQuestion(t *testing.T) {
	srv, _ := testutil.FakeLLM(0, "unused")
	defer srv.Close()

	mux, _, _, _, stop := newTestMux(srv.URL)
	defer close(stop)
	rec := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodPost, "/ai/what-if", bytes.NewBufferString(`{"question":"","mesh_state":"x"}`))
	mux.ServeHTTP(rec, req)

	if rec.Code != http.StatusBadRequest {
		t.Fatalf("status = %d, want 400", rec.Code)
	}
}

func TestSummary_HappyPath(t *testing.T) {
	srv, _ := testutil.FakeLLM(0, "summary text")
	defer srv.Close()

	mux, _, _, _, stop := newTestMux(srv.URL)
	defer close(stop)
	rec := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodPost, "/ai/summary", bytes.NewBufferString(`{"mesh_state":"session data"}`))
	mux.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("status = %d, want 200", rec.Code)
	}
	var resp SummaryResponse
	if err := json.NewDecoder(rec.Body).Decode(&resp); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if resp.Summary == "" {
		t.Fatal("expected summary field")
	}
}

func TestSummary_CacheHit(t *testing.T) {
	srv, count := testutil.FakeLLM(0, "summary text")
	defer srv.Close()

	mux, _, _, _, stop := newTestMux(srv.URL)
	defer close(stop)
	body := []byte(`{"mesh_state":"session data"}`)

	for range 2 {
		rec := httptest.NewRecorder()
		req := httptest.NewRequest(http.MethodPost, "/ai/summary", bytes.NewReader(body))
		mux.ServeHTTP(rec, req)
		if rec.Code != http.StatusOK {
			t.Fatalf("status = %d, want 200", rec.Code)
		}
	}

	if got := count.Load(); got != 1 {
		t.Fatalf("LLM calls = %d, want 1", got)
	}
}

func TestQuiz_HappyPath(t *testing.T) {
	var qs []quiz.Question
	for i := range 10 {
		qs = append(qs, quiz.Question{
			Text:        fmt.Sprintf("Question %d?", i),
			Options:     []string{"A", "B", "C", "D"},
			Correct:     0,
			Category:    "CONCEPT",
			Explanation: "Because.",
		})
	}
	qsJSON, err := json.Marshal(map[string]any{"questions": qs})
	if err != nil {
		t.Fatalf("marshal quiz JSON: %v", err)
	}

	srv, _ := testutil.FakeLLM(0, string(qsJSON))
	defer srv.Close()

	mux, _, _, _, stop := newTestMux(srv.URL)
	defer close(stop)
	rec := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodPost, "/ai/quiz", bytes.NewBufferString(`{"mesh_state":"session"}`))
	mux.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("status = %d, want 200", rec.Code)
	}
	var resp QuizResponse
	if err := json.NewDecoder(rec.Body).Decode(&resp); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if resp.Source != "ai" {
		t.Fatalf("source = %q, want ai", resp.Source)
	}
	if len(resp.Questions) != 10 {
		t.Fatalf("questions = %d, want 10", len(resp.Questions))
	}
}

func TestQuiz_LLMFailure_StaticFallback(t *testing.T) {
	srv := testutil.FakeLLMFunc(func(w http.ResponseWriter, r *http.Request) {
		http.Error(w, "boom", http.StatusInternalServerError)
	})
	defer srv.Close()

	mux, _, _, _, stop := newTestMux(srv.URL)
	defer close(stop)
	rec := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodPost, "/ai/quiz", bytes.NewBufferString(`{"mesh_state":"session"}`))
	mux.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("status = %d, want 200", rec.Code)
	}
	var resp QuizResponse
	if err := json.NewDecoder(rec.Body).Decode(&resp); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if resp.Source != "static" {
		t.Fatalf("source = %q, want static", resp.Source)
	}
	if len(resp.Questions) == 0 {
		t.Fatal("expected fallback questions")
	}
}

func TestQuiz_QueueFull_503(t *testing.T) {
	slowSrv, _ := testutil.FakeLLM(10*time.Second, "slow")
	defer slowSrv.Close()

	cfg := &llm.Config{BaseURL: slowSrv.URL, Model: "test-model"}
	queue := llm.NewQueue(cfg, 1, 0, 30*time.Second)
	stop := make(chan struct{})
	defer close(stop)
	sc := NewSummaryCache(1*time.Hour, stop)
	qc := NewQuizCache(30*time.Minute, stop)
	mux := http.NewServeMux()
	RegisterAIRoutes(mux, cfg, queue, sc, qc)

	go func() {
		rec := httptest.NewRecorder()
		req := httptest.NewRequest(http.MethodPost, "/ai/quiz", bytes.NewBufferString(`{"mesh_state":"one"}`))
		mux.ServeHTTP(rec, req)
	}()

	time.Sleep(50 * time.Millisecond)

	rec := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodPost, "/ai/quiz", bytes.NewBufferString(`{"mesh_state":"two"}`))
	mux.ServeHTTP(rec, req)

	if rec.Code != http.StatusServiceUnavailable {
		t.Fatalf("status = %d, want 503", rec.Code)
	}
	if rec.Header().Get("Retry-After") == "" {
		t.Fatal("expected Retry-After header")
	}
}

func TestAIConfig_GetPut(t *testing.T) {
	srv, _ := testutil.FakeLLM(0, "ok")
	defer srv.Close()

	mux, _, _, _, stop := newTestMux(srv.URL)
	defer close(stop)

	get1 := httptest.NewRecorder()
	mux.ServeHTTP(get1, httptest.NewRequest(http.MethodGet, "/ai/config", nil))
	if get1.Code != http.StatusOK {
		t.Fatalf("first GET status = %d, want 200", get1.Code)
	}
	var cfg1 map[string]any
	if err := json.NewDecoder(get1.Body).Decode(&cfg1); err != nil {
		t.Fatalf("decode first GET: %v", err)
	}
	if _, ok := cfg1["llm_base_url"]; !ok {
		t.Fatal("expected llm_base_url in GET response")
	}

	put := httptest.NewRecorder()
	putReq := httptest.NewRequest(http.MethodPut, "/ai/config", bytes.NewBufferString(`{"llm_model":"new-model"}`))
	mux.ServeHTTP(put, putReq)
	if put.Code != http.StatusOK {
		t.Fatalf("PUT status = %d, want 200", put.Code)
	}

	get2 := httptest.NewRecorder()
	mux.ServeHTTP(get2, httptest.NewRequest(http.MethodGet, "/ai/config", nil))
	if get2.Code != http.StatusOK {
		t.Fatalf("second GET status = %d, want 200", get2.Code)
	}
	var cfg2 map[string]any
	if err := json.NewDecoder(get2.Body).Decode(&cfg2); err != nil {
		t.Fatalf("decode second GET: %v", err)
	}
	if cfg2["llm_model"] != "new-model" {
		t.Fatalf("llm_model = %v, want new-model", cfg2["llm_model"])
	}
}

func TestAITest_HappyPath(t *testing.T) {
	srv, _ := testutil.FakeLLM(0, "OK")
	defer srv.Close()

	mux, _, _, _, stop := newTestMux(srv.URL)
	defer close(stop)
	rec := httptest.NewRecorder()
	mux.ServeHTTP(rec, httptest.NewRequest(http.MethodPost, "/ai/test", nil))

	if rec.Code != http.StatusOK {
		t.Fatalf("status = %d, want 200", rec.Code)
	}
	var resp map[string]string
	if err := json.NewDecoder(rec.Body).Decode(&resp); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if resp["response"] == "" {
		t.Fatal("expected response field")
	}
}
