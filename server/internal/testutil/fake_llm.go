package testutil

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"sync/atomic"
	"time"
)

// FakeLLM returns a test server that mimics /v1/chat/completions with controllable delay.
// The returned counter tracks how many times the LLM was called.
func FakeLLM(delay time.Duration, responseContent string) (*httptest.Server, *atomic.Int32) {
	var count atomic.Int32
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		count.Add(1)
		if delay > 0 {
			select {
			case <-time.After(delay):
			case <-r.Context().Done():
				http.Error(w, "cancelled", http.StatusServiceUnavailable)
				return
			}
		}
		resp := map[string]any{"choices": []map[string]any{{"message": map[string]string{"role": "assistant", "content": responseContent}}}}
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(resp)
	}))
	return srv, &count
}

// FakeLLMFunc returns a test server with a fully custom handler.
func FakeLLMFunc(handler http.HandlerFunc) *httptest.Server {
	return httptest.NewServer(handler)
}
