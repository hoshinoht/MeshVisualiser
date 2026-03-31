package llm

import (
	"context"
	"errors"
	"net/http"
	"sync"
	"testing"
	"time"

	"github.com/inf2007/inf2007-team07-2026/server/internal/testutil"
)

func TestCallLLM_HappyPath(t *testing.T) {
	srv, _ := testutil.FakeLLM(0, "hello")
	defer srv.Close()

	content, err := callLLM(context.Background(), &Config{BaseURL: srv.URL, Model: "test-model"}, []ChatMessage{{Role: "user", Content: "hi"}}, 10, "test")
	if err != nil {
		t.Fatalf("callLLM error = %v", err)
	}
	if content != "hello" {
		t.Fatalf("content = %q, want %q", content, "hello")
	}
}

func TestCallLLM_HTTPError(t *testing.T) {
	srv := testutil.FakeLLMFunc(func(w http.ResponseWriter, r *http.Request) {
		http.Error(w, "boom", http.StatusInternalServerError)
	})
	defer srv.Close()

	_, err := callLLM(context.Background(), &Config{BaseURL: srv.URL, Model: "test-model"}, []ChatMessage{{Role: "user", Content: "hi"}}, 10, "test")
	if err == nil || !contains(err.Error(), "500") {
		t.Fatalf("error = %v, want containing 500", err)
	}
}

func TestCallLLM_InvalidJSON(t *testing.T) {
	srv := testutil.FakeLLMFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte("not json"))
	})
	defer srv.Close()

	_, err := callLLM(context.Background(), &Config{BaseURL: srv.URL, Model: "test-model"}, []ChatMessage{{Role: "user", Content: "hi"}}, 10, "test")
	if err == nil || !contains(err.Error(), "parse") {
		t.Fatalf("error = %v, want containing parse", err)
	}
}

func TestCallLLM_EmptyChoices(t *testing.T) {
	srv := testutil.FakeLLMFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{"choices":[]}`))
	})
	defer srv.Close()

	_, err := callLLM(context.Background(), &Config{BaseURL: srv.URL, Model: "test-model"}, []ChatMessage{{Role: "user", Content: "hi"}}, 10, "test")
	if err == nil || !contains(err.Error(), "no choices") {
		t.Fatalf("error = %v, want containing no choices", err)
	}
}

func TestCallLLM_ContextCancel(t *testing.T) {
	srv, _ := testutil.FakeLLM(5*time.Second, "slow")
	defer srv.Close()

	ctx, cancel := context.WithCancel(context.Background())
	cancel()

	_, err := callLLM(ctx, &Config{BaseURL: srv.URL, Model: "test-model"}, []ChatMessage{{Role: "user", Content: "hi"}}, 10, "test")
	if !errors.Is(err, context.Canceled) {
		t.Fatalf("error = %v, want context.Canceled", err)
	}
}

func TestCallLLM_ContextTimeout(t *testing.T) {
	srv, _ := testutil.FakeLLM(5*time.Second, "slow")
	defer srv.Close()

	ctx, cancel := context.WithTimeout(context.Background(), 50*time.Millisecond)
	defer cancel()

	_, err := callLLM(ctx, &Config{BaseURL: srv.URL, Model: "test-model"}, []ChatMessage{{Role: "user", Content: "hi"}}, 10, "test")
	if !errors.Is(err, context.DeadlineExceeded) {
		t.Fatalf("error = %v, want context.DeadlineExceeded", err)
	}
}

func TestLLMConfig_ConcurrentReadWrite(t *testing.T) {
	cfg := NewConfig()
	var wg sync.WaitGroup

	for range 10 {
		wg.Add(1)
		go func() {
			defer wg.Done()
			_, _, _ = cfg.Get()
		}()
	}

	for range 10 {
		wg.Add(1)
		go func() {
			defer wg.Done()
			cfg.Update("http://x", "m", "k")
		}()
	}

	wg.Wait()
}

func contains(s, sub string) bool {
	return len(sub) == 0 || (len(s) >= len(sub) && (func() bool { return stringIndex(s, sub) >= 0 })())
}

func stringIndex(s, sub string) int {
	for i := 0; i+len(sub) <= len(s); i++ {
		if s[i:i+len(sub)] == sub {
			return i
		}
	}
	return -1
}
